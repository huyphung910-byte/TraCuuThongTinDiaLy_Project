package server;

import model.Message;
import security.SecureChannel;

import java.io.IOException;

public final class ClientSession {

    private final SecureChannel channel;
    private Integer authenticatedUserId;

    public ClientSession(SecureChannel channel) {
        this.channel = channel;
    }

    public synchronized void send(Message message) throws IOException {
        sendJson(message.toJson());
    }

    public synchronized void sendJson(String json) throws IOException {
        channel.send(json);
    }

    public synchronized void authenticate(int userId) {
        authenticatedUserId = userId;
    }

    public synchronized void clearAuthentication() {
        authenticatedUserId = null;
    }

    /** Recheck ownership when a background task actually sends its notification. */
    public synchronized boolean sendToUser(int userId, Message message) throws IOException {
        if (authenticatedUserId == null || authenticatedUserId != userId) return false;
        send(message);
        return true;
    }
}
