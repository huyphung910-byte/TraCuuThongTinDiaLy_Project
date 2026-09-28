import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
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
import service.CountryAPIService;
import service.WeatherAPIService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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
    private Integer currentUserId = null;
    private PrintWriter writer;

    public ClientHandler(Socket clientSocket) {
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
        System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);
            
            String requestJson;
            while ((requestJson = reader.readLine()) != null) {
                if (!requestJson.trim().isEmpty()) {
                    writer.println(handleRequest(requestJson));
                }
            }
        } catch (IOException exception) {
            System.err.println("Client communication error: " + exception.getMessage());
        } finally {
            if (currentUserId != null) {
                ClientManager.removeClient(currentUserId);
            }
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Client disconnected: " + clientSocket.getRemoteSocketAddress());
        }
    }

    private String handleRequest(String requestJson) {
        try {
            Message request = gson.fromJson(requestJson, Message.class);
            if (request == null || request.getAction() == null || request.getAction().trim().isEmpty()) {
                return createErrorResponse("Missing action");
            }

            switch (request.getAction().trim().toUpperCase()) {
                case "LOGIN":
                    return handleLogin(request);
                case "REGISTER":
                    return handleRegister(request);
                case "UPDATE_PROFILE":
                    return handleUpdateProfile(request);
                case "SEARCH_LOCATION":
                    return handleLocationSearch(request);
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
                default:
                    return createErrorResponse("Unsupported action: " + request.getAction());
            }
        } catch (JsonParseException | IllegalArgumentException exception) {
            return createErrorResponse("Invalid JSON request");
        } catch (RuntimeException exception) {
            exception.printStackTrace();
            return createErrorResponse("Server error while processing request");
        }
    }

    private String handleLogin(Message request) {
        JsonObject data = gson.toJsonTree(request.getData()).getAsJsonObject();
        String username = data.has("username") ? data.get("username").getAsString() : null;
        String password = data.has("password") ? data.get("password").getAsString() : null;

        if (username == null || password == null) {
            return createErrorResponse("LOGIN requires username and password");
        }

        User user = userDAO.checkLogin(username, password);
        if (user != null) {
            this.currentUserId = user.getId();
            ClientManager.addClient(user.getId(), this.writer);
            return new Message("LOGIN_RESPONSE", user).toJson();
        }
        return new Message("LOGIN_RESPONSE", null).toJson();
    }

    private String handleRegister(Message request) {
        JsonObject data = gson.toJsonTree(request.getData()).getAsJsonObject();
        String username = data.has("username") ? data.get("username").getAsString() : null;
        String password = data.has("password") ? data.get("password").getAsString() : null;
        String fullname = data.has("fullname") ? data.get("fullname").getAsString() : null;
        String email = data.has("email") ? data.get("email").getAsString() : null;

        boolean success = userDAO.register(username, password, fullname, email);
        return new Message("REGISTER_RESPONSE", success).toJson();
    }

    private String handleUpdateProfile(Message request) {
        JsonObject data = gson.toJsonTree(request.getData()).getAsJsonObject();
        int id = data.has("id") ? data.get("id").getAsInt() : -1;
        String fullname = data.has("fullname") ? data.get("fullname").getAsString() : null;
        String email = data.has("email") ? data.get("email").getAsString() : null;

        boolean success = userDAO.updateProfile(id, fullname, email);
        return new Message("UPDATE_PROFILE_RESPONSE", success).toJson();
    }

    private String handleLocationSearch(Message request) {
        String keyword = getStringData(request.getData(), "keyword");
        List<Location> locations = locationDAO.searchByName(keyword);
        return new Message("SEARCH_LOCATION_RESPONSE", locations).toJson();
    }

    private String handleWeather(Message request) {
        String cityName = getStringData(request.getData(), "cityName");
        WeatherData weatherData = weatherAPIService.getWeatherData(cityName);
        return new Message("GET_WEATHER_RESPONSE", weatherData).toJson();
    }

    private String handleSearchCountry(Message request) {
        String countryName = getStringData(request.getData(), "countryName");
        // Try DB first
        List<Country> list = countryDAO.searchByName(countryName);
        if (!list.isEmpty()) {
            return new Message("SEARCH_COUNTRY_RESPONSE", list.get(0)).toJson();
        }
        // Fallback to API
        Country country = countryAPIService.searchCountry(countryName);
        return new Message("SEARCH_COUNTRY_RESPONSE", country).toJson();
    }

    private String handleGetHotels(Message request) {
        String cityName = getStringData(request.getData(), "cityName");
        List<Hotel> hotels = hotelDAO.getHotelsByCity(cityName);
        return new Message("GET_HOTELS_RESPONSE", hotels).toJson();
    }

    private String handleSaveLocation(Message request) {
        JsonObject data = gson.toJsonTree(request.getData()).getAsJsonObject();
        int userId = data.get("userId").getAsInt();
        String name = data.get("name").getAsString();
        double lat = data.has("lat") ? data.get("lat").getAsDouble() : 0.0;
        double lng = data.has("lng") ? data.get("lng").getAsDouble() : 0.0;
        String note = data.has("note") ? data.get("note").getAsString() : "";

        boolean success = locationDAO.saveLocation(userId, name, lat, lng, note);
        return new Message("SAVE_LOCATION_RESPONSE", success).toJson();
    }

    private String handleGetSavedLocations(Message request) {
        int userId = Integer.parseInt(getStringData(request.getData(), "userId"));
        List<Location> locations = locationDAO.getByUserId(userId);
        return new Message("GET_SAVED_LOCATIONS_RESPONSE", locations).toJson();
    }

    private String handleDeleteLocation(Message request) {
        JsonObject data = gson.toJsonTree(request.getData()).getAsJsonObject();
        int id = data.get("id").getAsInt();
        int userId = data.get("userId").getAsInt();
        boolean success = locationDAO.deleteById(id, userId);
        return new Message("DELETE_LOCATION_RESPONSE", success).toJson();
    }

    private String handleSaveHistory(Message request) {
        JsonObject data = gson.toJsonTree(request.getData()).getAsJsonObject();
        int userId = data.get("userId").getAsInt();
        String keyword = data.get("keyword").getAsString();
        boolean success = historyDAO.saveHistory(userId, keyword);
        return new Message("SAVE_HISTORY_RESPONSE", success).toJson();
    }

    private String handleGetHistory(Message request) {
        int userId = Integer.parseInt(getStringData(request.getData(), "userId"));
        List<String> history = historyDAO.getHistoryByUserId(userId);
        return new Message("GET_HISTORY_RESPONSE", history).toJson();
    }

    private String handleSubscribeWeather(Message request) {
        JsonObject data = gson.toJsonTree(request.getData()).getAsJsonObject();
        int userId = data.get("userId").getAsInt();
        String location = data.get("location").getAsString();
        double lat = data.has("lat") ? data.get("lat").getAsDouble() : 0.0;
        double lng = data.has("lng") ? data.get("lng").getAsDouble() : 0.0;
        String condition = data.get("condition").getAsString();

        boolean success = subscriptionDAO.subscribe(userId, location, lat, lng, condition);
        return new Message("SUBSCRIBE_WEATHER_RESPONSE", success).toJson();
    }

    private String handleGetSubscriptions(Message request) {
        int userId = Integer.parseInt(getStringData(request.getData(), "userId"));
        List<String[]> subs = subscriptionDAO.getByUserId(userId);
        return new Message("GET_SUBSCRIPTIONS_RESPONSE", subs).toJson();
    }

    private String getStringData(Object data, String objectField) {
        if (data == null) {
            return "";
        }
        JsonElement dataElement = gson.toJsonTree(data);
        if (dataElement.isJsonPrimitive()) {
            return dataElement.getAsString();
        }
        if (dataElement.isJsonObject()) {
            JsonObject dataObject = dataElement.getAsJsonObject();
            if (dataObject.has(objectField) && !dataObject.get(objectField).isJsonNull()) {
                return dataObject.get(objectField).getAsString();
            }
        }
        return "";
    }

    private String createErrorResponse(String errorMessage) {
        return new Message("ERROR", errorMessage).toJson();
    }
}