package com.tutorbridge.identity.internal;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the identity module ({@code tutorbridge.identity.*}, values in application.yaml).
 *
 * <p>Keys are base64 of 32 random bytes, read from the environment and never committed. The limits
 * are provisional values without measurements (Đính, Q6 of TB-8, 08/11/2026).
 */
@ConfigurationProperties("tutorbridge.identity")
public record IdentityProperties(List<String> termsVersions, Keys keys, Limits limits) {

    public IdentityProperties {
        termsVersions = termsVersions == null ? List.of()
                : termsVersions.stream().map(String::strip).filter(v -> !v.isEmpty()).toList();
        Objects.requireNonNull(keys, "tutorbridge.identity.keys");
        Objects.requireNonNull(limits, "tutorbridge.identity.limits");
    }

    /**
     * @param emailDigest HMAC-SHA256 key for {@code accounts.email_digest} and the source digests
     * @param emailEncryption AES-256-GCM key for {@code accounts.email_ciphertext}
     * @param linkToken AES-256-GCM key for the one-time link codes carried in outbox events
     */
    public record Keys(String emailDigest, String emailEncryption, String linkToken) {}

    public record Limits(Limit registerPerSource, Limit resendPerAccount, Limit resendPerSource,
            Limit recoveryPerAccount, Limit recoveryPerSource) {

        public Limits {
            Objects.requireNonNull(registerPerSource, "register-per-source");
            Objects.requireNonNull(resendPerAccount, "resend-per-account");
            Objects.requireNonNull(resendPerSource, "resend-per-source");
            Objects.requireNonNull(recoveryPerAccount, "recovery-per-account");
            Objects.requireNonNull(recoveryPerSource, "recovery-per-source");
        }
    }

    /** At most {@code attempts} per fixed {@code window}. */
    public record Limit(int attempts, Duration window) {

        public Limit {
            if (attempts < 1 || window == null || window.isNegative() || window.isZero()) {
                throw new IllegalArgumentException("a limit needs attempts >= 1 and a positive window");
            }
        }
    }
}
