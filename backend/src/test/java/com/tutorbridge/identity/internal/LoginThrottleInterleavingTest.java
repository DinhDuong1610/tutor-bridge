package com.tutorbridge.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.tutorbridge.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Codex review of #153: a wrong try is recorded by begin() before its slow password check. A correct
 * login that finishes during that check clears the account's failures, and used to delete the row
 * of the try still being checked, so that wrong password was never counted. These steps are run in
 * that order on PostgreSQL, which the HTTP tests cannot pause in between.
 */
class LoginThrottleInterleavingTest extends IntegrationTest {

    @Autowired
    private LoginThrottle throttle;

    @Autowired
    private SessionRepository sessions;

    @Autowired
    private TransactionOperations transactions;

    @Test
    void a_wrong_try_still_counts_when_a_correct_login_cleared_the_failures_during_its_check() {
        String email = digest();
        String source = digest();

        LoginThrottle.Attempt wrong = throttle.begin(email, source);
        // A correct login for the same email finishes while the wrong try is still hashing.
        transactions.executeWithoutResult(status -> sessions.clearAccountFailures(email));
        throttle.failed(wrong);

        assertThat(failures("ACCOUNT", email)).isEqualTo(1);
        assertThat(failures("SOURCE", source)).isEqualTo(1);
    }

    @Test
    void a_wrong_try_is_counted_once() {
        String email = digest();
        String source = digest();

        throttle.failed(throttle.begin(email, source));

        assertThat(failures("ACCOUNT", email)).isEqualTo(1);
        assertThat(failures("SOURCE", source)).isEqualTo(1);
    }

    @Test
    void a_correct_try_is_not_counted() {
        String email = digest();
        String source = digest();

        throttle.passed(throttle.begin(email, source));

        assertThat(failures("ACCOUNT", email)).isZero();
        assertThat(failures("SOURCE", source)).isZero();
    }

    private int failures(String scope, String digest) {
        return jdbc.sql("select count(*) from auth_login_failures where scope = ? and subject_digest = ?")
                .params(scope, digest).query(Integer.class).single();
    }

    private static String digest() {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
