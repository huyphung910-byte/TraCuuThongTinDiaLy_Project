import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import model.Location;
import model.Message;
import model.User;
import model.WeatherData;
import dao.LocationDAO;
import dao.UserDAO;
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
    private final WeatherAPIService weatherAPIService;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.gson = new Gson();
        this.userDAO = new UserDAO();
        this.locationDAO = new LocationDAO();
        this.weatherAPIService = new WeatherAPIService();
    }

    @Override
    public void run() {
        System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

        try (Socket socket = clientSocket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            String requestJson;
            while ((requestJson = reader.readLine()) != null) {
                if (!requestJson.trim().isEmpty()) {
                    writer.println(handleRequest(requestJson));
                }
            }
        } catch (IOException exception) {
            System.err.println("Client communication error: " + exception.getMessage());
        } finally {
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
                case "SEARCH_LOCATION":
                    return handleLocationSearch(request);
                case "GET_WEATHER":
                    return handleWeather(request);
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
        LoginData loginData = gson.fromJson(gson.toJson(request.getData()), LoginData.class);
        if (loginData == null || loginData.username == null || loginData.password == null) {
            return createErrorResponse("LOGIN requires username and password");
        }

        User user = userDAO.checkLogin(loginData.username, loginData.password);
        return new Message("LOGIN_RESPONSE", user).toJson();
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

    private static class LoginData {
        private String username;
        private String password;
    }
}