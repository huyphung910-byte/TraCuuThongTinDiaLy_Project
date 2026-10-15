import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.LocationDAO;
import dao.UserDAO;
import model.Location;
import model.Message;
import model.User;
import security.SecureChannel;
import server.ClientManager;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real encrypted TCP regression; fake DAOs never access MySQL or external APIs. */
public final class SavedLocationsIntegrationTest {
    private static final int OWNER = 101;
    private static final int OTHER_OWNER = 202;
    private static final int OWN_LOCATION = 1001;
    private static final int FOREIGN_LOCATION = 2001;

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        FakeLocationDAO locations = new FakeLocationDAO();
        AtomicReference<Socket> acceptedSocket = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "saved-locations-test-server");
            thread.setDaemon(true);
            return thread;
        });

        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            listener.setSoTimeout(10_000);
            Future<?> server = executor.submit(() -> {
                try {
                    Socket socket = listener.accept();
                    acceptedSocket.set(socket);
                    ClientHandler handler = new ClientHandler(socket, keys);
                    inject(handler, "userDAO", new FakeUserDAO());
                    inject(handler, "locationDAO", locations);
                    handler.run();
                } catch (Exception exception) {
                    throw new RuntimeException("Loopback server failed", exception);
                }
            });

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(listener.getInetAddress(), listener.getLocalPort()), 5_000);
                try (SecureChannel channel = SecureChannel.connect(socket, keys.getPublic())) {
                    socket.setSoTimeout(5_000);

                    JsonObject unauthenticated = exchange(channel, "GET_SAVED_LOCATIONS", new JsonObject());
                    expectAction(unauthenticated, "UNAUTHORIZED");
                    check(locations.readCalls.get() == 0, "Unauthenticated reads must never reach the DAO");

                    JsonObject credentials = new JsonObject();
                    credentials.addProperty("username", "fixture-user");
                    credentials.addProperty("password", "FixturePassword1!");
                    credentials.addProperty("userId", OTHER_OWNER);
                    JsonObject login = exchange(channel, "LOGIN", credentials);
                    expectAction(login, "LOGIN_RESPONSE");
                    check(login.getAsJsonObject("data").get("id").getAsInt() == OWNER,
                            "Verified credentials must establish the account identity");

                    // This is the exact request sent by the updated SavedLocationsPanel.
                    Message read = new Message("GET_SAVED_LOCATIONS", new JsonObject());
                    JsonObject wire = JsonParser.parseString(read.toJson()).getAsJsonObject();
                    check(wire.getAsJsonObject("data").size() == 0, "The request payload must be exactly an empty object");
                    JsonObject response = exchange(channel, read);
                    expectOwnLocation(response);
                    check(locations.readCalls.get() == 1 && locations.lastReadOwner.get() == OWNER,
                            "An empty payload must read the authenticated owner's places");

                    JsonObject forgedIdentity = new JsonObject();
                    forgedIdentity.addProperty("userId", OTHER_OWNER);
                    forgedIdentity.addProperty("id", OTHER_OWNER);
                    expectOwnLocation(exchange(channel, "GET_SAVED_LOCATIONS", forgedIdentity));
                    check(locations.readCalls.get() == 2 && locations.lastReadOwner.get() == OWNER,
                            "Client account ids must never select another owner's saved places");

                    JsonObject foreignDelete = new JsonObject();
                    foreignDelete.addProperty("id", FOREIGN_LOCATION);
                    foreignDelete.addProperty("userId", OTHER_OWNER);
                    JsonObject deniedDelete = exchange(channel, "DELETE_LOCATION", foreignDelete);
                    expectAction(deniedDelete, "DELETE_LOCATION_RESPONSE");
                    check(!deniedDelete.get("data").getAsBoolean() && locations.lastDeleteOwner.get() == OWNER,
                            "A forged owner must not authorize deletion of another account's place");

                    JsonObject ownDelete = new JsonObject();
                    ownDelete.addProperty("id", OWN_LOCATION);
                    JsonObject deleted = exchange(channel, "DELETE_LOCATION", ownDelete);
                    expectAction(deleted, "DELETE_LOCATION_RESPONSE");
                    check(deleted.get("data").getAsBoolean(), "The authenticated owner can delete their own place");
                    JsonObject empty = exchange(channel, "GET_SAVED_LOCATIONS", new JsonObject());
                    expectAction(empty, "GET_SAVED_LOCATIONS_RESPONSE");
                    check(empty.getAsJsonArray("data").isEmpty(), "Empty saved-place results must serialize correctly");

                    JsonObject logout = exchange(channel, "LOGOUT", null);
                    expectAction(logout, "LOGOUT_RESPONSE");
                    check(logout.get("data").getAsBoolean(), "Logout must succeed");
                    int previousCalls = locations.readCalls.get();
                    expectAction(exchange(channel, "GET_SAVED_LOCATIONS", new JsonObject()), "UNAUTHORIZED");
                    check(locations.readCalls.get() == previousCalls,
                            "A logged-out connection must not perform another personal DAO read");
                    check(ClientManager.getSession(OWNER) == null, "Logout must remove the registered authenticated session");
                }
            }
            server.get(5, TimeUnit.SECONDS);
            check(ClientManager.getSession(OWNER) == null, "Disconnect must not retain an authenticated session");
            System.out.println("SavedLocationsIntegrationTest: passed encrypted TCP saved-place/session checks.");
        } finally {
            Socket socket = acceptedSocket.get();
            if (socket != null) socket.close();
            executor.shutdownNow();
            check(executor.awaitTermination(5, TimeUnit.SECONDS), "The loopback test server must terminate");
        }
    }

    private static JsonObject exchange(SecureChannel channel, String action, JsonObject data) throws Exception {
        return exchange(channel, new Message(action, data));
    }

    private static JsonObject exchange(SecureChannel channel, Message request) throws Exception {
        channel.send(request.toJson());
        String response = channel.receive();
        check(response != null, "The server must return a response to " + request.getAction());
        return JsonParser.parseString(response).getAsJsonObject();
    }

    private static void expectOwnLocation(JsonObject response) {
        expectAction(response, "GET_SAVED_LOCATIONS_RESPONSE");
        check(response.getAsJsonArray("data").size() == 1, "Only the authenticated owner's place may be returned");
        JsonObject place = response.getAsJsonArray("data").get(0).getAsJsonObject();
        check(place.get("id").getAsInt() == OWN_LOCATION
                        && place.get("cityName").getAsString().equals("Hà Nội"),
                "The real wire response must preserve the owner's saved-place record");
    }

    private static void expectAction(JsonObject response, String action) {
        check(response.get("action").getAsString().equals(action), "Expected " + action + ", got " + response);
    }

    private static void inject(ClientHandler handler, String name, Object value) throws Exception {
        Field field = ClientHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(handler, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeUserDAO extends UserDAO {
        @Override
        public User checkLogin(String username, String password) {
            return "fixture-user".equals(username) && "FixturePassword1!".equals(password)
                    ? new User(OWNER, username, "Người dùng kiểm thử", "fixture@example.com") : null;
        }
    }

    private static final class FakeLocationDAO extends LocationDAO {
        private final List<Location> ownPlaces = new ArrayList<>(List.of(
                new Location(OWN_LOCATION, "Hà Nội", null, 21.03, 105.85, "Địa điểm kiểm thử")));
        private final AtomicInteger readCalls = new AtomicInteger();
        private final AtomicInteger lastReadOwner = new AtomicInteger();
        private final AtomicInteger lastDeleteOwner = new AtomicInteger();

        @Override
        public List<Location> getByUserId(int userId) {
            readCalls.incrementAndGet();
            lastReadOwner.set(userId);
            if (userId == OWNER) return List.copyOf(ownPlaces);
            if (userId == OTHER_OWNER) return List.of(new Location(FOREIGN_LOCATION, "Tokyo", null, 35, 139, ""));
            return List.of();
        }

        @Override
        public boolean deleteById(int id, int userId) {
            lastDeleteOwner.set(userId);
            return userId == OWNER && ownPlaces.removeIf(place -> place.getId() == id);
        }
    }
}
