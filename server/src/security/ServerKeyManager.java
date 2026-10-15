package security;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;

public final class ServerKeyManager {

    private ServerKeyManager() {
    }

    public static KeyPair load() throws IOException {

        String configured = System.getProperty("secure.keyDir");
        Path dir = configured != null && !configured.trim().isEmpty()
                ? Paths.get(configured)
                : (Files.isDirectory(Paths.get("server", "security"))
                        ? Paths.get("server", "security")
                        : Paths.get("security"));

        try {
            PublicKey publicKey =
                    SecureChannel.readPublicKey(
                            dir.resolve("server-public.key")
                    );

            PrivateKey privateKey =
                    KeyFactory.getInstance("RSA")
                            .generatePrivate(
                                    new PKCS8EncodedKeySpec(
                                            Files.readAllBytes(
                                                    dir.resolve(
                                                            "server-private.key"
                                                    )
                                            )
                                    )
                            );

            if (
                    !((RSAPublicKey) publicKey)
                            .getModulus()
                            .equals(
                                    ((RSAPrivateKey) privateKey)
                                            .getModulus()
                            )
            ) {
                throw new IOException(
                        "Cặp khóa của máy chủ không khớp."
                );
            }

            return new KeyPair(
                    publicKey,
                    privateKey
            );

        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IOException(
                    "Không tải được khóa RSA của máy chủ.",
                    e
            );
        } catch (java.nio.file.NoSuchFileException e) {
            throw new IOException("Không tìm thấy tệp khóa RSA của máy chủ. Hãy tạo khóa bằng init_keys.bat.", e);
        } catch (java.nio.file.AccessDeniedException e) {
            throw new IOException("Không có quyền đọc tệp khóa RSA của máy chủ.", e);
        }
    }

    // Chạy riêng một lần để tạo cặp khóa RSA.
    public static void main(String[] args) throws Exception {

        Path dir = Paths.get(
                args.length == 0
                        ? "security"
                        : args[0]
        );

        Files.createDirectories(dir);

        Path privateFile =
                dir.resolve("server-private.key");

        Path publicFile =
                dir.resolve("server-public.key");

        if (
                Files.exists(privateFile)
                        || Files.exists(publicFile)
        ) {
            throw new IOException(
                    "Khóa đã tồn tại; không ghi đè khóa hiện có."
            );
        }

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance("RSA");

        generator.initialize(3072);

        KeyPair pair = generator.generateKeyPair();

        try {
            Files.createFile(
                    privateFile,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString(
                                    "rw-------"
                            )
                    )
            );

        } catch (UnsupportedOperationException e) {
            // Windows không hỗ trợ POSIX permissions.
            Files.createFile(privateFile);
        }

        Files.write(
                privateFile,
                pair.getPrivate().getEncoded(),
                StandardOpenOption.WRITE
        );

        Files.write(
                publicFile,
                pair.getPublic().getEncoded(),
                StandardOpenOption.CREATE_NEW
        );

        System.out.println(
                "Đã tạo khóa RSA. Chỉ sao chép server-public.key "
                        + "vào thư mục security của máy khách."
        );

        System.out.println(
                "Dấu vân tay SHA-256 của khóa công khai: "
                        + java.util.HexFormat.of().formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(
                                                pair.getPublic()
                                                        .getEncoded()
                                        )
                        )
        );
    }
}
