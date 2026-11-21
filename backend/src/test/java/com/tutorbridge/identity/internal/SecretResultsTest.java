package com.tutorbridge.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spring MVC logs the records a controller returns at DEBUG; a token must not be in them (#197). */
class SecretResultsTest {

    private static final String TOKEN = "token-bi-mat-0123456789";

    @Test
    void returned_records_print_no_token() {
        Instant at = Instant.parse("2026-11-21T00:00:00Z");
        UUID session = UUID.fromString("0193a1b2-0000-7000-8000-000000000001");
        String credential = new AccountSessions.Credential(session, TOKEN, "PARENT", at, at).toString();
        String reauth = new PasswordChange.ReauthToken(TOKEN, at).toString();

        assertThat(credential).doesNotContain(TOKEN).contains("[REDACTED]").contains(session.toString())
                .contains("role=PARENT");
        assertThat(reauth).doesNotContain(TOKEN).contains("[REDACTED]").contains("expiresAt=" + at);
    }
}
