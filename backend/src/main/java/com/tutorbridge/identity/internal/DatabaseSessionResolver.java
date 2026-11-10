package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.SessionResolver;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Looks the token digest up on every request: revocation, account status, {@code auth_epoch}, idle and
 * absolute expiry (ADR-0004, ADR-0010). Nothing is cached, so a revoke or a lock applies to the next
 * request on every instance (AC-UC01-03, INV-80). The token itself is never stored or logged.
 */
@Component
class DatabaseSessionResolver implements SessionResolver {

    private final SessionRepository sessions;
    private final Clock clock;

    DatabaseSessionResolver(SessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    public Optional<Actor> resolve(String token) {
        if (token.length() < 20 || token.length() > 512) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return sessions.resolve(AccountSecrets.linkTokenDigest(token), now).map(live -> {
            sessions.touch(live.sessionId(), now);
            return new Actor(live.accountId(), live.role(), live.sessionId());
        });
    }
}
