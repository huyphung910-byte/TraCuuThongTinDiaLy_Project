import com.google.gson.Gson;
import model.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientSocketManager implements AutoCloseable {
    private static final String HOST = "localhost";
    private static final int PORT = 8888;

    private final Gson gson;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    public ClientSocketManager() throws IOException {
        this.gson = new Gson();
        connect();
    }

    private void connect() throws IOException {
        socket = new Socket(HOST, PORT);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    public synchronized Message sendRequest(Message req) throws IOException {
        if (req == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (socket == null || socket.isClosed()) {
            connect();
        }

        writer.println(req.toJson());
        String responseJson = reader.readLine();
        if (responseJson == null) {
            throw new IOException("Server closed the connection");
        }
        return gson.fromJson(responseJson, Message.class);
    }

    @Override
    public synchronized void close() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }
}