package util;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUtil {

    private static final int ITERATIONS = 310_000;
    private static final int MIN_ITERATIONS = 10_000;
    private static final int MAX_ITERATIONS = 1_000_000;

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private PasswordUtil() {
    }

    public static String hashPassword(String password) {

        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException(
                    "Mật khẩu không được để trống."
            );
        }

        byte[] salt = new byte[SALT_LENGTH];

        RANDOM.nextBytes(salt);

        byte[] hash =
                pbkdf2(
                        password,
                        salt,
                        ITERATIONS
                );

        return ITERATIONS + ":"
                + Base64.getEncoder()
                        .encodeToString(salt)
                + ":"
                + Base64.getEncoder()
                        .encodeToString(hash);
    }

    public static boolean verifyPassword(
            String password,
            String storedHash
    ) {
        if (
                password == null
                        || password.isEmpty()
                        || storedHash == null
                        || storedHash.length() > 255
        ) {
            return false;
        }

        try {
            String[] parts =
                    storedHash.split(":", -1);

            if (parts.length != 3) {
                return false;
            }

            int iterations =
                    Integer.parseInt(parts[0]);

            if (
                    iterations < MIN_ITERATIONS
                            || iterations > MAX_ITERATIONS
            ) {
                return false;
            }

            byte[] salt =
                    Base64.getDecoder()
                            .decode(parts[1]);

            byte[] expectedHash =
                    Base64.getDecoder()
                            .decode(parts[2]);

            if (
                    salt.length != SALT_LENGTH
                            || expectedHash.length != HASH_LENGTH
            ) {
                return false;
            }

            byte[] actualHash =
                    pbkdf2(
                            password,
                            salt,
                            iterations
                    );

            return MessageDigest.isEqual(
                    expectedHash,
                    actualHash
            );

        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(
            String password,
            byte[] salt,
            int iterations
    ) {
        char[] passwordChars =
                password.toCharArray();

        PBEKeySpec spec =
                new PBEKeySpec(
                        passwordChars,
                        salt,
                        iterations,
                        HASH_LENGTH * 8
                );

        Arrays.fill(passwordChars, '\0');

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance(
                            "PBKDF2WithHmacSHA256"
                    );

            return factory.generateSecret(spec)
                    .getEncoded();

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Không tạo được mã băm mật khẩu.",
                    e
            );

        } finally {
            spec.clearPassword();
        }
    }
}
