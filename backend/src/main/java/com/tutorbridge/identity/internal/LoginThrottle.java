package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.TooManyRequests;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Five wrong passwords in the last 15 minutes, by email or by source, delay the next try until the
 * oldest of those five is 15 minutes old (UC01; sliding window, Đính 10/11/2026). Login, change of
 * password and reauthentication share it (Q3 of TB-9). A refused try is not a failure: no password
 * was checked. State lives in auth_login_failures (INV-80).
 */
@Component
class LoginThrottle {

    static final int FAILURES_BEFORE_DELAY = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final SessionRepository sessions;
    private final Clock clock;

    LoginThrottle(SessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    void refuseWhileDelayed(String emailDigest, String sourceDigest) {
        Instant now = clock.instant();
        Duration wait = Duration.ZERO;
        for (var subject : List.of(List.of("ACCOUNT", emailDigest), List.of("SOURCE", sourceDigest))) {
            List<Instant> recent = sessions.recentFailures(subject.get(0), subject.get(1), now.minus(WINDOW),
                    FAILURES_BEFORE_DELAY);
            if (recent.size() >= FAILURES_BEFORE_DELAY) {
                Duration w = Duration.between(now, recent.getLast().plus(WINDOW));
                if (w.compareTo(wait) > 0) {
                    wait = w;
                }
            }
        }
        if (!wait.isZero() && !wait.isNegative()) {
            throw new TooManyRequests("LOGIN_DELAYED", "Bạn đã nhập sai nhiều lần. Vui lòng thử lại sau.", wait);
        }
    }

    void recordFailure(String emailDigest, String sourceDigest) {
        Instant now = clock.instant();
        sessions.recordFailure("ACCOUNT", emailDigest, now);
        sessions.recordFailure("SOURCE", sourceDigest, now);
    }
}
