import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dao.HistoryDAO;
import dao.LocationDAO;
import dao.UserDAO;
import dao.WeatherSubscriptionDAO;
import model.Location;
import model.Message;
import model.User;
import server.ClientManager;
import server.ClientSession;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Offline session regression tests: fake DAOs never connect to or modify MySQL. */
public final class ClientHandlerSessionTest {
    private static final int ALICE = 101, BOB = 202;
    private static final String ALICE_PASSWORD = "AlicePass1!", BOB_PASSWORD = "BobPass2!";
    private static final Gson GSON = new Gson();
    private static final String[] PERSONAL_ACTIONS = {
            "GET_PROFILE", "UPDATE_PROFILE", "CHANGE_PASSWORD", "SAVE_LOCATION",
            "GET_SAVED_LOCATIONS", "DELETE_LOCATION", "SAVE_HISTORY", "GET_HISTORY",
            "SUBSCRIBE_WEATHER", "GET_SUBSCRIPTIONS", "UNSUBSCRIBE_WEATHER", "SEARCH_TRIP_CITIES", "PLAN_TRIP"
    };

    public static void main(String[] args) throws Exception {
        rejectsUnauthenticatedRequestsBeforePayloadParsing();
        credentialsEstablishIdentityAndSpoofedIdsNeverChangeIt();
        independentHandlersKeepPersonalDataSeparate();
        deletionRequiresOwnershipOfTheRecord();
        cancellationRequiresSubscriptionOwnership();
        logoutAndFailedLoginRemoveAuthentication();
        replacementSessionSurvivesTheOldHandlerClosing();
        disconnectClearsIdentityAndPendingCitySelections();
        System.out.println("ClientHandlerSessionTest: tất cả kiểm tra phiên đăng nhập đã đạt.");
    }

    private static void rejectsUnauthenticatedRequestsBeforePayloadParsing() throws Exception {
        Store store = new Store();
        try (Harness handler = new Harness(store)) {
            check(!handler.session.sendToUser(ALICE, new Message("WEATHER_ALERT", "fixture")),
                    "An unauthenticated connection cannot receive private background notifications");
            for (String action : java.util.stream.Stream.concat(java.util.Arrays.stream(PERSONAL_ACTIONS),
                    java.util.stream.Stream.of("SEARCH_LOCATION", "GET_CITY_DETAILS", "SEARCH_COMPARISON_CITIES",
                            "COMPARE_CITIES", "COMPARE_COUNTRIES", "GET_WEATHER", "SEARCH_COUNTRY",
                            "GET_HOTELS", "LOGOUT", "UNKNOWN_ACTION")).toList()) {
                for (String payload : new String[]{null, "[1,2]", "{\"userId\":202,\"id\":202}"}) {
                    JsonObject response = handler.send(action, payload);
                    unauthorized(response, "Unauthenticated " + action);
                    check(response.get("data").getAsString().contains("đăng nhập"),
                            "Authentication must be checked before parsing the payload: " + action);
                    check(store.calls.isEmpty(), "Unauthorized requests must never reach a personal DAO");
                    check(handler.identity() == null, "A supplied account id cannot authenticate the handler");
                }
            }
            JsonObject response = handler.send("  get_history  ", "{\"userId\":202}");
            unauthorized(response, "Normalized action still requires authentication");
            check(store.calls.isEmpty(), "Action normalization must not bypass the authentication guard");
        }
    }

    private static void credentialsEstablishIdentityAndSpoofedIdsNeverChangeIt() throws Exception {
        Store store = new Store();
        try (Harness handler = new Harness(store)) {
            login(handler, "alice", ALICE_PASSWORD, ALICE);
            check(ClientManager.getSession(ALICE) == handler.session, "Login registers this authenticated session");
            check(!handler.session.sendToUser(BOB, new Message("WEATHER_ALERT", "fixture")),
                    "Background notifications for a different account cannot reach this connection");
            success(handler.send("SAVE_LOCATION", """
                    {"userId":202,"id":202,"name":"Hà Nội","lat":21.03,"lng":105.85,"note":"Địa điểm riêng"}
                    """), "SAVE_LOCATION_RESPONSE");
            check(store.last().userId == ALICE, "Saved places use the authenticated owner");

            // Even malformed legacy account ids must be ignored, rather than parsed or trusted.
            for (String spoof : new String[]{"202", "null", "\"not-an-id\"", "[202]", "{\"id\":202}"}) {
                success(handler.send("SAVE_HISTORY", "{\"userId\":" + spoof + ",\"type\":\"thanh_pho\",\"keyword\":\"Việt Nam\"}"),
                        "SAVE_HISTORY_RESPONSE");
                check(store.last().userId == ALICE, "History ignores the supplied account id: " + spoof);
            }
            success(handler.send("SUBSCRIBE_WEATHER", """
                    {"userId":null,"location":"Hà Nội","lat":21.03,"lng":105.85,"condition":"Mưa"}
                    """), "SUBSCRIBE_WEATHER_RESPONSE");
            check(store.last().userId == ALICE, "Weather subscriptions belong to the session account");

            for (String payload : new String[]{null, "{}", "{\"userId\":202}", "{\"userId\":[202]}", "[202]", "202"}) {
                JsonObject places = handler.send("GET_SAVED_LOCATIONS", payload);
                action(places, "GET_SAVED_LOCATIONS_RESPONSE");
                check(places.getAsJsonArray("data").size() == 1
                                && places.getAsJsonArray("data").get(0).getAsJsonObject().get("cityName").getAsString().equals("Hà Nội"),
                        "Saved-place reads return the session's own data regardless of payload");
                JsonObject history = handler.send("GET_HISTORY", payload);
                action(history, "GET_HISTORY_RESPONSE");
                check(history.getAsJsonArray("data").size() == 5, "History reads do not select another account");
                JsonObject subscriptions = handler.send("GET_SUBSCRIPTIONS", payload);
                action(subscriptions, "GET_SUBSCRIPTIONS_RESPONSE");
                check(subscriptions.getAsJsonArray("data").get(0).getAsJsonArray().get(1).getAsInt() == ALICE,
                        "Subscription reads return the authenticated owner's rows");
            }

            JsonObject profile = handler.send("GET_PROFILE", "{\"id\":202,\"userId\":null}");
            action(profile, "GET_PROFILE_RESPONSE");
            check(profile.getAsJsonObject("data").get("id").getAsInt() == ALICE, "Profile cannot select another user");
            check(profile.getAsJsonObject("data").keySet().equals(java.util.Set.of("id", "username", "fullname", "email")),
                    "Only public profile fields are serialized, even if a DAO returns a User subclass with secrets");
            check(!profile.toString().contains("fixture-secret"), "Profile response must not include password material");
            action(handler.send("GET_PROFILE", "[202]"), "GET_PROFILE_RESPONSE");

            success(handler.send("UPDATE_PROFILE", """
                    {"id":{"bad":202},"userId":202,"fullname":"  Nguyễn An  ","email":"  an@example.com  "}
                    """), "UPDATE_PROFILE_RESPONSE");
            check(store.users.get(ALICE).getFullname().equals("Nguyễn An")
                            && store.users.get(ALICE).getEmail().equals("an@example.com"),
                    "Profile updates apply trimmed values to the session account");
            check(store.users.get(BOB).getFullname().equals("Bình"), "A forged id must not update another user's profile");

            success(handler.send("CHANGE_PASSWORD", """
                    {"id":202,"userId":"forged","currentPassword":"AlicePass1!","newPassword":"AliceNew3!"}
                    """), "CHANGE_PASSWORD_RESPONSE");
            check(store.passwords.get(ALICE).equals("AliceNew3!") && store.passwords.get(BOB).equals(BOB_PASSWORD),
                    "Password changes affect only the authenticated account");
            error(handler.send("CHANGE_PASSWORD", """
                    {"id":202,"currentPassword":"BobPass2!","newPassword":"Attacker9!"}
                    """), "Another account's password cannot verify the current account");
            check(store.last().userId == ALICE && store.passwords.get(BOB).equals(BOB_PASSWORD),
                    "Password verification is forwarded with the server-side identity");
            for (Call call : store.calls) check(call.userId == ALICE, "Every personal DAO operation uses currentUserId");
            check(handler.identity() == ALICE, "Account fields in requests never replace the session identity");
        }
    }

    private static void independentHandlersKeepPersonalDataSeparate() throws Exception {
        Store store = new Store();
        try (Harness alice = new Harness(store); Harness bob = new Harness(store)) {
            login(alice, "alice", ALICE_PASSWORD, ALICE);
            login(bob, "bob", BOB_PASSWORD, BOB);
            success(alice.send("SAVE_HISTORY", "{\"type\":\"thanh_pho\",\"keyword\":\"Hà Nội\",\"userId\":202}"), "SAVE_HISTORY_RESPONSE");
            success(bob.send("SAVE_HISTORY", "{\"type\":\"thanh_pho\",\"keyword\":\"Tokyo\",\"userId\":101}"), "SAVE_HISTORY_RESPONSE");
            JsonObject aliceHistory = alice.send("GET_HISTORY", "{\"userId\":202}");
            JsonObject bobHistory = bob.send("GET_HISTORY", "{\"userId\":101}");
            check(aliceHistory.getAsJsonArray("data").get(0).getAsJsonObject().get("keyword").getAsString().equals("Hà Nội"), "Alice cannot read Bob's history");
            check(bobHistory.getAsJsonArray("data").get(0).getAsJsonObject().get("keyword").getAsString().equals("Tokyo"), "Bob cannot read Alice's history");
            success(alice.send("LOGOUT", null), "LOGOUT_RESPONSE");
            unauthorized(alice.send("GET_SUBSCRIPTIONS", "{\"userId\":202}"), "Logged-out handler cannot reuse Bob's id");
            action(bob.send("GET_PROFILE", null), "GET_PROFILE_RESPONSE");
            check(bob.identity() == BOB && ClientManager.getSession(BOB) == bob.session,
                    "Logging out one client does not affect another client");
        }
    }

    private static void deletionRequiresOwnershipOfTheRecord() throws Exception {
        Store store = new Store();
        store.locations.put(1001, new OwnedLocation(ALICE, new Location(1001, "Hà Nội", null, 21, 105, "")));
        store.locations.put(2001, new OwnedLocation(BOB, new Location(2001, "Tokyo", null, 35, 139, "")));
        try (Harness handler = new Harness(store)) {
            login(handler, "alice", ALICE_PASSWORD, ALICE);
            JsonObject denied = handler.send("DELETE_LOCATION", "{\"id\":2001,\"userId\":202}");
            action(denied, "DELETE_LOCATION_RESPONSE");
            check(!denied.get("data").getAsBoolean() && store.locations.containsKey(2001),
                    "A foreign location record cannot be deleted by forging its owner's id");
            check(store.last().userId == ALICE && store.last().recordId == 2001,
                    "Delete uses the requested record id and the server's authenticated owner");
            success(handler.send("DELETE_LOCATION", "{\"id\":1001,\"userId\":[202]}"), "DELETE_LOCATION_RESPONSE");
            check(!store.locations.containsKey(1001) && store.locations.containsKey(2001),
                    "Only the authenticated user's selected record is removed");
        }
    }

    private static void cancellationRequiresSubscriptionOwnership() throws Exception {
        Store store = new Store();
        try (Harness alice = new Harness(store); Harness bob = new Harness(store)) {
            login(alice, "alice", ALICE_PASSWORD, ALICE);
            login(bob, "bob", BOB_PASSWORD, BOB);
            String payload = "{\"location\":\"Tokyo\",\"lat\":35,\"lng\":139,\"condition\":\"RAIN\"}";
            success(alice.send("SUBSCRIBE_WEATHER", payload), "SUBSCRIBE_WEATHER_RESPONSE");
            success(bob.send("SUBSCRIBE_WEATHER", payload), "SUBSCRIBE_WEATHER_RESPONSE");
            int aliceId = Integer.parseInt(store.subscriptions.get(ALICE).get(0)[0]);
            int bobId = Integer.parseInt(store.subscriptions.get(BOB).get(0)[0]);
            JsonObject denied = alice.send("UNSUBSCRIBE_WEATHER", "{\"id\":" + bobId + ",\"userId\":202}");
            action(denied, "UNSUBSCRIBE_WEATHER_RESPONSE");
            check(!denied.get("data").getAsBoolean(), "Cannot cancel another account's subscription");
            success(alice.send("UNSUBSCRIBE_WEATHER", "{\"id\":" + aliceId + ",\"userId\":202}"),
                    "UNSUBSCRIBE_WEATHER_RESPONSE");
            check(store.subscriptions.get(ALICE).isEmpty() && store.subscriptions.get(BOB).size() == 1,
                    "Cancellation removes only the authenticated owner's subscription");
            error(alice.send("UNSUBSCRIBE_WEATHER", "{\"id\":-1}"), "Invalid subscription ids are rejected");
        }
    }

    private static void logoutAndFailedLoginRemoveAuthentication() throws Exception {
        Store store = new Store();
        try (Harness handler = new Harness(store)) {
            login(handler, "alice", ALICE_PASSWORD, ALICE);
            handler.seedSelections();
            success(handler.send("LOGOUT", "{\"userId\":202}"), "LOGOUT_RESPONSE");
            check(handler.identity() == null && ClientManager.getSession(ALICE) == null, "Logout removes account and alert registration");
            check(!handler.session.sendToUser(ALICE, new Message("WEATHER_ALERT", "fixture")),
                    "A notification captured before logout must be rejected after logout");
            check(handler.selectionsEmpty(), "Logout clears selections associated with the old connection session");
            unauthorized(handler.send("SAVE_HISTORY", "{\"userId\":101,\"type\":\"thanh_pho\",\"keyword\":\"Denied\"}"), "Logout invalidates personal access");
            unauthorized(handler.send("LOGOUT", null), "Repeated logout requires authentication");

            login(handler, "alice", ALICE_PASSWORD, ALICE);
            JsonObject failed = handler.send("LOGIN", "{\"username\":\"bob\",\"password\":\"incorrect\",\"id\":202}");
            action(failed, "LOGIN_RESPONSE");
            check(!failed.has("data") || failed.get("data").isJsonNull(), "Failed credentials do not return an authenticated user");
            check(handler.identity() == null && ClientManager.getSession(ALICE) == null, "Failed re-login cannot retain Alice's privileges");
            unauthorized(handler.send("GET_HISTORY", null), "Failed login leaves the handler unauthenticated");

            for (String malformed : new String[]{null, "[1]", "{\"username\":123,\"password\":\"anything\"}", "{\"username\":\"bob\"}"}) {
                login(handler, "alice", ALICE_PASSWORD, ALICE);
                error(handler.send("LOGIN", malformed), "Malformed login request");
                check(handler.identity() == null && ClientManager.getSession(ALICE) == null,
                        "A malformed login attempt must clear the previous authenticated identity");
            }
            login(handler, "alice", ALICE_PASSWORD, ALICE);
            login(handler, "bob", BOB_PASSWORD, BOB);
            check(ClientManager.getSession(ALICE) == null && ClientManager.getSession(BOB) == handler.session,
                    "Successful account switching removes the prior user's registration");
            check(!handler.session.sendToUser(ALICE, new Message("WEATHER_ALERT", "fixture")),
                    "Delayed notifications for the previous account are blocked after re-login");
            JsonObject profile = handler.send("GET_PROFILE", null);
            check(profile.getAsJsonObject("data").get("id").getAsInt() == BOB, "Re-login uses the newly authenticated identity");
        }
    }

    private static void replacementSessionSurvivesTheOldHandlerClosing() throws Exception {
        Store store = new Store();
        try (Harness oldHandler = new Harness(store); Harness newHandler = new Harness(store)) {
            login(oldHandler, "alice", ALICE_PASSWORD, ALICE);
            login(newHandler, "alice", ALICE_PASSWORD, ALICE);
            check(ClientManager.getSession(ALICE) == newHandler.session, "The latest alert registration belongs to the new handler");
            success(oldHandler.send("LOGOUT", null), "LOGOUT_RESPONSE");
            check(ClientManager.getSession(ALICE) == newHandler.session,
                    "Closing an older handler cannot unregister a newer connection for the same account");
            action(newHandler.send("GET_PROFILE", null), "GET_PROFILE_RESPONSE");
            check(newHandler.identity() == ALICE, "The new connection retains its own authentication");
        }
    }

    private static void disconnectClearsIdentityAndPendingCitySelections() throws Exception {
        Store store = new Store();
        try (Harness handler = new Harness(store)) {
            login(handler, "alice", ALICE_PASSWORD, ALICE);
            handler.seedSelections();
            // Unconnected fixture socket/null keys fail the handshake immediately, exercising run()'s real finally block.
            handler.handler.run();
            check(handler.socket.isClosed(), "The disconnected handler closes its socket");
            check(handler.identity() == null && ClientManager.getSession(ALICE) == null,
                    "Connection cleanup removes the authenticated account and its alert registration");
            check(handler.selectionsEmpty(), "Connection cleanup clears pending city selections");
            check(!handler.session.sendToUser(ALICE, new Message("WEATHER_ALERT", "fixture")),
                    "Connection cleanup prevents private notifications from using a stale session reference");
            unauthorized(handler.send("GET_HISTORY", "{\"userId\":101}"), "A cleared handler cannot access personal data");
        }
    }

    private static void login(Harness handler, String username, String password, int expectedId) throws Exception {
        JsonObject data = new JsonObject();
        data.addProperty("username", username);
        data.addProperty("password", password);
        data.addProperty("id", expectedId == ALICE ? BOB : ALICE);
        data.addProperty("userId", expectedId == ALICE ? BOB : ALICE);
        JsonObject response = handler.send("LOGIN", data.toString());
        action(response, "LOGIN_RESPONSE");
        check(response.getAsJsonObject("data").get("id").getAsInt() == expectedId
                        && handler.identity() == expectedId,
                "Only verified credentials establish the server-side identity");
    }

    private static void action(JsonObject response, String expected) {
        check(response.get("action").getAsString().equals(expected), "Expected " + expected + ", got " + response);
    }

    private static void success(JsonObject response, String expected) {
        action(response, expected);
        check(response.get("data").getAsBoolean(), "Expected successful " + expected + ": " + response);
    }

    private static void unauthorized(JsonObject response, String context) {
        action(response, "UNAUTHORIZED");
        check(response.get("data").getAsString().contains("đăng nhập"), context);
    }

    private static void error(JsonObject response, String context) {
        action(response, "ERROR");
        check(response.has("data") && response.get("data").isJsonPrimitive()
                        && !response.get("data").getAsString().isBlank(), "Useful error required: " + context);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Harness implements AutoCloseable {
        final Socket socket = new Socket();
        final ClientHandler handler = new ClientHandler(socket, null);
        final ClientSession session = new ClientSession(null);
        final Method handleRequest;

        Harness(Store store) throws Exception {
            set("session", session);
            set("userDAO", new FakeUserDAO(store));
            set("locationDAO", new FakeLocationDAO(store));
            set("historyDAO", new FakeHistoryDAO(store));
            set("subscriptionDAO", new FakeSubscriptionDAO(store));
            handleRequest = ClientHandler.class.getDeclaredMethod("handleRequest", String.class);
            handleRequest.setAccessible(true);
        }

        JsonObject send(String action, String payload) throws Exception {
            JsonElement data = payload == null ? null : JsonParser.parseString(payload);
            return JsonParser.parseString((String) handleRequest.invoke(handler, GSON.toJson(new Message(action, data)))).getAsJsonObject();
        }

        Integer identity() throws Exception { return (Integer) get("currentUserId"); }

        void seedSelections() throws Exception {
            selectionMap("searchedCities").put(1, new Location(1, "Hà Nội", null, 21, 105, ""));
            selectionMap("comparisonCities").put(2, new Location(2, "Tokyo", null, 35, 139, ""));
        }

        boolean selectionsEmpty() throws Exception {
            return selectionMap("searchedCities").isEmpty() && selectionMap("comparisonCities").isEmpty();
        }

        @SuppressWarnings("unchecked")
        private Map<Integer, Location> selectionMap(String name) throws Exception { return (Map<Integer, Location>) get(name); }

        private Object get(String name) throws Exception {
            Field field = ClientHandler.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(handler);
        }

        private void set(String name, Object value) throws Exception {
            Field field = ClientHandler.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(handler, value);
        }

        @Override public void close() throws Exception {
            send("LOGOUT", null);
            socket.close();
        }
    }

    private record Call(String operation, int userId, int recordId) {}
    private record OwnedLocation(int owner, Location location) {}

    private static final class Store {
        final Map<Integer, User> users = new HashMap<>();
        final Map<Integer, String> passwords = new HashMap<>();
        final Map<Integer, OwnedLocation> locations = new HashMap<>();
        final Map<Integer, List<model.HistoryEntry>> histories = new HashMap<>();
        final Map<Integer, List<String[]>> subscriptions = new HashMap<>();
        final List<Call> calls = new ArrayList<>();
        int nextRecordId = 3000;

        Store() {
            users.put(ALICE, new User(ALICE, "alice", "An", "alice@example.com"));
            users.put(BOB, new User(BOB, "bob", "Bình", "bob@example.com"));
            passwords.put(ALICE, ALICE_PASSWORD);
            passwords.put(BOB, BOB_PASSWORD);
        }

        void called(String operation, int userId) { calls.add(new Call(operation, userId, 0)); }
        Call last() { return calls.get(calls.size() - 1); }
    }

    private static final class SensitiveUser extends User {
        private final String password = "fixture-secret-password";
        private final String passwordHash = "fixture-secret-hash";
        SensitiveUser(User user) { super(user.getId(), user.getUsername(), user.getFullname(), user.getEmail()); }
    }

    private static final class FakeUserDAO extends UserDAO {
        private final Store store;
        FakeUserDAO(Store store) { this.store = store; }

        @Override public User checkLogin(String username, String password) {
            return store.users.values().stream().filter(user -> user.getUsername().equals(username)
                    && store.passwords.get(user.getId()).equals(password)).findFirst().orElse(null);
        }

        @Override public User findById(int id) throws SQLException {
            store.called("GET_PROFILE", id);
            User user = store.users.get(id);
            return user == null ? null : new SensitiveUser(user);
        }

        @Override public boolean updateProfile(int id, String fullname, String email) {
            store.called("UPDATE_PROFILE", id);
            User user = store.users.get(id);
            if (user == null) return false;
            user.setFullname(fullname);
            user.setEmail(email);
            return true;
        }

        @Override public PasswordChangeResult changePassword(int id, String currentPassword, String newPassword) {
            store.called("CHANGE_PASSWORD", id);
            if (!store.passwords.containsKey(id)) return PasswordChangeResult.USER_NOT_FOUND;
            if (!store.passwords.get(id).equals(currentPassword)) return PasswordChangeResult.WRONG_CURRENT_PASSWORD;
            store.passwords.put(id, newPassword);
            return PasswordChangeResult.SUCCESS;
        }
    }

    private static final class FakeLocationDAO extends LocationDAO {
        private final Store store;
        FakeLocationDAO(Store store) { this.store = store; }

        @Override public boolean saveLocation(int userId, String name, double lat, double lng, String note) {
            store.called("SAVE_LOCATION", userId);
            int id = store.nextRecordId++;
            store.locations.put(id, new OwnedLocation(userId, new Location(id, name, null, lat, lng, note)));
            return true;
        }

        @Override public List<Location> getByUserId(int userId) {
            store.called("GET_SAVED_LOCATIONS", userId);
            return store.locations.values().stream().filter(row -> row.owner == userId).map(OwnedLocation::location).toList();
        }

        @Override public boolean deleteById(int id, int userId) {
            store.calls.add(new Call("DELETE_LOCATION", userId, id));
            OwnedLocation row = store.locations.get(id);
            if (row == null || row.owner != userId) return false;
            store.locations.remove(id);
            return true;
        }
    }

    private static final class FakeHistoryDAO extends HistoryDAO {
        private final Store store;
        FakeHistoryDAO(Store store) { this.store = store; }

        @Override public boolean saveHistory(int userId, String queryText, String type) {
            store.called("SAVE_HISTORY", userId);
            store.histories.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(new model.HistoryEntry(queryText, type));
            return true;
        }

        @Override public List<model.HistoryEntry> getHistoryByUserId(int userId) {
            store.called("GET_HISTORY", userId);
            return List.copyOf(store.histories.getOrDefault(userId, List.of()));
        }
    }

    private static final class FakeSubscriptionDAO extends WeatherSubscriptionDAO {
        private final Store store;
        FakeSubscriptionDAO(Store store) { this.store = store; }

        @Override public boolean delete(int id, int userId) {
            store.calls.add(new Call("UNSUBSCRIBE_WEATHER", userId, id));
            return store.subscriptions.getOrDefault(userId, new ArrayList<>())
                    .removeIf(row -> Integer.parseInt(row[0]) == id);
        }

        @Override public boolean subscribe(int userId, String location, double lat, double lng, String condition) {
            store.called("SUBSCRIBE_WEATHER", userId);
            store.subscriptions.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(new String[]{
                    Integer.toString(store.nextRecordId++), Integer.toString(userId), location,
                    Double.toString(lat), Double.toString(lng), condition});
            return true;
        }

        @Override public List<String[]> getByUserId(int userId) {
            store.called("GET_SUBSCRIPTIONS", userId);
            return List.copyOf(store.subscriptions.getOrDefault(userId, List.of()));
        }
    }
}
