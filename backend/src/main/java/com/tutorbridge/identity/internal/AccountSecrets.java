package com.tutorbridge.identity.internal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Email digests, email encryption and one-time link codes (Q4, Q11 of TB-8).
 *
 * <p>Two separate keys protect the email: an HMAC-SHA256 key for the unique digest, an AES-GCM key
 * for the ciphertext. A third AES-GCM key protects link codes while they wait in the outbox, so the
 * database never holds a usable code (Huy, 11/11/2026). Ciphertexts start with a key version
 * ({@code v1:}) so a key can be replaced without a migration. A fourth AES-GCM key protects the admin TOTP
 * secrets, so {@code admin_mfa_enrollments.secret_handle} is useless without it (TB-13, Đính 23/11/2026 on
 * #13). Nothing here is ever logged.
 */
@Component
public class AccountSecrets {

    private static final String VERSION = "v1:";
    private static final byte[] VERSION_BYTES = VERSION.getBytes(StandardCharsets.US_ASCII);
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int LINK_TOKEN_BYTES = 32;
    /** 160 bits, the HMAC-SHA1 block RFC 4226 recommends for a TOTP secret. */
    private static final int TOTP_SECRET_BYTES = 20;

    private final SecureRandom random = new SecureRandom();
    private final SecretKey digestKey;
    private final SecretKey emailKey;
    private final SecretKey linkKey;
    private final SecretKey mfaKey;

    public AccountSecrets(IdentityProperties properties) {
        IdentityProperties.Keys keys = properties.keys();
        this.digestKey = key(keys.emailDigest(), "HmacSHA256", "tutorbridge.identity.keys.email-digest");
        this.emailKey = key(keys.emailEncryption(), "AES", "tutorbridge.identity.keys.email-encryption");
        this.linkKey = key(keys.linkToken(), "AES", "tutorbridge.identity.keys.link-token");
        this.mfaKey = key(keys.adminMfa(), "AES", "tutorbridge.identity.keys.admin-mfa");
    }

    private static SecretKey key(String base64, String algorithm, String name) {
        byte[] bytes;
        try {
            bytes = base64 == null ? new byte[0] : Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " is not base64");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException(name + " must be base64 of 32 random bytes");
        }
        return new SecretKeySpec(bytes, algorithm);
    }

    /** Trim, lower case, NFC (Q4 of TB-8). */
    public static String normalizeEmail(String email) {
        return Normalizer.normalize(email.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    }

    /** Keyed digest of a normalized email, as stored in {@code accounts.email_digest}. */
    public String emailDigest(String normalizedEmail) {
        return hmac("email:" + normalizedEmail);
    }

    /** Keyed digest of a client address for the attempt counters; the raw address is never stored. */
    public String sourceDigest(String source) {
        return hmac("source:" + source);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(digestKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /** {@code v1:} + nonce + AES-GCM ciphertext, bound to the account id. */
    public byte[] encryptEmail(UUID accountId, String normalizedEmail) {
        byte[] sealed = seal(emailKey, uuid(accountId), normalizedEmail.getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.allocate(VERSION_BYTES.length + sealed.length).put(VERSION_BYTES).put(sealed).array();
    }

    public String decryptEmail(UUID accountId, byte[] ciphertext) {
        if (ciphertext.length <= VERSION_BYTES.length
                || !Arrays.equals(VERSION_BYTES, Arrays.copyOf(ciphertext, VERSION_BYTES.length))) {
            throw new IllegalArgumentException("unknown email key version");
        }
        byte[] sealed = Arrays.copyOfRange(ciphertext, VERSION_BYTES.length, ciphertext.length);
        return new String(open(emailKey, uuid(accountId), sealed), StandardCharsets.UTF_8);
    }

    /** A new one-time link code: 256 random bits, base64url without padding (43 characters). */
    public String newLinkToken() {
        byte[] bytes = new byte[LINK_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex of a link code, as stored in {@code auth_challenges.token_digest}. */
    public static String linkTokenDigest(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** {@code v1:} + base64 of nonce and AES-GCM ciphertext, bound to the challenge id. */
    public String encryptLinkToken(UUID challengeId, String token) {
        byte[] sealed = seal(linkKey, uuid(challengeId), token.getBytes(StandardCharsets.US_ASCII));
        return VERSION + Base64.getEncoder().encodeToString(sealed);
    }

    /** For the mail sender: only a ciphertext made for this challenge opens. */
    public String decryptLinkToken(UUID challengeId, String ciphertext) {
        if (!ciphertext.startsWith(VERSION)) {
            throw new IllegalArgumentException("unknown link key version");
        }
        byte[] sealed = Base64.getDecoder().decode(ciphertext.substring(VERSION.length()));
        return new String(open(linkKey, uuid(challengeId), sealed), StandardCharsets.US_ASCII);
    }

    /** A new TOTP secret for an admin enrollment. */
    public byte[] newTotpSecret() {
        byte[] secret = new byte[TOTP_SECRET_BYTES];
        random.nextBytes(secret);
        return secret;
    }

    /** {@code v1:} + base64 of nonce and AES-GCM ciphertext, bound to the admin's account id. */
    public String encryptTotpSecret(UUID accountId, byte[] secret) {
        return VERSION + Base64.getEncoder().encodeToString(seal(mfaKey, uuid(accountId), secret));
    }

    /** Only a handle made for this account with this key opens. */
    public byte[] decryptTotpSecret(UUID accountId, String handle) {
        if (!handle.startsWith(VERSION)) {
            throw new IllegalArgumentException("unknown admin MFA key version");
        }
        return open(mfaKey, uuid(accountId), Base64.getDecoder().decode(handle.substring(VERSION.length())));
    }

    private byte[] seal(SecretKey key, byte[] aad, byte[] plain) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] body = cipher.doFinal(plain);
            return ByteBuffer.allocate(nonce.length + body.length).put(nonce).put(body).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM unavailable", e);
        }
    }

    private static byte[] open(SecretKey key, byte[] aad, byte[] sealed) {
        if (sealed.length <= NONCE_BYTES) {
            throw new IllegalArgumentException("ciphertext too short");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, sealed, 0, NONCE_BYTES));
            cipher.updateAAD(aad);
            return cipher.doFinal(sealed, NONCE_BYTES, sealed.length - NONCE_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("ciphertext does not open with this key and binding");
        }
    }

    private static byte[] uuid(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits())
                .array();
    }
}
