import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import model.Country;
import model.Hotel;
import model.Location;
import model.Message;
import model.User;
import model.WeatherData;

import dao.CountryDAO;
import dao.HistoryDAO;
import dao.HotelDAO;
import dao.LocationDAO;
import dao.UserDAO;
import dao.WeatherSubscriptionDAO;

import server.ClientManager;
import server.ClientSession;

import security.SecureChannel;

import service.CountryAPIService;
import service.CountryDetailsService;
import service.WeatherAPIService;
import service.CityAPIService;
import service.ComparisonService;
import util.InputValidation.ValidationException;
import util.RequestValidation;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.util.List;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final Gson gson;

    private final UserDAO userDAO;
    private final LocationDAO locationDAO;
    private final HistoryDAO historyDAO;
    private final HotelDAO hotelDAO;
    private final CountryDAO countryDAO;
    private final WeatherSubscriptionDAO subscriptionDAO;

    private final WeatherAPIService weatherAPIService;
    private final CountryAPIService countryAPIService;
    private final CountryDetailsService countryDetailsService = new CountryDetailsService();

    private final KeyPair serverKeys;

    private Integer currentUserId = null;
    private ClientSession session;
    private final CityAPIService cityAPIService = new CityAPIService();
    private final java.util.Map<Integer, Location> searchedCities = new java.util.HashMap<>();
    private final java.util.Map<Integer, Location> tripCities = new java.util.HashMap<>();
    private final java.util.Map<Integer, Location> comparisonCities = new java.util.LinkedHashMap<>();

    public ClientHandler(
            Socket clientSocket,
            KeyPair serverKeys
    ) {
        this.serverKeys = serverKeys;
        this.clientSocket = clientSocket;

        this.gson = new Gson();

        this.userDAO = new UserDAO();
        this.locationDAO = new LocationDAO();
        this.historyDAO = new HistoryDAO();
        this.hotelDAO = new HotelDAO();
        this.countryDAO = new CountryDAO();
        this.subscriptionDAO = new WeatherSubscriptionDAO();

        this.weatherAPIService = new WeatherAPIService();
        this.countryAPIService = new CountryAPIService();
    }

    @Override
    public void run() {

        System.out.println(
                "Máy khách đã kết nối: "
                        + clientSocket.getRemoteSocketAddress()
        );

        try (
                SecureChannel channel =
                        SecureChannel.accept(
                                clientSocket,
                                serverKeys
                        )
        ) {
            session = new ClientSession(channel);

            String requestJson;

            while (
                    (requestJson = channel.receive()) != null
            ) {
                session.sendJson(
                        handleRequest(requestJson)
                );
            }

        } catch (IOException exception) {
            System.err.println(
                    "Lỗi giao tiếp với máy khách: "
                            + (exception instanceof java.net.SocketTimeoutException
                                    ? "Hết thời gian chờ trao đổi bảo mật."
                                    : exception instanceof java.net.SocketException || exception instanceof java.io.EOFException
                                            ? "Kết nối bị gián đoạn."
                                            : exception.getMessage() == null ? "Kết nối đã đóng." : exception.getMessage())
            );

        } finally {
            clearAuthentication();

            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Không đóng được kết nối với máy khách.");
            }

            System.out.println(
                    "Máy khách đã ngắt kết nối: "
                            + clientSocket.getRemoteSocketAddress()
            );
        }
    }

    private String handleRequest(String requestJson) {

        try {
            JsonElement parsed = JsonParser.parseString(requestJson);
            if (!parsed.isJsonObject()) {
                return createErrorResponse("Yêu cầu phải là một đối tượng JSON.");
            }
            JsonObject envelope = parsed.getAsJsonObject();
            JsonElement actionValue = envelope.get("action");
            if (actionValue == null || actionValue.isJsonNull()) {
                return createErrorResponse("Yêu cầu thiếu tên thao tác.");
            }
            if (!actionValue.isJsonPrimitive() || !actionValue.getAsJsonPrimitive().isString()) {
                return createErrorResponse("Tên thao tác phải là chuỗi ký tự.");
            }
            String action = actionValue.getAsString().trim().toUpperCase(java.util.Locale.ROOT);
            if (action.isEmpty()) return createErrorResponse("Yêu cầu thiếu tên thao tác.");
            // Validation can reject a login before dispatch. Drop the old identity first.
            if ("LOGIN".equals(action)) clearAuthentication();
            if (requiresAuthentication(action) && currentUserId == null) {
                return new Message("UNAUTHORIZED", "Bạn cần đăng nhập để sử dụng chức năng này.").toJson();
            }
            Message request = RequestValidation.validate(new Message(action, envelope.get("data")));

            switch (action) {
                case "LOGIN":
                    return handleLogin(request);

                case "REGISTER":
                    return handleRegister(request);

                case "UPDATE_PROFILE":
                    return handleUpdateProfile(request);

                case "GET_PROFILE":
                    return handleGetProfile(request);

                case "CHANGE_PASSWORD":
                    return handleChangePassword(request);

                case "LOGOUT":
                    return handleLogout();

                case "SEARCH_LOCATION":
                    return handleLocationSearch(request);

                case "SEARCH_TRIP_CITIES": {
                    tripCities.clear();
                    try {
                        List<Location> cities = cityAPIService.search(getStringData(request.getData(), "keyword"));
                        for (Location city : cities) tripCities.put(city.getId(), city);
                        return new Message("SEARCH_TRIP_CITIES_RESPONSE", cities).toJson();
                    } catch (IOException e) { return createErrorResponse(e.getMessage()); }
                }

                case "PLAN_TRIP": {
                    JsonObject data = gson.toJsonTree(request.getData()).getAsJsonObject();
                    Location city = tripCities.get(data.get("id").getAsInt());
                    if (city == null) return createErrorResponse("Hãy tìm và chọn lại địa điểm cho lịch trình.");
                    return new Message("PLAN_TRIP_RESPONSE", new service.TripPlannerService().plan(city,
                            data.get("days").getAsInt(), cityAPIService.getDetails(city))).toJson();
                }

                case "GET_CITY_DETAILS":
                    return handleCityDetails(request);

                case "SEARCH_COMPARISON_CITIES":
                    return handleComparisonCitySearch(request);

                case "COMPARE_CITIES":
                    return handleCompareCities(request);

                case "COMPARE_COUNTRIES":
                    return handleCompareCountries(request);

                case "GET_WEATHER":
                    return handleWeather(request);

                case "SEARCH_COUNTRY":
                    return handleSearchCountry(request);

                case "GET_HOTELS":
                    return handleGetHotels(request);

                case "SAVE_LOCATION":
                    return handleSaveLocation(request);

                case "GET_SAVED_LOCATIONS":
                    return handleGetSavedLocations(request);

                case "DELETE_LOCATION":
                    return handleDeleteLocation(request);

                case "SAVE_HISTORY":
                    return handleSaveHistory(request);

                case "GET_HISTORY":
                    return handleGetHistory(request);

                case "SUBSCRIBE_WEATHER":
                    return handleSubscribeWeather(request);

                case "GET_SUBSCRIPTIONS":
                    return handleGetSubscriptions(request);

                case "UNSUBSCRIBE_WEATHER":
                    JsonObject subscription = gson.toJsonTree(request.getData()).getAsJsonObject();
                    return new Message("UNSUBSCRIBE_WEATHER_RESPONSE",
                            subscriptionDAO.delete(subscription.get("id").getAsInt(), currentUserId)).toJson();

                default:
                    return createErrorResponse(
                            "Thao tác không được hỗ trợ: "
                                    + request.getAction()
                    );
            }

        } catch (ValidationException exception) {
            return createErrorResponse(exception.getMessage());

        } catch (
                JsonParseException
                        | IllegalArgumentException exception
        ) {
            return createErrorResponse(
                    "Dữ liệu JSON của yêu cầu không hợp lệ."
            );

        } catch (RuntimeException exception) {
            System.err.println("Máy chủ gặp lỗi khi xử lý yêu cầu.");

            return createErrorResponse(
                    "Máy chủ gặp lỗi khi xử lý yêu cầu."
            );
        }
    }

    private String handleLogin(Message request) {
        JsonObject data = profileData(request);
        String username = profileString(data, "username");
        String password = profileString(data, "password");

        User user =
                userDAO.checkLogin(
                        username,
                        password
                );

        if (user != null) {

            currentUserId = user.getId();
            if (session != null) {
                session.authenticate(currentUserId);
                ClientManager.addClient(currentUserId, session);
            }

            return new Message(
                    "LOGIN_RESPONSE",
                    user
            ).toJson();
        }

        return new Message(
                "LOGIN_RESPONSE",
                null
        ).toJson();
    }

    private String handleRegister(Message request) {

        JsonObject data =
                gson.toJsonTree(request.getData())
                        .getAsJsonObject();

        String username =
                data.has("username")
                        ? data.get("username").getAsString()
                        : null;

        String password =
                data.has("password")
                        ? data.get("password").getAsString()
                        : null;

        String fullname =
                data.has("fullname")
                        ? data.get("fullname").getAsString()
                        : null;

        String email =
                data.has("email")
                        ? data.get("email").getAsString()
                        : null;

        boolean success =
                userDAO.register(
                        username,
                        password,
                        fullname,
                        email
                );

        return new Message(
                "REGISTER_RESPONSE",
                success
        ).toJson();
    }

    private String handleUpdateProfile(Message request) {
        if (currentUserId == null) return createErrorResponse("Bạn cần đăng nhập để cập nhật thông tin tài khoản.");
        JsonObject data = profileData(request);
        String fullname = profileString(data, "fullname"), email = profileString(data, "email");
        return new Message("UPDATE_PROFILE_RESPONSE", userDAO.updateProfile(currentUserId, fullname, email)).toJson();
    }

    private String handleGetProfile(Message request) {
        if (currentUserId == null) return createErrorResponse("Bạn cần đăng nhập để xem thông tin tài khoản.");
        try {
            User profile = userDAO.findById(currentUserId);
            if (profile == null) return createErrorResponse("Không tìm thấy tài khoản đang đăng nhập.");
            return new Message("GET_PROFILE_RESPONSE", new User(profile.getId(), profile.getUsername(),
                    profile.getFullname(), profile.getEmail())).toJson();
        } catch (java.sql.SQLException e) {
            return createErrorResponse("Không tải được thông tin tài khoản. Hãy kiểm tra kết nối cơ sở dữ liệu.");
        }
    }

    private String handleChangePassword(Message request) {
        if (currentUserId == null) return createErrorResponse("Bạn cần đăng nhập để đổi mật khẩu.");
        JsonObject data = profileData(request);
        String currentPassword = profileString(data, "currentPassword"), newPassword = profileString(data, "newPassword");
        switch (userDAO.changePassword(currentUserId, currentPassword, newPassword)) {
            case SUCCESS: return new Message("CHANGE_PASSWORD_RESPONSE", true).toJson();
            case WRONG_CURRENT_PASSWORD: return createErrorResponse("Mật khẩu hiện tại không đúng.");
            case USER_NOT_FOUND: return createErrorResponse("Không tìm thấy tài khoản đang đăng nhập.");
            case CONCURRENT_CHANGE: return createErrorResponse("Mật khẩu đã được đổi bởi yêu cầu khác. Hãy đăng nhập lại.");
            default: return createErrorResponse("Không đổi được mật khẩu. Hãy kiểm tra kết nối cơ sở dữ liệu.");
        }
    }

    private String handleLogout() {
        clearAuthentication();
        return new Message("LOGOUT_RESPONSE", true).toJson();
    }

    private void clearAuthentication() {
        if (session != null) session.clearAuthentication();
        if (currentUserId != null && session != null) ClientManager.removeClient(currentUserId, session);
        currentUserId = null;
        searchedCities.clear();
        tripCities.clear();
        comparisonCities.clear();
    }

    private static boolean requiresAuthentication(String action) {
        // New and unknown actions require authentication by default.
        return !"LOGIN".equals(action) && !"REGISTER".equals(action);
    }

    private JsonObject profileData(Message request) {
        JsonElement data = gson.toJsonTree(request.getData());
        return data.isJsonNull() ? new JsonObject() : data.getAsJsonObject();
    }

    private static String profileString(JsonObject data, String field) {
        JsonElement value = data.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private String handleLocationSearch(Message request) {

        String keyword =
                getStringData(
                        request.getData(),
                        "keyword"
                );

        searchedCities.clear();
        try {
            List<Location> locations = cityAPIService.search(keyword);
            for (Location city : locations) searchedCities.put(city.getId(), city);
            return new Message("SEARCH_LOCATION_RESPONSE", locations).toJson();
        } catch (IOException e) {
            return createErrorResponse("Không tra cứu được thành phố: " + e.getMessage());
        }
    }

    private String handleCityDetails(Message request) {
        int id = gson.toJsonTree(request.getData()).getAsJsonObject().get("id").getAsInt();
        Location city = searchedCities.get(id);
        if (city == null) return createErrorResponse("Hãy tra cứu và chọn lại thành phố.");
        return new Message("GET_CITY_DETAILS_RESPONSE", cityAPIService.getDetails(city)).toJson();
    }

    private String handleComparisonCitySearch(Message request) {
        try {
            List<Location> cities = cityAPIService.search(getStringData(request.getData(), "keyword"));
            for (Location city : cities) comparisonCities.put(city.getId(), city);
            // Separate from the main search: searching column 2 preserves column 1 and the main selected city.
            while (comparisonCities.size() > 200) {
                comparisonCities.remove(comparisonCities.keySet().iterator().next());
            }
            return new Message("SEARCH_COMPARISON_CITIES_RESPONSE", cities).toJson();
        } catch (IOException e) { return createErrorResponse(e.getMessage()); }
    }

    private String handleCompareCities(Message request) {
        JsonObject data = gson.toJsonTree(request.getData()).getAsJsonObject();
        Location left = comparisonCities.get(data.get("leftId").getAsInt());
        Location right = comparisonCities.get(data.get("rightId").getAsInt());
        try {
            return new Message("COMPARE_CITIES_RESPONSE",
                    new ComparisonService(countryAPIService, weatherAPIService).compareCities(left, right)).toJson();
        } catch (IOException e) { return createErrorResponse(e.getMessage()); }
    }

    private String handleCompareCountries(Message request) {
        try {
            return new Message("COMPARE_COUNTRIES_RESPONSE",
                    new ComparisonService(countryAPIService, weatherAPIService).compareCountries(
                            getStringData(request.getData(), "leftName"),
                            getStringData(request.getData(), "rightName"))).toJson();
        } catch (IOException e) { return createErrorResponse(e.getMessage()); }
    }

    private String handleWeather(Message request) {

        String cityName =
                getStringData(
                        request.getData(),
                        "cityName"
                );

        try {
            WeatherData weatherData = weatherAPIService.getWeatherData(cityName);
            return new Message("GET_WEATHER_RESPONSE", weatherData).toJson();
        } catch (IOException e) {
            return createErrorResponse(e.getMessage());
        }
    }

    private String handleSearchCountry(Message request) {

        String countryName =
                getStringData(
                        request.getData(),
                        "countryName"
                );

        try {
            Country country = countryDetailsService.getCountry(countryName);
            return new Message("SEARCH_COUNTRY_RESPONSE", country).toJson();
        } catch (IOException e) {
            return createErrorResponse("Không tra cứu được quốc gia: " + e.getMessage());
        }
    }

    private String handleGetHotels(Message request) {

        String cityName =
                getStringData(
                        request.getData(),
                        "cityName"
                );

        List<Hotel> hotels =
                hotelDAO.getHotelsByCity(cityName);

        return new Message(
                "GET_HOTELS_RESPONSE",
                hotels
        ).toJson();
    }

    private String handleSaveLocation(Message request) {

        JsonObject data =
                gson.toJsonTree(request.getData())
                        .getAsJsonObject();

        String name =
                data.get("name").getAsString();

        double lat = data.get("lat").getAsDouble();
        double lng = data.get("lng").getAsDouble();

        String note =
                data.has("note")
                        ? data.get("note").getAsString()
                        : "";

        boolean success =
                locationDAO.saveLocation(
                        currentUserId,
                        name,
                        lat,
                        lng,
                        note
                );

        return new Message(
                "SAVE_LOCATION_RESPONSE",
                success
        ).toJson();
    }

    private String handleGetSavedLocations(
            Message request
    ) {
        List<Location> locations =
                locationDAO.getByUserId(currentUserId);

        return new Message(
                "GET_SAVED_LOCATIONS_RESPONSE",
                locations
        ).toJson();
    }

    private String handleDeleteLocation(Message request) {

        JsonObject data =
                gson.toJsonTree(request.getData())
                        .getAsJsonObject();

        int id =
                data.get("id").getAsInt();

        boolean success =
                locationDAO.deleteById(
                        id,
                        currentUserId
                );

        return new Message(
                "DELETE_LOCATION_RESPONSE",
                success
        ).toJson();
    }

    private String handleSaveHistory(Message request) {

        JsonObject data =
                gson.toJsonTree(request.getData())
                        .getAsJsonObject();

        String keyword =
                data.get("keyword").getAsString();

        boolean success =
                historyDAO.saveHistory(
                        currentUserId,
                        keyword,
                        data.get("type").getAsString()
                );

        return new Message(
                "SAVE_HISTORY_RESPONSE",
                success
        ).toJson();
    }

    private String handleGetHistory(Message request) {

        List<model.HistoryEntry> history =
                historyDAO.getHistoryByUserId(
                        currentUserId
                );

        return new Message(
                "GET_HISTORY_RESPONSE",
                history
        ).toJson();
    }

    private String handleSubscribeWeather(
            Message request
    ) {
        JsonObject data =
                gson.toJsonTree(request.getData())
                        .getAsJsonObject();

        String location =
                data.get("location").getAsString();

        String condition =
                data.get("condition").getAsString();

        // A name-only subscription remains valid. Do not invent a location at (0, 0).
        boolean success = data.has("lat")
                ? subscriptionDAO.subscribe(currentUserId.intValue(), location,
                        data.get("lat").getAsDouble(), data.get("lng").getAsDouble(), condition)
                : subscriptionDAO.subscribe(currentUserId.intValue(), location, (Double) null, (Double) null, condition);

        return new Message(
                "SUBSCRIBE_WEATHER_RESPONSE",
                success
        ).toJson();
    }

    private String handleGetSubscriptions(
            Message request
    ) {
        List<String[]> subs =
                subscriptionDAO.getByUserId(
                        currentUserId
                );

        return new Message(
                "GET_SUBSCRIPTIONS_RESPONSE",
                subs
        ).toJson();
    }

    private String getStringData(
            Object data,
            String objectField
    ) {
        if (data == null) {
            return "";
        }

        JsonElement dataElement =
                gson.toJsonTree(data);

        if (dataElement.isJsonPrimitive()) {
            return dataElement.getAsString();
        }

        if (dataElement.isJsonObject()) {
            JsonObject dataObject =
                    dataElement.getAsJsonObject();

            if (
                    dataObject.has(objectField)
                            && !dataObject.get(objectField)
                                    .isJsonNull()
            ) {
                return dataObject.get(objectField)
                        .getAsString();
            }
        }

        return "";
    }

    private String createErrorResponse(
            String errorMessage
    ) {
        return new Message(
                "ERROR",
                errorMessage
        ).toJson();
    }
}
