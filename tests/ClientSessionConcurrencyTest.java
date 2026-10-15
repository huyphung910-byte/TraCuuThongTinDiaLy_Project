import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPairGenerator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import model.Message;
import security.SecureChannel;
import server.ClientSession;

/** Concurrent responses and pushes must remain complete, ordered encrypted frames. */
public final class ClientSessionConcurrencyTest {
    public static void main(String[] args) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(3);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        try (ServerSocket listener = new ServerSocket(0)) {
            Future<SecureChannel> accepted = workers.submit(() ->
                    SecureChannel.accept(listener.accept(), keys));
            try (Socket socket = new Socket("localhost", listener.getLocalPort());
                 SecureChannel client = SecureChannel.connect(socket, keys.getPublic());
                 SecureChannel server = accepted.get(15, TimeUnit.SECONDS)) {
                socket.setSoTimeout(15000);
                ClientSession session = new ClientSession(server);
                session.authenticate(7);
                CountDownLatch start = new CountDownLatch(1);
                Future<?> responses = workers.submit(() -> {
                    start.await();
                    for (int i = 0; i < 100; i++) session.sendJson(new Message("RESPONSE", i).toJson());
                    return null;
                });
                Future<?> pushes = workers.submit(() -> {
                    start.await();
                    for (int i = 0; i < 100; i++) {
                        if (!session.sendToUser(7, new Message("WEATHER_ALERT", i))) throw new AssertionError();
                    }
                    return null;
                });
                start.countDown();
                Set<String> actual = new HashSet<>();
                for (int i = 0; i < 200; i++) {
                    if (!actual.add(client.receive())) throw new AssertionError("Duplicate frame");
                }
                responses.get(15, TimeUnit.SECONDS);
                pushes.get(15, TimeUnit.SECONDS);
                for (int i = 0; i < 100; i++) {
                    if (!actual.contains(new Message("RESPONSE", i).toJson())
                            || !actual.contains(new Message("WEATHER_ALERT", i).toJson())) {
                        throw new AssertionError("Missing or corrupted frame");
                    }
                }
                session.clearAuthentication();
                if (session.sendToUser(7, new Message("WEATHER_ALERT", "late"))) {
                    throw new AssertionError("Push after logout");
                }
            }
        } finally {
            workers.shutdownNow();
        }
        System.out.println("ClientSessionConcurrencyTest: concurrent responses and pushes passed.");
    }
}
