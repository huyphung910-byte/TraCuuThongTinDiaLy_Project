package panel;

import com.google.gson.JsonObject;
import model.Message;
import model.User;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Tests the account form without sockets, a database, or visible windows. */
public final class AccountPanelTest {
    private static final int USER_ID = 7;
    private static final String ORIGINAL_NAME = "Người dùng ban đầu";
    private static final String ORIGINAL_EMAIL = "original@example.com";

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        initialProfile();
        saveRequiresAcknowledgement();
        failedSavePreservesProfile();
        profileValidation();
        reloadProfile();
        rejectForeignProfile();
        passwordValidation();
        changePasswordSuccessAndFailure();
        logoutClearsPasswords();
        detachedWorkerCannotUpdateProfile();
        detachedWorkerCannotReportPasswordSuccess();
        System.out.println("ĐẠT: giao diện tài khoản, xác nhận cập nhật, kiểm tra dữ liệu, đổi mật khẩu, đăng xuất và hủy tác vụ khi đóng giao diện");
    }

    private static void initialProfile() throws Exception {
        Fixture fixture = fixture(request -> { throw new AssertionError("No initial request expected"); });
        edt(() -> {
            check(!fixture.panel.usernameField.isEditable(), "Username must be read-only");
            check(fixture.panel.usernameField.getText().equals("tester"), "Initial username");
            check(fixture.panel.fullnameField.getText().equals(ORIGINAL_NAME), "Initial full name");
            check(fixture.panel.emailField.getText().equals(ORIGINAL_EMAIL), "Initial email");
            return null;
        });
        check(fixture.sendCount.get() == 0, "Constructing the panel must not send a request");
    }

    private static void saveRequiresAcknowledgement() throws Exception {
        BlockingReply reply = new BlockingReply(new Message("UPDATE_PROFILE_RESPONSE", true));
        Fixture fixture = fixture(reply::send);
        edt(() -> {
            fixture.panel.fullnameField.setText("  Nguyễn Văn Mới  ");
            fixture.panel.emailField.setText("  updated@example.com  ");
            fixture.panel.saveProfile();
            return null;
        });
        await(reply.entered, "Profile request did not start");
        Message sent = fixture.sent.get(0);
        check(sent.getAction().equals("UPDATE_PROFILE"), "Save action");
        JsonObject data = data(sent);
        noIdentity(data);
        check(data.get("fullname").getAsString().equals("Nguyễn Văn Mới"), "Trim full name");
        check(data.get("email").getAsString().equals("updated@example.com"), "Trim email");
        edt(() -> {
            originalProfile(fixture);
            check(fixture.profileUpdates.get() == 0, "Do not update status bar before acknowledgement");
            check(!fixture.panel.saveButton.isEnabled(), "Prevent concurrent profile requests");
            return null;
        });
        reply.release.countDown();
        awaitIdle(fixture);
        edt(() -> {
            check(fixture.user.getFullname().equals("Nguyễn Văn Mới"), "Update name after acknowledgement");
            check(fixture.user.getEmail().equals("updated@example.com"), "Update email after acknowledgement");
            check(fixture.profileUpdates.get() == 1, "Refresh parent after saving");
            check(fixture.lastDialog().type == JOptionPane.INFORMATION_MESSAGE, "Save success dialog");
            return null;
        });
    }

    private static void failedSavePreservesProfile() throws Exception {
        for (Message response : List.of(new Message("UPDATE_PROFILE_RESPONSE", false),
                new Message("ERROR", "Email đã được sử dụng."),
                new Message("UNEXPECTED_RESPONSE", true))) {
            Fixture fixture = fixture(request -> response);
            edt(() -> {
                fixture.panel.fullnameField.setText("Không được lưu");
                fixture.panel.emailField.setText("not-saved@example.com");
                fixture.panel.saveProfile();
                return null;
            });
            awaitIdle(fixture);
            edt(() -> {
                originalProfile(fixture);
                check(fixture.profileUpdates.get() == 0, "Failed saves must not update the parent");
                check(fixture.lastDialog().type == JOptionPane.ERROR_MESSAGE, "Show a failed-save error");
                return null;
            });
        }
    }

    private static void profileValidation() throws Exception {
        for (String invalidName : List.of("   ", "x".repeat(101), "A\tB", "\uD83D\uDE00".repeat(101))) {
            Fixture fixture = fixture(request -> { throw new AssertionError("Invalid name must not be sent"); });
            edt(() -> {
                fixture.panel.fullnameField.setText(invalidName);
                fixture.panel.saveProfile();
                noRequestWarning(fixture);
                return null;
            });
        }
        for (String invalidEmail : List.of("missing-at.example.com", "x@localhost", ".x@example.com",
                "x.@example.com", "x..y@example.com", "x@-example.com", "x@example-.com",
                "x@exa_mple.com", "người@example.com", "x".repeat(89) + "@example.com")) {
            Fixture fixture = fixture(request -> { throw new AssertionError("Invalid email must not be sent"); });
            edt(() -> {
                fixture.panel.emailField.setText(invalidEmail);
                fixture.panel.saveProfile();
                noRequestWarning(fixture);
                return null;
            });
        }
        String unicodeName = "\uD83D\uDE00".repeat(100);
        Fixture fixture = fixture(request -> new Message("UPDATE_PROFILE_RESPONSE", true));
        edt(() -> {
            fixture.panel.fullnameField.setText(unicodeName);
            fixture.panel.saveProfile();
            return null;
        });
        awaitIdle(fixture);
        check(fixture.sendCount.get() == 1, "Full-name limits count Unicode code points");
        edt(() -> {
            check(fixture.user.getFullname().equals(unicodeName), "Accept a 100-code-point name");
            return null;
        });
    }

    private static void reloadProfile() throws Exception {
        User serverUser = new User(USER_ID, "fresh-login", "Tên trên máy chủ", "fresh@example.com");
        Fixture fixture = fixture(request -> new Message("GET_PROFILE_RESPONSE", serverUser));
        edt(() -> { fixture.panel.reloadProfile(); return null; });
        awaitIdle(fixture);
        check(fixture.sent.get(0).getAction().equals("GET_PROFILE"), "Reload action");
        noIdentity(data(fixture.sent.get(0)));
        check(data(fixture.sent.get(0)).size() == 0, "Reload does not send client account identifiers");
        edt(() -> {
            check(fixture.user.getUsername().equals("fresh-login"), "Reload username");
            check(fixture.user.getFullname().equals("Tên trên máy chủ"), "Reload name");
            check(fixture.user.getEmail().equals("fresh@example.com"), "Reload email");
            check(fixture.panel.fullnameField.getText().equals("Tên trên máy chủ"), "Reload fields");
            check(fixture.profileUpdates.get() == 1, "Reload refreshes the parent");
            check(fixture.dialogs.isEmpty(), "Successful reload does not need a dialog");
            return null;
        });
    }

    private static void rejectForeignProfile() throws Exception {
        for (User invalid : List.of(new User(USER_ID + 1, "foreign", "Tên khác", "foreign@example.com"),
                new User(USER_ID, "", "Tên khác", "foreign@example.com"))) {
            Fixture fixture = fixture(request -> new Message("GET_PROFILE_RESPONSE", invalid));
            edt(() -> { fixture.panel.reloadProfile(); return null; });
            awaitIdle(fixture);
            edt(() -> {
                originalProfile(fixture);
                check(fixture.user.getUsername().equals("tester"), "Reject invalid reload usernames");
                check(fixture.profileUpdates.get() == 0, "Reject foreign-account model updates");
                check(fixture.lastDialog().type == JOptionPane.ERROR_MESSAGE, "Invalid reload must show an error");
                return null;
            });
        }
    }

    private static void passwordValidation() throws Exception {
        String[][] invalid = {
                {"", "new-password", "new-password"},
                {"   ", "new-password", "new-password"},
                {"old-password", "short", "short"},
                {"old-password", " ".repeat(8), " ".repeat(8)},
                {"old-password", "x".repeat(129), "x".repeat(129)},
                {"old-password", "old-password", "old-password"},
                {"old-password", "new-password", "wrong-confirmation"},
                {"old-password", "\uD83D\uDE00".repeat(7), "\uD83D\uDE00".repeat(7)}
        };
        for (String[] values : invalid) {
            Fixture fixture = fixture(request -> { throw new AssertionError("Invalid password must not be sent"); });
            edt(() -> {
                passwords(fixture, values[0], values[1], values[2]);
                fixture.panel.changePassword();
                noRequestWarning(fixture);
                return null;
            });
        }
    }

    private static void changePasswordSuccessAndFailure() throws Exception {
        String newPassword = "\uD83D\uDE00".repeat(8);
        for (Message response : List.of(new Message("CHANGE_PASSWORD_RESPONSE", true),
                new Message("CHANGE_PASSWORD_RESPONSE", false),
                new Message("ERROR", "Mật khẩu hiện tại không đúng."))) {
            Fixture fixture = fixture(request -> response);
            edt(() -> {
                passwords(fixture, "old-password", newPassword, newPassword);
                fixture.panel.changePassword();
                return null;
            });
            awaitIdle(fixture);
            check(fixture.sendCount.get() == 1, "Eight Unicode code points form a valid new password");
            Message sent = fixture.sent.get(0);
            check(sent.getAction().equals("CHANGE_PASSWORD"), "Password action");
            JsonObject snapshot = data(sent);
            noIdentity(snapshot);
            check(snapshot.get("currentPassword").getAsString().equals("old-password"), "Send current password for verification");
            check(snapshot.get("newPassword").getAsString().equals(newPassword), "Send the requested new password");
            check(!fixture.rawData.get(0).has("currentPassword") && !fixture.rawData.get(0).has("newPassword"),
                    "Remove passwords from completed request data");
            edt(() -> {
                originalProfile(fixture);
                check(fixture.profileUpdates.get() == 0, "Password changes do not alter profile fields");
                if (Boolean.TRUE.equals(response.getData())) {
                    passwordsEmpty(fixture);
                    check(fixture.lastDialog().type == JOptionPane.INFORMATION_MESSAGE, "Password success dialog");
                } else {
                    check(fixture.panel.currentPasswordField.getPassword().length > 0, "Allow correcting the current password after an error");
                    check(fixture.lastDialog().type == JOptionPane.ERROR_MESSAGE, "Password failure dialog");
                }
                return null;
            });
        }
    }

    private static void logoutClearsPasswords() throws Exception {
        Fixture fixture = fixture(request -> { throw new AssertionError("Logout callback owns connection cleanup"); });
        edt(() -> {
            passwords(fixture, "old-password", "new-password", "new-password");
            fixture.panel.logout();
            fixture.panel.logout();
            passwordsEmpty(fixture);
            check(fixture.logouts.get() == 1, "Run logout callback once");
            check(!fixture.panel.logoutButton.isEnabled(), "Disable account form while logging out");
            check(fixture.sendCount.get() == 0, "Panel delegates session logout to its owner");
            return null;
        });
    }

    private static void detachedWorkerCannotUpdateProfile() throws Exception {
        BlockingReply reply = new BlockingReply(new Message("UPDATE_PROFILE_RESPONSE", true));
        Fixture fixture = fixture(reply::send);
        edt(() -> {
            fixture.panel.fullnameField.setText("Tên không được áp dụng sau khi đóng");
            fixture.panel.saveProfile();
            return null;
        });
        await(reply.entered, "Detached profile request did not start");
        edt(() -> {
            passwords(fixture, "old-password", "new-password", "new-password");
            fixture.panel.removeNotify();
            passwordsEmpty(fixture);
            return null;
        });
        reply.release.countDown();
        await(reply.exited, "Detached profile request did not finish");
        flushPendingCompletion();
        edt(() -> {
            originalProfile(fixture);
            check(fixture.profileUpdates.get() == 0, "Do not mutate the old account after detaching");
            check(fixture.dialogs.isEmpty(), "Do not show stale profile dialogs after detaching");
            return null;
        });
    }

    private static void detachedWorkerCannotReportPasswordSuccess() throws Exception {
        BlockingReply reply = new BlockingReply(new Message("CHANGE_PASSWORD_RESPONSE", true));
        Fixture fixture = fixture(reply::send);
        edt(() -> {
            passwords(fixture, "old-password", "new-password", "new-password");
            fixture.panel.changePassword();
            return null;
        });
        await(reply.entered, "Detached password request did not start");
        edt(() -> { fixture.panel.removeNotify(); passwordsEmpty(fixture); return null; });
        reply.release.countDown();
        await(reply.exited, "Detached password request did not finish");
        flushPendingCompletion();
        check(!fixture.rawData.get(0).has("currentPassword") && !fixture.rawData.get(0).has("newPassword"),
                "Scrub password data even when a worker is canceled");
        edt(() -> {
            passwordsEmpty(fixture);
            check(fixture.dialogs.isEmpty(), "Do not show stale password dialogs after detaching");
            return null;
        });
    }

    private static Fixture fixture(Responder responder) throws Exception {
        return edt(() -> new Fixture(responder));
    }

    private static void originalProfile(Fixture fixture) {
        check(fixture.user.getId() == USER_ID, "Never replace the account ID");
        check(fixture.user.getFullname().equals(ORIGINAL_NAME), "Preserve original name");
        check(fixture.user.getEmail().equals(ORIGINAL_EMAIL), "Preserve original email");
    }

    private static void noRequestWarning(Fixture fixture) {
        check(fixture.sendCount.get() == 0, "Validation must happen before sending");
        check(fixture.lastDialog().type == JOptionPane.WARNING_MESSAGE, "Invalid input must show a warning");
        check(fixture.panel.saveButton.isEnabled(), "Invalid input must not leave the form busy");
    }

    private static void passwords(Fixture fixture, String current, String next, String confirmation) {
        fixture.panel.currentPasswordField.setText(current);
        fixture.panel.newPasswordField.setText(next);
        fixture.panel.confirmPasswordField.setText(confirmation);
    }

    private static void passwordsEmpty(Fixture fixture) {
        check(fixture.panel.currentPasswordField.getPassword().length == 0, "Clear current password field");
        check(fixture.panel.newPasswordField.getPassword().length == 0, "Clear new password field");
        check(fixture.panel.confirmPasswordField.getPassword().length == 0, "Clear confirmation field");
    }

    private static JsonObject data(Message request) { return (JsonObject) request.getData(); }

    private static void noIdentity(JsonObject data) {
        check(!data.has("id") && !data.has("userId"), "Account identity belongs to the server session");
    }

    private static void awaitIdle(Fixture fixture) throws Exception {
        waitUntil(() -> {
            try { return edt(() -> fixture.panel.saveButton.isEnabled()); }
            catch (Exception e) { throw new AssertionError(e); }
        }, "Account operation did not finish");
        check(fixture.sendCount.get() == 1, "Exactly one request expected");
    }

    private static void waitUntil(BooleanSupplier ready, String failure) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!ready.getAsBoolean()) {
            if (System.nanoTime() >= deadline) throw new AssertionError(failure);
            Thread.sleep(10);
        }
    }

    private static void await(CountDownLatch latch, String failure) throws InterruptedException {
        check(latch.await(5, TimeUnit.SECONDS), failure);
    }

    private static void flushPendingCompletion() throws Exception {
        // SwingWorker batches its done() callbacks on a short Swing timer.
        CountDownLatch drained = new CountDownLatch(1);
        edt(() -> {
            javax.swing.Timer timer = new javax.swing.Timer(120, event -> drained.countDown());
            timer.setRepeats(false);
            timer.start();
            return null;
        });
        await(drained, "EDT completion did not drain");
        edt(() -> null);
    }

    private static <T> T edt(Callable<T> task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return task.call();
        FutureTask<T> future = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(future);
        return future.get();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface private interface Responder { Message send(Message request) throws Exception; }

    private record Dialog(String message, int type) { }

    private static final class Fixture {
        final User user = new User(USER_ID, "tester", ORIGINAL_NAME, ORIGINAL_EMAIL);
        final AtomicInteger profileUpdates = new AtomicInteger();
        final AtomicInteger logouts = new AtomicInteger();
        final AtomicInteger sendCount = new AtomicInteger();
        final List<Message> sent = Collections.synchronizedList(new ArrayList<>());
        final List<JsonObject> rawData = Collections.synchronizedList(new ArrayList<>());
        final List<Dialog> dialogs = new ArrayList<>();
        final AccountPanel panel;

        Fixture(Responder responder) {
            check(SwingUtilities.isEventDispatchThread(), "Construct account panels on EDT");
            panel = new AccountPanel(user, request -> {
                check(!SwingUtilities.isEventDispatchThread(), "Network requests must not block EDT");
                sendCount.incrementAndGet();
                JsonObject requestData = data(request);
                sent.add(new Message(request.getAction(), requestData.deepCopy()));
                rawData.add(requestData);
                return responder.send(request);
            }, () -> {
                check(SwingUtilities.isEventDispatchThread(), "Profile callback runs on EDT");
                profileUpdates.incrementAndGet();
            }, () -> {
                check(SwingUtilities.isEventDispatchThread(), "Logout callback runs on EDT");
                logouts.incrementAndGet();
            }, (parent, message, title, type) -> {
                check(SwingUtilities.isEventDispatchThread(), "Dialogs run on EDT");
                dialogs.add(new Dialog(message, type));
            });
        }

        Dialog lastDialog() {
            check(!dialogs.isEmpty(), "Expected an account dialog");
            return dialogs.get(dialogs.size() - 1);
        }
    }

    private static final class BlockingReply {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch exited = new CountDownLatch(1);
        private final Message response;

        BlockingReply(Message response) { this.response = response; }

        Message send(Message ignored) throws IOException {
            entered.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            try {
                while (release.getCount() != 0) {
                    try {
                        if (release.await(100, TimeUnit.MILLISECONDS)) break;
                    } catch (InterruptedException canceled) {
                        // Simulate a transport that still returns after the panel is closed.
                    }
                    if (System.nanoTime() >= deadline) throw new IOException("Test response was not released");
                }
                return response;
            } finally { exited.countDown(); }
        }
    }
}
