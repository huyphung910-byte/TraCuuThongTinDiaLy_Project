package service;

import dao.WeatherSubscriptionDAO;
import model.Message;
import model.WeatherData;
import server.ClientManager;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WeatherMonitorService {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final WeatherSubscriptionDAO subscriptionDAO = new WeatherSubscriptionDAO();
    private final WeatherAPIService weatherAPIService = new WeatherAPIService();

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkWeatherAndAlert, 0, 10, TimeUnit.MINUTES);
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void checkWeatherAndAlert() {
        try {
            System.out.println("[WeatherMonitorService] Checking weather subscriptions...");
            List<String[]> subscriptions = subscriptionDAO.getAll();
            for (String[] sub : subscriptions) {
                int userId = Integer.parseInt(sub[1]);
                String location = sub[2];
                String condition = sub[5];

                WeatherData weatherData = weatherAPIService.getWeatherData(location);
                if (weatherData != null) {
                    if (shouldAlert(weatherData, condition)) {
                        sendAlertToUser(userId, location, weatherData, condition);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean shouldAlert(WeatherData data, String condition) {
        String desc = data.getDescription().toLowerCase();
        double temp = data.getTemp();
        String condLower = condition.toLowerCase();

        if (condLower.contains("mua lon") || condLower.contains("mưa lớn") || condLower.contains("bao") || condLower.contains("bão")) {
            if (desc.contains("rain") || desc.contains("storm") || desc.contains("thunderstorm")) {
                return true;
            }
        }
        if (condLower.contains("nhiet do tren 38") || condLower.contains("nhiệt độ trên 38")) {
            if (temp >= 38.0) {
                return true;
            }
        }
        if (condLower.contains("tuyet") || condLower.contains("tuyết")) {
            if (desc.contains("snow")) {
                return true;
            }
        }
        return false;
    }

    private void sendAlertToUser(int userId, String location, WeatherData data, String condition) {
        PrintWriter writer = ClientManager.getWriter(userId);
        if (writer != null) {
            String alertMsg = "CẢNH BÁO: Thời tiết tại " + location + " đang " + data.getDescription() + 
                              " (" + data.getTemp() + "°C). Khớp với điều kiện: " + condition;
            Message message = new Message("WEATHER_ALERT", alertMsg);
            writer.println(message.toJson());
        }
    }
}
