package service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import model.WeatherData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class WeatherAPIService {
    private static final String API_URL = "https://api.openweathermap.org/data/2.5/weather";
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int READ_TIMEOUT_MILLIS = 5000;

    private final Gson gson;

    public WeatherAPIService() {
        this.gson = new Gson();
    }

    public WeatherData getWeatherData(String cityName) {
        String normalizedCityName = cityName == null ? "" : cityName.trim();
        String apiKey = getApiKey();

        if (normalizedCityName.isEmpty() || apiKey.isEmpty()) {
            return createMockData(normalizedCityName);
        }

        HttpURLConnection connection = null;
        try {
            String encodedCityName = URLEncoder.encode(normalizedCityName, StandardCharsets.UTF_8.name());
            String requestUrl = API_URL + "?q=" + encodedCityName + "&appid=" + apiKey + "&units=metric";
            connection = (HttpURLConnection) new URL(requestUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("OpenWeatherMap returned HTTP " + responseCode);
            }

            String responseJson = readResponse(connection.getInputStream());
            return parseWeatherData(responseJson, normalizedCityName);
        } catch (IOException | RuntimeException exception) {
            exception.printStackTrace();
            return createMockData(normalizedCityName);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String getApiKey() {
        String apiKey = System.getenv("OPENWEATHER_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = System.getProperty("openweather.api.key", "");
        }
        return apiKey.trim();
    }

    private String readResponse(InputStream inputStream) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private WeatherData parseWeatherData(String responseJson, String requestedCityName) {
        JsonObject weatherJson = gson.fromJson(responseJson, JsonObject.class);
        JsonObject mainJson = weatherJson.getAsJsonObject("main");
        JsonObject windJson = weatherJson.getAsJsonObject("wind");
        JsonArray weatherArray = weatherJson.getAsJsonArray("weather");
        String description = weatherArray != null && !weatherArray.isEmpty()
                ? weatherArray.get(0).getAsJsonObject().get("description").getAsString()
                : "Unknown";
        String actualCityName = weatherJson.has("name")
                ? weatherJson.get("name").getAsString()
                : requestedCityName;
        String updatedAt = weatherJson.has("dt")
                ? Instant.ofEpochSecond(weatherJson.get("dt").getAsLong()).toString()
                : Instant.now().toString();

        return new WeatherData(
                actualCityName,
                mainJson.get("temp").getAsDouble(),
                mainJson.get("humidity").getAsInt(),
                description,
                windJson != null && windJson.has("speed") ? windJson.get("speed").getAsDouble() : 0.0,
                updatedAt
        );
    }

    private WeatherData createMockData(String cityName) {
        return new WeatherData(
                cityName.isEmpty() ? "Unknown" : cityName,
                28.0,
                70,
                "Clear sky (mock data)",
                3.5,
                Instant.now().toString()
        );
    }
}