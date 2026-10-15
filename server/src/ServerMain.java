import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.security.KeyPair;

import security.ServerKeyManager;
import server.ClientConnectionPool;
import service.WeatherMonitorService;
import util.ConsoleEncoding;
import util.AppConfig;

public class ServerMain {

    public static void main(String[] args) {
        ConsoleEncoding.configureUtf8();

        final KeyPair keys;
        final AppConfig config;
        final int port, maxClients;
        final String bindAddress;

        try {
            config = AppConfig.load("server");
            port = config.positiveInt("server.port", 65535);
            maxClients = config.positiveInt("server.maxClients", 10000);
            bindAddress = config.required("server.bindAddress");
            keys = ServerKeyManager.load();

        } catch (IOException e) {
            System.err.println(
                    "Không khởi động được máy chủ: "
                            + e.getMessage()
            );

            System.err.println(
                    "Tạo khóa một lần bằng lệnh: "
                            + "java -cp bin security.ServerKeyManager"
            );

            return;
        }

        WeatherMonitorService monitor =
                new WeatherMonitorService();

        Thread shutdownHook = null;
        try (
                ClientConnectionPool clients = new ClientConnectionPool(
                        maxClients);
                ServerSocket serverSocket =
                        new ServerSocket()
        ) {
            serverSocket.bind(new InetSocketAddress(bindAddress, port));
            shutdownHook = new Thread(() -> {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.err.println("Không đóng được cổng máy chủ.");
                }
                monitor.stop();
                clients.close();
            }, "server-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            monitor.start();

            System.out.println(
                    "Máy chủ bảo mật đã khởi động tại cổng "
                            + port
            );

            while (true) {
                Socket socket =
                        serverSocket.accept();

                if (!clients.submit(socket, new ClientHandler(socket, keys))) {
                    System.err.println("Đã đạt giới hạn kết nối; đóng kết nối mới.");
                }
            }

        } catch (IOException e) {
            System.err.println(
                    "Máy chủ gặp lỗi kết nối. Kiểm tra cổng " + port + " có đang được sử dụng không."
            );

        } finally {
            monitor.stop();
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // JVM shutdown has already started.
                }
            }
        }
    }
}
