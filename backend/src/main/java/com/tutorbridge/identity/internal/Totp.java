package com.tutorbridge.identity.internal;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP of the admin sign-in (TB-13; Đính 23/11/2026 on #13): RFC 6238 over HMAC-SHA1, 6 digits, steps of
 * 30 seconds, a code of the step before or after now is accepted, and a code of a step already used is
 * refused. JDK only, no library. Pure: the caller passes {@code now} from the application clock.
 */
public final class Totp {

    static final int DIGITS = 6;
    static final long STEP_SECONDS = 30;
    /** Steps accepted on each side of now, for clock drift between the server and the phone. */
    static final int DRIFT_STEPS = 1;
    static final String ISSUER = "TutorBridge";
    private static final int MODULO = 1_000_000;
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private Totp() {}

    /** The RFC 6238 time step that holds {@code now}. */
    public static long step(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), STEP_SECONDS);
    }

    /** The code of one step, zero padded to six digits (RFC 4226 dynamic truncation). */
    public static String code(byte[] secret, long step) {
        byte[] hash;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 unavailable", e);
        }
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
        return String.format("%0" + DIGITS + "d", binary % MODULO);
    }

    /**
     * The step whose code this is, among now and one step either side, and only a step after
     * {@code lastUsedStep}; empty otherwise. Every candidate is compared in constant time, so the time
     * taken does not tell which step was close.
     */
    public static OptionalLong verify(byte[] secret, String code, Instant now, Long lastUsedStep) {
        if (code == null || code.length() != DIGITS || !code.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return OptionalLong.empty();
        }
        byte[] given = code.getBytes(StandardCharsets.US_ASCII);
        long current = step(now);
        long matched = -1;
        for (long step = current - DRIFT_STEPS; step <= current + DRIFT_STEPS; step++) {
            boolean same = MessageDigest.isEqual(given, code(secret, step).getBytes(StandardCharsets.US_ASCII));
            boolean unused = lastUsedStep == null || step > lastUsedStep;
            if (same && unused && matched < 0) {
                matched = step;
            }
        }
        return matched < 0 ? OptionalLong.empty() : OptionalLong.of(matched);
    }

    /** RFC 4648 base32 without padding, as authenticator apps read the secret. */
    public static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32[(buffer >> (bits - 5)) & 0x1f]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32[(buffer << (5 - bits)) & 0x1f]);
        }
        return out.toString();
    }

    /**
     * The {@code otpauth://} URI an authenticator app scans. It carries the secret: the admin-setup command
     * prints it once to the operator and nothing ever logs it.
     */
    public static String enrollmentUri(byte[] secret, String email) {
        String label = URLEncoder.encode(ISSUER + ":" + email, StandardCharsets.UTF_8).replace("%3A", ":");
        return "otpauth://totp/" + label + "?secret=" + base32(secret) + "&issuer=" + ISSUER
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }
}
