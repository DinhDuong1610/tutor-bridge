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

    public String hash(String password) {
        return encoder.encode(normalize(password));
    }

    private static String normalize(String password) {
        return Normalizer.normalize(password, Normalizer.Form.NFKC);
    }
}
