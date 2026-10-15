package server;

import java.util.concurrent.ConcurrentHashMap;

public final class ClientManager {

    private static final ConcurrentHashMap<Integer, ClientSession>
            sessions = new ConcurrentHashMap<>();

    private ClientManager() {
    }

    public static void addClient(
            int userId,
            ClientSession session
    ) {
        sessions.put(userId, session);
    }

    public static void removeClient(
            int userId,
            ClientSession session
    ) {
        sessions.remove(userId, session);
    }

    public static ClientSession getSession(int userId) {
        return sessions.get(userId);
    }
}