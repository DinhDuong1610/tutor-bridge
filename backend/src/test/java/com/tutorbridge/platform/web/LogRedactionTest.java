package com.tutorbridge.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonWriter;

class LogRedactionTest {

    @Test
    void masks_bearer_tokens_session_cookies_and_secret_pairs() {
        assertThat(LogRedaction.redact("Authorization: Bearer abc.DEF-123 rejected"))
                .isEqualTo("Authorization: Bearer [REDACTED] rejected")
                .doesNotContain("abc.DEF-123");
        assertThat(LogRedaction.redact("Cookie: tb_session=s3cr3t; tb_csrf=c5rf"))
                .isEqualTo("Cookie: tb_session=[REDACTED]; tb_csrf=[REDACTED]");
        assertThat(LogRedaction.redact("password=hunter2&token=t0k3n"))
                .isEqualTo("password=[REDACTED]&token=[REDACTED]");
        assertThat(LogRedaction.redact("{\"password\": \"hunter2\", \"Idempotency-Key\": \"k1234567890123456\"}"))
                .doesNotContain("hunter2").doesNotContain("k1234567890123456");
    }

    /** Spring MVC logs a request record at DEBUG, e.g. ChangePasswordInput[currentPassword=…] (#197). */
    @Test
    void masks_secret_keys_that_have_a_prefix() {
        assertThat(LogRedaction.redact("Read to [ChangePasswordInput[currentPassword=hunter2, newPassword=n3wpass]]"))
                .isEqualTo("Read to [ChangePasswordInput[currentPassword=[REDACTED], newPassword=[REDACTED]]]");
        assertThat(LogRedaction.redact("ResetInput[token=t0k3n, newPassword=n3wpass]"))
                .isEqualTo("ResetInput[token=[REDACTED], newPassword=[REDACTED]]");
        assertThat(LogRedaction.redact("{\"new_password\":\"n3wpass\",\"csrfToken\":\"c5rf\",\"X-Reauth-Token\": \"r3auth\"}"))
                .doesNotContain("n3wpass").doesNotContain("c5rf").doesNotContain("r3auth");
    }

    @Test
    void leaves_keys_that_only_start_with_a_secret_word_alone() {
        assertThat(LogRedaction.redact("tokenCount=5 passwordAgeDays=30 secretary=Lan"))
                .isEqualTo("tokenCount=5 passwordAgeDays=30 secretary=Lan");
    }

    @Test
    void masks_email_addresses() {
        assertThat(LogRedaction.redact("Verification sent to parent.one+test@example.test"))
                .isEqualTo("Verification sent to [EMAIL]");
    }

    @Test
    void leaves_ordinary_messages_alone() {
        String message = "Offer 0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10 accepted in 42 ms";
        assertThat(LogRedaction.redact(message)).isEqualTo(message);
        assertThat(LogRedaction.redact("")).isEmpty();
        assertThat(LogRedaction.redact(null)).isNull();
    }

    @Test
    void applies_to_every_string_in_the_json_log_line() {
        JsonWriter<Map<String, String>> writer = JsonWriter.of(members -> {
            members.add("message", map -> map.get("message"));
            members.add("traceId", map -> map.get("traceId"));
            new LogRedaction().customize(cast(members));
        });

        String json = writer.writeToString(Map.of(
                "message", "Login for a@b.test with Bearer xyz", "traceId", "0123456789abcdef"));

        assertThat(json).contains("[EMAIL]").contains("Bearer [REDACTED]").contains("0123456789abcdef")
                .doesNotContain("a@b.test").doesNotContain("xyz");
    }

    @SuppressWarnings("unchecked")
    private static JsonWriter.Members<Object> cast(JsonWriter.Members<?> members) {
        return (JsonWriter.Members<Object>) members;
    }
}
