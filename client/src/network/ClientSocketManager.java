package network;

import com.google.gson.Gson;
import model.Message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ClientSocketManager implements AutoCloseable {
    private static final String HOST = "localhost";
    private static final int PORT = 8888;

    private final Gson gson;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Consumer<String> alertListener;
    private BlockingQueue<Message> responseQueue = new LinkedBlockingQueue<>();
    private Thread listenerThread;

    public ClientSocketManager() throws IOException {
        this.gson = new Gson();
        connect();
    }

    private void connect() throws IOException {
        socket = new Socket(HOST, PORT);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        
        listenerThread = new Thread(this::listenForMessages);
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void setAlertListener(Consumer<String> listener) {
        this.alertListener = listener;
    }

    private void listenForMessages() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                Message msg = gson.fromJson(line, Message.class);
                if ("WEATHER_ALERT".equals(msg.getAction())) {
                    if (alertListener != null) {
                        alertListener.accept(msg.getData().toString());
                    }
                } else {
                    responseQueue.put(msg);
                }
            }
        } catch (Exception e) {
            // Connection closed or error
        }
    }

    public synchronized Message sendRequest(Message req) throws IOException {
        if (req == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (socket == null || socket.isClosed()) {
            connect();
        }

        writer.println(req.toJson());
        try {
            Message response = responseQueue.poll(10, TimeUnit.SECONDS);
            if (response == null) {
                throw new IOException("Timeout waiting for server response");
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for response");
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }
}
