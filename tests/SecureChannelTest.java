import security.SecureChannel;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.concurrent.*;
import javax.crypto.SecretKey;

/** Loopback integration checks; no database or external server required. */
public final class SecureChannelTest {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        try {
            byte[] previousKey = null;
            for (int i = 0; i < 2; i++) {
                try (ServerSocket listener = new ServerSocket(0)) {
                    Future<SecureChannel> accepted = EXECUTOR.submit(() ->
                            SecureChannel.accept(listener.accept(), keys));
                    try (Socket socket = new Socket("localhost", listener.getLocalPort());
                         SecureChannel client = SecureChannel.connect(socket, keys.getPublic());
                         SecureChannel server = accepted.get(15, TimeUnit.SECONDS)) {
                        Field keyField = SecureChannel.class.getDeclaredField("sendKey");
                        keyField.setAccessible(true);
                        byte[] key = ((SecretKey) keyField.get(client)).getEncoded();
                        check(!Arrays.equals(previousKey, key), "Session keys reused");
                        previousKey = key;
                        for (int n = 0; n < 5; n++) {
                            String json = "{\"action\":\"SEARCH\",\"data\":\"Thành phố Hồ Chí Minh " + n + "\"}";
                            client.send(json);
                            check(json.equals(server.receive()), "Client JSON changed");
                            server.send(json);
                            check(json.equals(client.receive()), "Server JSON changed");
                        }
                        // Inject a frame at the expected sequence with an invalid GCM tag.
                        DataOutputStream raw = new DataOutputStream(socket.getOutputStream());
                        raw.writeInt(24);
                        raw.writeLong(6); // CLIENT_READY plus five application messages.
                        raw.write(new byte[16]);
                        raw.flush();
                        try {
                            server.receive();
                            throw new AssertionError("Tampered frame accepted");
                        } catch (IOException expected) {
                            check(expected.getCause() instanceof javax.crypto.AEADBadTagException,
                                    "Frame did not reach GCM authentication");
                        }
                    }
                }
            }
            try (ServerSocket listener = new ServerSocket(0)) {
                Future<Boolean> rejected = EXECUTOR.submit(() -> {
                    try (SecureChannel channel = SecureChannel.accept(listener.accept(), keys)) {
                        return false;
                    } catch (IOException expected) {
                        return true;
                    }
                });
                try (Socket socket = new Socket("localhost", listener.getLocalPort())) {
                    try {
                        SecureChannel.connect(socket, generator.generateKeyPair().getPublic());
                        throw new AssertionError("Untrusted public key accepted");
                    } catch (IOException expected) {
                        check(expected.getMessage().contains("khóa tin cậy"), "Wrong rejection reason");
                    }
                }
                check(rejected.get(15, TimeUnit.SECONDS), "Server accepted failed handshake");
            }
            System.out.println("ĐẠT: JSON mã hóa hai chiều, khóa phiên riêng, từ chối dữ liệu bị sửa, xác minh khóa tin cậy");
        } finally {
            EXECUTOR.shutdownNow();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
