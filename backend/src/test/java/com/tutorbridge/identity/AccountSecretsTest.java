package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tutorbridge.identity.internal.AccountSecrets;
import com.tutorbridge.identity.internal.IdentityProperties;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Email normalization, digests and the AES-GCM bindings, without Spring or a database. */
class AccountSecretsTest {

    private final AccountSecrets secrets = secretsWith(key(), key(), key());

    @Test
    void emails_are_trimmed_lower_cased_and_nfc_normalized_before_the_digest() {
        String composed = "lan.nguyễn@example.test"; // ễ as one code point
        String decomposed = "  LAN.NGUYỄN@Example.Test "; // E + circumflex + tilde

        assertThat(AccountSecrets.normalizeEmail(decomposed)).isEqualTo(composed);
        assertThat(secrets.emailDigest(AccountSecrets.normalizeEmail(decomposed)))
                .isEqualTo(secrets.emailDigest(composed))
                .matches("[0-9a-f]{64}");
    }

    @Test
    void digests_depend_on_the_key_and_keep_emails_apart_from_sources() {
        AccountSecrets other = secretsWith(key(), key(), key());
        assertThat(other.emailDigest("a@example.test")).isNotEqualTo(secrets.emailDigest("a@example.test"));
        assertThat(secrets.sourceDigest("a@example.test")).isNotEqualTo(secrets.emailDigest("a@example.test"));
    }

    @Test
    void an_email_ciphertext_opens_only_for_its_own_account() {
        UUID account = UUID.randomUUID();
        byte[] ciphertext = secrets.encryptEmail(account, "a@example.test");

        assertThat(new String(ciphertext, 0, 3, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("v1:");
        assertThat(secrets.decryptEmail(account, ciphertext)).isEqualTo("a@example.test");
        assertThatThrownBy(() -> secrets.decryptEmail(UUID.randomUUID(), ciphertext))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_link_code_ciphertext_opens_only_for_its_own_challenge_and_key() {
        UUID challenge = UUID.randomUUID();
        String token = secrets.newLinkToken();
        String ciphertext = secrets.encryptLinkToken(challenge, token);

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(ciphertext).startsWith("v1:").doesNotContain(token);
        assertThat(secrets.decryptLinkToken(challenge, ciphertext)).isEqualTo(token);
        assertThatThrownBy(() -> secrets.decryptLinkToken(UUID.randomUUID(), ciphertext))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> secretsWith(key(), key(), key()).decryptLinkToken(challenge, ciphertext))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void the_app_refuses_to_start_with_a_missing_or_short_key() {
        assertThatThrownBy(() -> secretsWith(null, key(), key()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("email-digest");
        String sixteenBytes = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> secretsWith(key(), key(), sixteenBytes))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("link-token");
    }

    private static String key() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static AccountSecrets secretsWith(String digest, String email, String link) {
        return new AccountSecrets(properties(digest, email, link));
    }

    private static IdentityProperties properties(String digest, String email, String link) {
        IdentityProperties.Limit limit = new IdentityProperties.Limit(1, Duration.ofHours(1));
        return new IdentityProperties(List.of(), new IdentityProperties.Keys(digest, email, link),
                new IdentityProperties.Limits(limit, limit, limit));
    }
}
