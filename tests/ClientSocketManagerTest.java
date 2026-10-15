package network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import model.Message;
import security.SecureChannel;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises real encrypted TCP connections without a database or external API. */
public final class ClientSocketManagerTest {
    private static final Gson GSON = new Gson();
    private static final long TIMEOUT_SECONDS = 4;

    public static void main(String[] args) throws Exception {
        String[] propertyNames = {"server.host", "server.port", "secure.serverPublicKey"};
        String[] previousProperties = new String[propertyNames.length];
        for (int i = 0; i < propertyNames.length; i++) {
            previousProperties[i] = System.getProperty(propertyNames[i]);
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        Path publicKey = Files.createTempFile("geo-client-socket-test-", ".key");
        ExecutorService requests = daemonExecutor("test-client-request");
        try {
            Files.write(publicKey, keys.getPublic().getEncoded());
            System.setProperty("server.host", "127.0.0.1");
            System.setProperty("secure.serverPublicKey", publicKey.toString());
            responseAndPushes(keys, requests);
            suppressQueuedPushes(keys, requests);
            closeWaitingRequestAndReconnect(keys, requests);
            invalidInputDoesNotWriteOrClose(keys, requests);
            countryLookupAllowsEnrichmentTime(keys, requests);
            System.out.println("ClientSocketManagerTest: all checks passed.");
        } finally {
            requests.shutdownNow();
            requests.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (int i = 0; i < propertyNames.length; i++) {
                if (previousProperties[i] == null) System.clearProperty(propertyNames[i]);
                else System.setProperty(propertyNames[i], previousProperties[i]);
            }
            Files.deleteIfExists(publicKey);
        }
    }

    private static void responseAndPushes(KeyPair keys, ExecutorService requests) throws Exception {
        try (Fixture server = new Fixture(keys); ClientSocketManager client = new ClientSocketManager()) {
            List<String> alerts = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch delivered = new CountDownLatch(2);
            AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
            client.setAlertListener(text -> {
                if (!SwingUtilities.isEventDispatchThread()) {
                    callbackFailure.set(new AssertionError("Alerts must run on the Swing EDT."));
                }
                alerts.add(text);
                delivered.countDown();
            });

            JsonObject payload = new JsonObject();
            payload.addProperty("city", "Hà Nội");
            payload.addProperty("userId", 999);
            Future<Message> response = requests.submit(() -> client.sendRequest(new Message("PING", payload)));
            Incoming request = server.nextRequest();
            equal("PING", request.message.getAction(), "Encrypted request action");
            equal("Hà Nội", GSON.toJsonTree(request.message.getData()).getAsJsonObject()
                    .get("city").getAsString(), "UTF-8 request payload");
            request.session.send(new Message("WEATHER_ALERT", "Có mưa"));
            request.session.send(new Message("WEATHER_ERROR", "Không lấy được thời tiết"));
            request.session.send(new Message("PING_RESPONSE", "Đã nhận"));

            Message result = response.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            equal("PING_RESPONSE", result.getAction(), "Pushes must not consume the request response");
            equal("Đã nhận", result.getData(), "Encrypted UTF-8 response");
            await(delivered, "Weather pushes were not delivered");
            equal("Có mưa", alerts.get(0), "Weather alert payload");
            equal("Không lấy được thời tiết", alerts.get(1), "Weather error payload");
            check(callbackFailure.get() == null, "Alert callback thread: " + callbackFailure.get());
            server.assertHealthy();
        }
    }

    private static void suppressQueuedPushes(KeyPair keys, ExecutorService requests) throws Exception {
        try (Fixture server = new Fixture(keys); ClientSocketManager client = new ClientSocketManager()) {
            AtomicInteger delivered = new AtomicInteger();
            client.setAlertListener(text -> delivered.incrementAndGet());
            try (EdtBlock blocked = new EdtBlock()) {
                queuePushBeforeResponse(server, client, requests, "cleared listener");
                // Receiving the following response proves the reader already queued the earlier push.
                client.setAlertListener(null);
            }
            equal(0, delivered.get(), "Clearing a listener must invalidate its queued callbacks");

            client.setAlertListener(text -> delivered.incrementAndGet());
            try (EdtBlock blocked = new EdtBlock()) {
                queuePushBeforeResponse(server, client, requests, "closed connection");
                client.close();
            }
            equal(0, delivered.get(), "Closing a connection must invalidate its queued callbacks");
            server.assertHealthy();
        }
    }

    private static void queuePushBeforeResponse(Fixture server, ClientSocketManager client,
                                                ExecutorService requests, String text) throws Exception {
        Future<Message> response = requests.submit(() -> client.sendRequest(new Message("PING", null)));
        Incoming incoming = server.nextRequest();
        incoming.session.send(new Message("WEATHER_ALERT", text));
        incoming.session.send(new Message("PING_RESPONSE", true));
        equal("PING_RESPONSE", response.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getAction(),
                "Response behind queued push");
    }

    private static void closeWaitingRequestAndReconnect(KeyPair keys, ExecutorService requests) throws Exception {
        try (Fixture server = new Fixture(keys); ClientSocketManager oldClient = new ClientSocketManager()) {
            AtomicInteger oldAlerts = new AtomicInteger();
            oldClient.setAlertListener(text -> oldAlerts.incrementAndGet());
            Future<IOException> waiting = requests.submit(() -> {
                try {
                    oldClient.sendRequest(new Message("WAIT_WITHOUT_RESPONSE", null));
                    throw new AssertionError("An interrupted request unexpectedly succeeded.");
                } catch (IOException expected) {
                    return expected;
                }
            });
            Incoming oldRequest = server.nextRequest();
            long beforeClose = System.nanoTime();
            oldClient.close();
            IOException failure = waiting.get(2, TimeUnit.SECONDS);
            check(failure.getMessage() != null && !failure.getMessage().isEmpty(),
                    "Closing must return a useful connection error");
            check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - beforeClose) < 2_000,
                    "Closing must interrupt the wait instead of waiting for the ten-second timeout");
            try {
                oldClient.sendRequest(new Message("PING", null));
                throw new AssertionError("A closed connection accepted another request.");
            } catch (IOException expected) {
                // The old login connection remains closed.
            }

            try (ClientSocketManager freshClient = new ClientSocketManager()) {
                List<String> freshAlerts = Collections.synchronizedList(new ArrayList<>());
                CountDownLatch delivered = new CountDownLatch(1);
                freshClient.setAlertListener(text -> {
                    freshAlerts.add(text);
                    delivered.countDown();
                });
                JsonObject credentials = new JsonObject();
                credentials.addProperty("username", "new-user");
                credentials.addProperty("password", "FixturePassword1!");
                Future<Message> response = requests.submit(() -> freshClient.sendRequest(new Message("LOGIN", credentials)));
                Incoming freshRequest = server.nextRequest();
                check(oldRequest.session.id != freshRequest.session.id,
                        "A new manager must establish a distinct encrypted connection");
                equal("LOGIN", freshRequest.message.getAction(), "Fresh connection request");
                freshRequest.session.send(new Message("WEATHER_ALERT", "new session alert"));
                freshRequest.session.send(new Message("LOGIN_RESPONSE", "new session response"));
                Message result = response.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                equal("LOGIN_RESPONSE", result.getAction(), "Fresh connection response action");
                equal("new session response", result.getData(), "Old failures must not reach the fresh connection");
                await(delivered, "Fresh connection alert was not delivered");
                equal(Collections.singletonList("new session alert"), freshAlerts, "Fresh connection alerts");
                equal(0, oldAlerts.get(), "Fresh alerts must not reach the closed connection");
            }
            server.assertHealthy();
        }
    }

    private static ExecutorService daemonExecutor(String name) {
        return Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void countryLookupAllowsEnrichmentTime(KeyPair keys, ExecutorService requests) throws Exception {
        try (Fixture server = new Fixture(keys); ClientSocketManager client = new ClientSocketManager()) {
            JsonObject query = new JsonObject();
            query.addProperty("countryName", "VN");
            Future<Message> response = requests.submit(() -> client.sendRequest(new Message("SEARCH_COUNTRY", query)));
            Incoming incoming = server.nextRequest();
            // A cold lookup can take longer than the former ten-second socket timeout.
            Thread.sleep(11_000);
            incoming.session.send(new Message("SEARCH_COUNTRY_RESPONSE", "country details"));
            equal("SEARCH_COUNTRY_RESPONSE", response.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getAction(),
                    "Country enrichment must fit its fifteen-second response budget");
            server.assertHealthy();
        }
    }

    private static void invalidInputDoesNotWriteOrClose(KeyPair keys, ExecutorService requests) throws Exception {
        try (Fixture server = new Fixture(keys); ClientSocketManager client = new ClientSocketManager()) {
            for (Message invalid : new Message[]{new Message("REGISTER", new JsonObject()),
                    new Message("SEARCH_COUNTRY", "   "),
                    new Message("SAVE_LOCATION", com.google.gson.JsonParser.parseString("{\"name\":\"Hà Nội\",\"lat\":91,\"lng\":105}"))}) {
                try {
                    client.sendRequest(invalid);
                    throw new AssertionError("Invalid input must be rejected locally");
                } catch (util.InputValidation.ValidationException expected) { }
            }
            Future<Message> response = requests.submit(() -> client.sendRequest(new Message("SEARCH_COUNTRY", " VN ")));
            Incoming incoming = server.nextRequest();
            equal("SEARCH_COUNTRY", incoming.message.getAction(), "Invalid data must never appear on the wire");
            equal("VN", GSON.toJsonTree(incoming.message.getData()).getAsJsonObject().get("countryName").getAsString(),
                    "Valid request is normalized before encryption");
            incoming.session.send(new Message("SEARCH_COUNTRY_RESPONSE", "Valid after local errors"));
            equal("SEARCH_COUNTRY_RESPONSE", response.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getAction(),
                    "Validation errors preserve the working connection");
            check(server.incoming.isEmpty(), "Local errors must not leave extra requests or replies");
            server.assertHealthy();
        }
    }

    private static void await(CountDownLatch latch, String message) throws InterruptedException {
        check(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual, String message) {
        check(java.util.Objects.equals(expected, actual), message + ": expected=" + expected + ", actual=" + actual);
    }

    private static final class EdtBlock implements AutoCloseable {
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        EdtBlock() throws InterruptedException {
            CountDownLatch started = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                started.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        failure.set(new AssertionError("Timed out waiting to release the EDT."));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failure.set(e);
                }
            });
            await(started, "EDT blocker did not start");
        }

        @Override
        public void close() throws InterruptedException {
            release.countDown();
            CountDownLatch drained = new CountDownLatch(1);
            SwingUtilities.invokeLater(drained::countDown);
            await(drained, "Queued Swing callbacks did not drain");
            check(failure.get() == null, "EDT blocker failed: " + failure.get());
        }
    }

    private static final class Incoming {
        final Session session;
        final Message message;

        Incoming(Session session, Message message) {
            this.session = session;
            this.message = message;
        }
    }

    private static final class Session {
        final int id;
        final Socket socket;
        volatile SecureChannel channel;

        Session(int id, Socket socket) {
            this.id = id;
            this.socket = socket;
        }

        void send(Message message) throws IOException {
            channel.send(message.toJson());
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService workers = daemonExecutor("test-encrypted-server");
        private final BlockingQueue<Incoming> incoming = new LinkedBlockingQueue<>();
        private final ConcurrentHashMap<Integer, Session> sessions = new ConcurrentHashMap<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicInteger nextId = new AtomicInteger();
        private volatile boolean closing;

        Fixture(KeyPair keys) throws IOException {
            listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            System.setProperty("server.port", Integer.toString(listener.getLocalPort()));
            workers.submit(() -> {
                try {
                    while (!closing) {
                        Socket socket = listener.accept();
                        Session session = new Session(nextId.incrementAndGet(), socket);
                        sessions.put(session.id, session);
                        workers.submit(() -> receive(session, keys));
                    }
                } catch (Exception e) {
                    if (!closing) failure.compareAndSet(null, e);
                }
            });
        }

        private void receive(Session session, KeyPair keys) {
            try (Socket socket = session.socket) {
                session.channel = SecureChannel.accept(socket, keys);
                String json;
                while ((json = session.channel.receive()) != null) {
                    incoming.add(new Incoming(session, GSON.fromJson(json, Message.class)));
                }
            } catch (Exception e) {
                if (!closing) failure.compareAndSet(null, e);
            }
        }

        Incoming nextRequest() throws InterruptedException {
            Incoming result = incoming.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertHealthy();
            check(result != null, "Fixture did not receive an encrypted request");
            return result;
        }

        void assertHealthy() {
            check(failure.get() == null, "Encrypted server fixture failed: " + failure.get());
        }

        @Override
        public void close() throws Exception {
            closing = true;
            listener.close();
            for (Session session : sessions.values()) session.socket.close();
            workers.shutdownNow();
            check(workers.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Fixture workers did not stop");
            assertHealthy();
        }
    }
}
