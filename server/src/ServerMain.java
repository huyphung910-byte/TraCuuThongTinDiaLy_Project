import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import service.WeatherMonitorService;

public class ServerMain {
    private static final int PORT = 8888;

    public static void main(String[] args) {
        WeatherMonitorService weatherMonitor = new WeatherMonitorService();
        weatherMonitor.start();
        System.out.println("WeatherMonitorService started.");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                Thread clientThread = new Thread(new ClientHandler(clientSocket));
                clientThread.start();
            }
        } catch (IOException exception) {
            System.err.println("Unable to start server on port " + PORT + ": " + exception.getMessage());
        }
    }
}