package service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dao.CacheDAO;
import model.Country;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

public class CountryAPIService {
    private static final String API_URL = "https://restcountries.com/v3.1/name/";
    private final Gson gson;
    private final CacheDAO cacheDAO;

    public CountryAPIService() {
        this.gson = new Gson();
        this.cacheDAO = new CacheDAO();
    }

    public Country searchCountry(String countryName) {
        if (countryName == null || countryName.trim().isEmpty()) {
            return null;
        }
        
        String cacheKey = "country_" + countryName.trim().toLowerCase();
        String cachedJson = cacheDAO.get(cacheKey);
        
        if (cachedJson != null) {
            return parseCountry(cachedJson);
        }

        HttpURLConnection connection = null;
        try {
            String encodedName = URLEncoder.encode(countryName.trim(), StandardCharsets.UTF_8.name());
            URL url = new URL(API_URL + encodedName);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            if (connection.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                String jsonResponse = response.toString();
                Country country = parseCountry(jsonResponse);
                if (country != null) {
                    cacheDAO.put(cacheKey, jsonResponse, 24 * 60); // Cache for 24 hours
                }
                return country;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private Country parseCountry(String jsonResponse) {
        try {
            JsonArray jsonArray = gson.fromJson(jsonResponse, JsonArray.class);
            if (jsonArray.isEmpty()) {
                return null;
            }
            JsonObject countryObj = jsonArray.get(0).getAsJsonObject();
            
            String name = countryObj.getAsJsonObject("name").get("common").getAsString();
            String code = countryObj.has("cca2") ? countryObj.get("cca2").getAsString() : "";
            
            String capital = "";
            if (countryObj.has("capital")) {
                JsonArray capitals = countryObj.getAsJsonArray("capital");
                if (!capitals.isEmpty()) {
                    capital = capitals.get(0).getAsString();
                }
            }

            String currency = "";
            if (countryObj.has("currencies")) {
                JsonObject currenciesObj = countryObj.getAsJsonObject("currencies");
                Set<Map.Entry<String, JsonElement>> entries = currenciesObj.entrySet();
                if (!entries.isEmpty()) {
                    Map.Entry<String, JsonElement> firstCurrency = entries.iterator().next();
                    JsonObject currencyObj = firstCurrency.getValue().getAsJsonObject();
                    String currName = currencyObj.has("name") ? currencyObj.get("name").getAsString() : firstCurrency.getKey();
                    String symbol = currencyObj.has("symbol") ? currencyObj.get("symbol").getAsString() : "";
                    currency = currName + " (" + symbol + ")";
                }
            }

            String language = "";
            if (countryObj.has("languages")) {
                JsonObject languagesObj = countryObj.getAsJsonObject("languages");
                Set<Map.Entry<String, JsonElement>> entries = languagesObj.entrySet();
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, JsonElement> entry : entries) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(entry.getValue().getAsString());
                }
                language = sb.toString();
            }

            String flagUrl = "";
            if (countryObj.has("flags")) {
                JsonObject flagsObj = countryObj.getAsJsonObject("flags");
                if (flagsObj.has("png")) {
                    flagUrl = flagsObj.get("png").getAsString();
                }
            }
            
            String borders = "";
            if (countryObj.has("borders")) {
                JsonArray bordersArray = countryObj.getAsJsonArray("borders");
                StringBuilder sb = new StringBuilder();
                for (JsonElement el : bordersArray) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(el.getAsString());
                }
                borders = sb.toString();
            }

            return new Country(0, name, code, capital, currency, language, flagUrl, borders, "");

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
