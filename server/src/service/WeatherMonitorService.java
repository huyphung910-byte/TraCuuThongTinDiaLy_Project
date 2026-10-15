package service;

import dao.WeatherSubscriptionDAO;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import model.Message;
import model.WeatherData;
import server.ClientManager;
import server.ClientSession;

public class WeatherMonitorService {

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(1);

    private final WeatherSubscriptionDAO subscriptionDAO =
            new WeatherSubscriptionDAO();

    private final WeatherAPIService weatherAPIService =
            new WeatherAPIService();

    public void start() {
        scheduler.scheduleAtFixedRate(
                this::checkWeatherAndAlert,
                0,
                10,
                TimeUnit.MINUTES
        );
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void checkWeatherAndAlert() {

        try {
            System.out.println(
                    "[WeatherMonitorService] "
                            + "Đang kiểm tra đăng ký theo dõi thời tiết..."
            );

            List<String[]> subscriptions =
                    subscriptionDAO.getAll();

            for (String[] sub : subscriptions) {

                int userId =
                        Integer.parseInt(sub[1]);

                String location = sub[2];
                String condition = sub[5];

                final WeatherData weatherData;
                try {
                    weatherData = weatherAPIService.getWeatherData(location);
                } catch (IOException e) {
                    System.err.println("[WeatherMonitorService] " + location + ": " + e.getMessage());
                    sendWeatherError(userId, location + ": " + e.getMessage());
                    continue;
                }

                if (weatherData != null) {
                    if (
                            shouldAlert(
                                    weatherData,
                                    condition
                            )
                    ) {
                        sendAlertToUser(
                                userId,
                                location,
                                weatherData,
                                condition
                        );
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean shouldAlert(
            WeatherData data,
            String condition
    ) {
        String desc =
                data.getDescription().toLowerCase(Locale.ROOT);

        double temp = data.getTemp();

        String condLower =
                condition.toLowerCase(Locale.ROOT);

        if (
                condLower.contains("mua lon")
                        || condLower.contains("mưa lớn")
                        || condLower.contains("bao")
                        || condLower.contains("bão")
        ) {
            if (
                    desc.contains("rain")
                            || desc.contains("storm")
                            || desc.contains("thunderstorm")
                            || desc.contains("mưa")
                            || desc.contains("bão")
                            || desc.contains("dông")
                            || desc.contains("giông")
                            || desc.contains("sấm")
            ) {
                return true;
            }
        }

        if (
                condLower.contains("nhiet do tren 38")
                        || condLower.contains("nhiệt độ trên 38")
        ) {
            if (temp >= 38.0) {
                return true;
            }
        }

        if (
                condLower.contains("tuyet")
                        || condLower.contains("tuyết")
        ) {
            if (desc.contains("snow") || desc.contains("tuyết")) {
                return true;
            }
        }

        return false;
    }

    private void sendWeatherError(int userId, String message) {
        ClientSession session = ClientManager.getSession(userId);
        if (session == null) return;
        try {
            session.sendToUser(userId, new Message("WEATHER_ERROR", message));
        } catch (IOException e) {
            ClientManager.removeClient(userId, session);
        }
    }

    private void sendAlertToUser(
            int userId,
            String location,
            WeatherData data,
            String condition
    ) {
        ClientSession session =
                ClientManager.getSession(userId);

        if (session != null) {

            String alertMsg =
                    "CẢNH BÁO: Thời tiết tại "
                            + location
                            + " đang "
                            + data.getDescription()
                            + " ("
                            + String.format(Locale.forLanguageTag("vi-VN"), "%.1f", data.getTemp())
                            + "°C). Khớp với điều kiện: "
                            + condition;

            Message message =
                    new Message(
                            "WEATHER_ALERT",
                            alertMsg
                    );

            try {
                // PUSH cũng được mã hóa bằng AES.
                session.sendToUser(userId, message);

            } catch (IOException e) {
                ClientManager.removeClient(
                        userId,
                        session
                );

                System.err.println(
                        "Không gửi được cảnh báo thời tiết cho người dùng "
                                + userId
                );
            }
        }
    }
}
