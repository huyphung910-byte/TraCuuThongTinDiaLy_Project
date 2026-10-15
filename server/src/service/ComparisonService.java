package service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import model.Country;
import model.Location;
import model.WeatherData;

import java.io.IOException;
import java.util.concurrent.*;

/** Builds two columns independently so one unavailable API does not erase other data. */
public final class ComparisonService {
    private static final long REQUEST_SECONDS = 8;
    // ClientHandler creates a service for each request; share successful capital resolutions across them.
    private static final CapitalGeocodingService DEFAULT_CAPITALS = new CapitalGeocodingService();
    private final Gson gson = new Gson();
    private final CountryAPIService countries;
    private final WeatherAPIService weather;
    private final CapitalGeocodingService capitals;

    public ComparisonService(CountryAPIService countries, WeatherAPIService weather) {
        this(countries, weather, DEFAULT_CAPITALS);
    }

    ComparisonService(CountryAPIService countries, WeatherAPIService weather, CapitalGeocodingService capitals) {
        this.countries = countries;
        this.weather = weather;
        this.capitals = capitals;
    }

    public JsonObject compareCities(Location left, Location right) throws IOException {
        if (left == null || right == null) throw new IOException("Hãy tìm và chọn đủ hai thành phố.");
        if (left.getId() == right.getId()) throw new IOException("Hãy chọn hai thành phố khác nhau.");
        long deadline = deadline();
        CompletableFuture<Country> leftCountry = countryForCity(left);
        CompletableFuture<Country> rightCountry = countryForCity(right);
        CompletableFuture<WeatherData> leftWeather = async(() -> weather.getWeatherData(
                left.getCityName(), left.getLatitude(), left.getLongitude()));
        CompletableFuture<WeatherData> rightWeather = async(() -> weather.getWeatherData(
                right.getCityName(), right.getLatitude(), right.getLongitude()));
        JsonObject result = new JsonObject();
        result.add("left", citySide(left, leftCountry, leftWeather, deadline));
        result.add("right", citySide(right, rightCountry, rightWeather, deadline));
        return result;
    }

    public JsonObject compareCountries(String leftName, String rightName) throws IOException {
        if (leftName == null || rightName == null || leftName.trim().isEmpty() || rightName.trim().isEmpty()) {
            throw new IOException("Nhập đủ hai quốc gia để so sánh.");
        }
        long deadline = deadline();
        CompletableFuture<Country> leftCountry = async(() -> countries.getCountry(leftName));
        CompletableFuture<Country> rightCountry = async(() -> countries.getCountry(rightName));
        CompletableFuture<WeatherData> leftWeather = capitalWeather(leftCountry, deadline);
        CompletableFuture<WeatherData> rightWeather = capitalWeather(rightCountry, deadline);
        JsonObject left = countrySide(leftCountry, leftWeather, deadline);
        JsonObject right = countrySide(rightCountry, rightWeather, deadline);
        if (left.has("country") && right.has("country")) {
            String leftCode = left.getAsJsonObject("country").get("maQuocGia").getAsString();
            String rightCode = right.getAsJsonObject("country").get("maQuocGia").getAsString();
            if (!leftCode.isEmpty() && leftCode.equalsIgnoreCase(rightCode)) {
                throw new IOException("Hai tên/mã vừa nhập cùng một quốc gia. Hãy chọn hai quốc gia khác nhau.");
            }
        }
        JsonObject result = new JsonObject();
        result.add("left", left);
        result.add("right", right);
        return result;
    }

    private CompletableFuture<Country> countryForCity(Location city) {
        return async(() -> city.getCountryCode() == null || city.getCountryCode().isBlank()
                ? countries.getCountry(city.getCountry()) : countries.getByCode(city.getCountryCode()));
    }

    private CompletableFuture<WeatherData> capitalWeather(CompletableFuture<Country> country, long deadline) {
        return country.thenApplyAsync(value -> {
            try {
                if (System.nanoTime() >= deadline) throw new IOException("Hết thời gian chờ thời tiết thủ đô.");
                String capital = firstCapital(value);
                if (capital.isBlank()) throw new IOException("API quốc gia chưa có thủ đô để lấy thời tiết đại diện.");
                if (value.getCapitalLatitude() != null && value.getCapitalLongitude() != null) {
                    return weather.getWeatherData(capital, value.getCapitalLatitude(), value.getCapitalLongitude());
                }
                CapitalGeocodingService.Coordinates coordinates = capitals.resolve(capital, value.getMaQuocGia(), deadline);
                if (System.nanoTime() >= deadline) throw new IOException("Hết thời gian chờ thời tiết thủ đô.");
                return weather.getWeatherData(capital, coordinates.getLatitude(), coordinates.getLongitude());
            } catch (IOException e) { throw new CompletionException(e); }
        });
    }

    private JsonObject citySide(Location city, CompletableFuture<Country> country,
                                CompletableFuture<WeatherData> data, long deadline) {
        JsonObject side = new JsonObject();
        side.add("city", gson.toJsonTree(city));
        side.addProperty("weatherLocation", city.getCityName());
        try { side.add("country", gson.toJsonTree(await(country, deadline))); }
        catch (IOException e) { side.addProperty("countryError", e.getMessage()); }
        addWeather(side, data, deadline);
        return side;
    }

    private JsonObject countrySide(CompletableFuture<Country> country,
                                   CompletableFuture<WeatherData> data, long deadline) {
        JsonObject side = new JsonObject();
        try {
            Country value = await(country, deadline);
            side.add("country", gson.toJsonTree(value));
            side.addProperty("weatherLocation", firstCapital(value));
            addWeather(side, data, deadline);
        } catch (IOException e) {
            side.addProperty("error", e.getMessage());
            data.cancel(true);
        }
        return side;
    }

    private void addWeather(JsonObject side, CompletableFuture<WeatherData> data, long deadline) {
        try { side.add("weather", gson.toJsonTree(await(data, deadline))); }
        catch (IOException e) { side.addProperty("weatherError", e.getMessage()); }
    }

    private static long deadline() { return System.nanoTime() + TimeUnit.SECONDS.toNanos(REQUEST_SECONDS); }
    private static String firstCapital(Country country) {
        if (country.getPrimaryCapital() != null) return country.getPrimaryCapital();
        return country.getThuDo() == null ? "" : country.getThuDo();
    }

    private static <T> CompletableFuture<T> async(IOCallable<T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try { return operation.call(); }
            catch (IOException e) { throw new CompletionException(e); }
        });
    }

    private static <T> T await(CompletableFuture<T> task, long deadline) throws IOException {
        try {
            // Even when another side consumed the budget, preserve an already completed successful side.
            if (task.isDone()) return task.get();
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new TimeoutException();
            return task.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            throw new IOException("Hết thời gian chờ dữ liệu so sánh. Hãy thử lại.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.cancel(true);
            throw new IOException("Đã hủy yêu cầu so sánh.");
        } catch (ExecutionException | CancellationException e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            throw new IOException(cause instanceof IOException ? cause.getMessage()
                    : "Không tải được dữ liệu so sánh từ API.");
        }
    }

    @FunctionalInterface
    private interface IOCallable<T> { T call() throws IOException; }
}
