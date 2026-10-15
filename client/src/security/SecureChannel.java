package security;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

public final class SecureChannel implements AutoCloseable {

    private static final String PROTOCOL = "GEOSEC1";
    private static final int MAX_FRAME = 1_048_576;
    private static final int HANDSHAKE_TIMEOUT = 10_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final OAEPParameterSpec OAEP =
            new OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT
            );

    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;

    private final SecretKeySpec sendKey;
    private final SecretKeySpec receiveKey;

    private final byte sendDirection;
    private final byte receiveDirection;

    private long sendSequence;
    private long receiveSequence;

    private SecureChannel(
            Socket socket,
            DataInputStream input,
            DataOutputStream output,
            byte[] material,
            boolean client
    ) {
        this.socket = socket;
        this.input = input;
        this.output = output;

        byte[] c2s = Arrays.copyOfRange(material, 0, 32);
        byte[] s2c = Arrays.copyOfRange(material, 32, 64);

        sendKey = new SecretKeySpec(
                client ? c2s : s2c,
                "AES"
        );

        receiveKey = new SecretKeySpec(
                client ? s2c : c2s,
                "AES"
        );

        sendDirection = (byte) (client ? 1 : 2);
        receiveDirection = (byte) (client ? 2 : 1);

        Arrays.fill(c2s, (byte) 0);
        Arrays.fill(s2c, (byte) 0);
    }

    // Client thực hiện handshake với Server.
    public static SecureChannel connect(
            Socket socket,
            PublicKey pinnedKey
    ) throws IOException {

        byte[] material = new byte[96];

        try {
            validateRsa(pinnedKey);

            socket.setSoTimeout(HANDSHAKE_TIMEOUT);

            DataInputStream in =
                    new DataInputStream(socket.getInputStream());

            DataOutputStream out =
                    new DataOutputStream(socket.getOutputStream());

            byte[] hello = readFrame(in);

            if (hello == null || hello.length > 8192) {
                throw new IOException("Thông tin bắt tay của máy chủ không hợp lệ.");
            }

            DataInputStream h =
                    new DataInputStream(
                            new ByteArrayInputStream(hello)
                    );

            if (!PROTOCOL.equals(h.readUTF())) {
                throw new IOException(
                        "Giao thức bảo mật không được hỗ trợ."
                );
            }

            int keyLength = h.readInt();

            if (keyLength < 256 || keyLength > 2048) {
                throw new IOException(
                        "Độ dài khóa công khai không hợp lệ."
                );
            }

            byte[] encodedKey = new byte[keyLength];
            h.readFully(encodedKey);

            byte[] challenge = new byte[32];
            h.readFully(challenge);

            if (h.available() != 0) {
                throw new IOException(
                        "Các trường bắt tay của máy chủ không hợp lệ."
                );
            }

            // Public key nhận từ Server phải trùng key được cấp trước.
            if (!MessageDigest.isEqual(
                    pinnedKey.getEncoded(),
                    encodedKey
            )) {
                throw new IOException(
                        "Khóa công khai của máy chủ không khớp với khóa tin cậy."
                );
            }

            // 32 byte: AES Client -> Server.
            // 32 byte: AES Server -> Client.
            // 32 byte: challenge của Server.
            RANDOM.nextBytes(material);

            System.arraycopy(
                    challenge,
                    0,
                    material,
                    64,
                    32
            );

            Cipher rsa =
                    Cipher.getInstance(
                            "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
                    );

            rsa.init(
                    Cipher.ENCRYPT_MODE,
                    pinnedKey,
                    OAEP
            );

            writeFrame(out, rsa.doFinal(material));

            SecureChannel channel =
                    new SecureChannel(
                            socket,
                            in,
                            out,
                            material,
                            true
                    );

            if (!"SERVER_READY".equals(channel.receive())) {
                throw new IOException(
                        "Xác nhận khóa của máy chủ thất bại."
                );
            }

            channel.send("CLIENT_READY");

            socket.setSoTimeout(0);

            return channel;

        } catch (GeneralSecurityException | RuntimeException e) {
            closeQuietly(socket);

            throw new IOException(
                    "Trao đổi khóa bảo mật thất bại.",
                    e
            );

        } catch (IOException e) {
            closeQuietly(socket);
            throw e;

        } finally {
            Arrays.fill(material, (byte) 0);
        }
    }

    // Server thực hiện handshake với Client.
    public static SecureChannel accept(
            Socket socket,
            KeyPair serverKeys
    ) throws IOException {

        byte[] material = null;

        try {
            validateRsa(serverKeys.getPublic());

            socket.setSoTimeout(HANDSHAKE_TIMEOUT);

            DataInputStream in =
                    new DataInputStream(socket.getInputStream());

            DataOutputStream out =
                    new DataOutputStream(socket.getOutputStream());

            byte[] challenge = new byte[32];
            RANDOM.nextBytes(challenge);

            byte[] publicKey =
                    serverKeys.getPublic().getEncoded();

            ByteArrayOutputStream buffer =
                    new ByteArrayOutputStream();

            DataOutputStream hello =
                    new DataOutputStream(buffer);

            hello.writeUTF(PROTOCOL);
            hello.writeInt(publicKey.length);
            hello.write(publicKey);
            hello.write(challenge);

            writeFrame(out, buffer.toByteArray());

            byte[] wrapped = readFrame(in);

            int rsaBytes =
                    (
                            ((RSAPublicKey) serverKeys.getPublic())
                                    .getModulus()
                                    .bitLength()
                                    + 7
                    ) / 8;

            if (wrapped == null || wrapped.length != rsaBytes) {
                throw new IOException(
                        "Khóa phiên được mã hóa không hợp lệ."
                );
            }

            Cipher rsa =
                    Cipher.getInstance(
                            "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
                    );

            rsa.init(
                    Cipher.DECRYPT_MODE,
                    serverKeys.getPrivate(),
                    OAEP
            );

            material = rsa.doFinal(wrapped);

            if (
                    material.length != 96
                            || !MessageDigest.isEqual(
                                    challenge,
                                    Arrays.copyOfRange(
                                            material,
                                            64,
                                            96
                                    )
                            )
            ) {
                throw new IOException(
                        "Khóa phiên hoặc mã thử thách không hợp lệ."
                );
            }

            SecureChannel channel =
                    new SecureChannel(
                            socket,
                            in,
                            out,
                            material,
                            false
                    );

            channel.send("SERVER_READY");

            if (!"CLIENT_READY".equals(channel.receive())) {
                throw new IOException(
                        "Xác nhận khóa của máy khách thất bại."
                );
            }

            socket.setSoTimeout(0);

            return channel;

        } catch (GeneralSecurityException | RuntimeException e) {
            closeQuietly(socket);

            throw new IOException(
                    "Trao đổi khóa bảo mật thất bại.",
                    e
            );

        } catch (IOException e) {
            closeQuietly(socket);
            throw e;

        } finally {
            if (material != null) {
                Arrays.fill(material, (byte) 0);
            }
        }
    }

    public static PublicKey readPublicKey(
            java.nio.file.Path path
    ) throws IOException {

        try {
            byte[] bytes =
                    java.nio.file.Files.readAllBytes(path);

            PublicKey key =
                    KeyFactory.getInstance("RSA")
                            .generatePublic(
                                    new X509EncodedKeySpec(bytes)
                            );

            validateRsa(key);

            return key;

        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IOException(
                    "Khóa công khai tin cậy của máy chủ không hợp lệ.",
                    e
            );
        }
    }

    private static void validateRsa(
            PublicKey key
    ) throws GeneralSecurityException {

        if (
                !(key instanceof RSAPublicKey)
                        || ((RSAPublicKey) key)
                                .getModulus()
                                .bitLength() < 2048
        ) {
            throw new GeneralSecurityException(
                    "Khóa RSA phải có độ dài ít nhất 2048 bit."
            );
        }
    }

    // Đồng bộ toàn bộ quá trình mã hóa và ghi frame.
    // Response và PUSH đều phải đi qua phương thức này.
    public synchronized void send(
            String text
    ) throws IOException {

        try {
            if (sendSequence == Long.MAX_VALUE) {
                throw new IOException(
                        "Đã hết số thứ tự thông điệp trong phiên."
                );
            }

            byte[] plain =
                    text.getBytes(StandardCharsets.UTF_8);

            if (plain.length > MAX_FRAME - 24) {
                throw new IOException("Thông điệp vượt quá kích thước cho phép.");
            }

            Cipher aes =
                    Cipher.getInstance(
                            "AES/GCM/NoPadding"
                    );

            aes.init(
                    Cipher.ENCRYPT_MODE,
                    sendKey,
                    new GCMParameterSpec(
                            128,
                            nonce(sendSequence)
                    )
            );

            aes.updateAAD(
                    aad(sendDirection, sendSequence)
            );

            byte[] encrypted = aes.doFinal(plain);

            byte[] payload =
                    ByteBuffer.allocate(
                                    8 + encrypted.length
                            )
                            .putLong(sendSequence)
                            .put(encrypted)
                            .array();

            writeFrame(output, payload);

            sendSequence++;

        } catch (GeneralSecurityException | RuntimeException e) {
            closeQuietly(socket);

            throw new IOException(
                    "Không mã hóa được thông điệp.",
                    e
            );

        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }
    }

    // Mỗi SecureChannel chỉ có một luồng đọc.
    public String receive() throws IOException {

        try {
            byte[] frame = readFrame(input);

            if (frame == null) {
                return null;
            }

            if (
                    frame.length < 24
                            || receiveSequence == Long.MAX_VALUE
            ) {
                throw new IOException(
                        "Khung dữ liệu mã hóa không hợp lệ."
                );
            }

            long seq =
                    ByteBuffer.wrap(frame).getLong();

            if (seq != receiveSequence) {
                throw new IOException(
                        "Số thứ tự thông điệp không đúng."
                );
            }

            Cipher aes =
                    Cipher.getInstance(
                            "AES/GCM/NoPadding"
                    );

            aes.init(
                    Cipher.DECRYPT_MODE,
                    receiveKey,
                    new GCMParameterSpec(
                            128,
                            nonce(seq)
                    )
            );

            aes.updateAAD(
                    aad(receiveDirection, seq)
            );

            byte[] plain =
                    aes.doFinal(
                            frame,
                            8,
                            frame.length - 8
                    );

            receiveSequence++;

            return new String(
                    plain,
                    StandardCharsets.UTF_8
            );

        } catch (GeneralSecurityException | RuntimeException e) {
            closeQuietly(socket);

            throw new IOException(
                    "Xác thực thông điệp mã hóa thất bại.",
                    e
            );

        } catch (IOException e) {
            closeQuietly(socket);
            throw e;
        }
    }

    private static byte[] nonce(long seq) {
        return ByteBuffer.allocate(12)
                .putInt(0)
                .putLong(seq)
                .array();
    }

    private static byte[] aad(
            byte direction,
            long seq
    ) {
        return ByteBuffer.allocate(16)
                .put(
                        PROTOCOL.getBytes(
                                StandardCharsets.US_ASCII
                        )
                )
                .put(direction)
                .putLong(seq)
                .array();
    }

    private static void writeFrame(
            DataOutputStream out,
            byte[] payload
    ) throws IOException {

        if (
                payload.length < 1
                        || payload.length > MAX_FRAME
        ) {
            throw new IOException("Độ dài khung dữ liệu không hợp lệ.");
        }

        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    private static byte[] readFrame(
            DataInputStream in
    ) throws IOException {

        int first = in.read();

        if (first == -1) {
            return null;
        }

        int length =
                (first << 24)
                        | (in.readUnsignedByte() << 16)
                        | (in.readUnsignedByte() << 8)
                        | in.readUnsignedByte();

        if (length < 1 || length > MAX_FRAME) {
            throw new IOException("Độ dài khung dữ liệu không hợp lệ.");
        }

        byte[] payload = new byte[length];

        in.readFully(payload);

        return payload;
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
