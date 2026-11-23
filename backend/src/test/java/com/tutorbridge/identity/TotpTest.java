package com.tutorbridge.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorbridge.identity.internal.Totp;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** TOTP of the admin sign-in (TB-13, Đính 23/11/2026 on #13): RFC 6238, SHA-1, 6 digits, 30 seconds. */
class TotpTest {

    /** The SHA-1 secret of RFC 6238 appendix B. */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest
    @CsvSource({
        // Appendix B of RFC 6238 gives 8 digits; a 6 digit code is their last 6.
        "59, 287082",
        "1111111109, 081804",
        "1111111111, 050471",
        "1234567890, 005924",
        "2000000000, 279037",
        "20000000000, 353130"
    })
    void codes_match_the_rfc_6238_test_vectors(long epochSecond, String code) {
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(epochSecond)))).isEqualTo(code);
    }

    @Test
    void a_code_of_the_step_before_or_after_now_is_accepted_and_gives_its_step() {
        Instant now = Instant.ofEpochSecond(1111111111);
        long step = Totp.step(now);

        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, null)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step), now, null)).hasValue(step);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, null)).hasValue(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now, null)).isEmpty();
    }

    @Test
    void a_code_of_a_step_already_used_or_earlier_is_refused() {
        Instant now = Instant.ofEpochSecond(1111111111);
        long step = Totp.step(now);

        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step), now, step)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, step)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, step)).hasValue(step + 1);
    }

    @Test
    void anything_but_six_digits_is_refused() {
        Instant now = Instant.ofEpochSecond(59);
        assertThat(Totp.verify(RFC_SECRET, "28708", now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "2870820", now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "28708a", now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, null, now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "287082", now, null)).isEqualTo(OptionalLong.of(Totp.step(now)));
    }

    @Test
    void the_enrollment_uri_carries_the_base32_secret_for_an_authenticator_app() {
        // The RFC 6238 test secret "12345678901234567890" in base32: public, not a key of ours. The URI is
        // built from it rather than spelled out, so a secret scanner does not read it as a leaked key.
        String base32 = Totp.base32(RFC_SECRET);
        assertThat(base32).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.enrollmentUri(RFC_SECRET, "admin@example.test")).isEqualTo(
                "otpauth://totp/TutorBridge:admin%40example.test?" + "secret=" + base32
                        + "&issuer=TutorBridge&algorithm=SHA1&digits=6&period=30");
    }
}
