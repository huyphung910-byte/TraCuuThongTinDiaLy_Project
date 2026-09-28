package server;

import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;

public class ClientManager {
    private static final ConcurrentHashMap<Integer, PrintWriter> clientWriters = new ConcurrentHashMap<>();

    public static void addClient(int userId, PrintWriter writer) {
        clientWriters.put(userId, writer);
    }

    public static void removeClient(int userId) {
        clientWriters.remove(userId);
    }

    public static PrintWriter getWriter(int userId) {
        return clientWriters.get(userId);
    }
}
