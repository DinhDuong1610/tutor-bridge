package com.tutorbridge.identity.internal;

import java.text.Normalizer;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id per ADR-0008: NFKC before hashing, PHC string output, policy name stored beside it.
 * Hashing is slow on purpose, so callers run it outside a database transaction.
 */
@Component
public class PasswordHasher {

    /** {@code m=19456} KiB, {@code t=2}, {@code p=1}, salt 16 bytes, hash 32 bytes (ADR-0008). */
    public static final String POLICY = "argon2id-v1";

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);

    /** Compared against when the email has no account, so the reply takes as long (ADR-0008). */
    private final String dummyHash = encoder.encode("tutorbridge-dummy-password-for-timing");

    /** UC01: 15–128 characters, counted in code points as JSON Schema does. */
    public static boolean acceptableLength(String password) {
        int length = password.codePointCount(0, password.length());
        return length >= 15 && length <= 128;
    }

    public String hash(String password) {
        return encoder.encode(normalize(password));
    }

    /** False for a null hash too, after the same work as a real comparison. */
    public boolean matches(String password, String passwordHash) {
        if (passwordHash == null) {
            encoder.matches(normalize(password), dummyHash);
            return false;
        }
        return encoder.matches(normalize(password), passwordHash);
    }

    private static String normalize(String password) {
        return Normalizer.normalize(password, Normalizer.Form.NFKC);
    }
}
