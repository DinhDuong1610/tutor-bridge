package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.Outbox;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.IdGenerator;

/**
 * Creates a one-time link code and queues its email in the caller's transaction (INV-21). Only the
 * SHA-256 digest is stored; the outbox payload holds the code encrypted for its challenge, never in
 * clear, and never the email address.
 */
@Component
class LinkIssuer {

    private final AccountRepository accounts;
    private final AccountSecrets secrets;
    private final Outbox outbox;
    private final IdGenerator ids;

    LinkIssuer(AccountRepository accounts, AccountSecrets secrets, Outbox outbox, IdGenerator ids) {
        this.accounts = accounts;
        this.secrets = secrets;
        this.outbox = outbox;
        this.ids = ids;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void issue(UUID accountId, String purpose, Duration lifetime, String eventType, Instant now) {
        UUID challengeId = ids.generateId();
        String token = secrets.newLinkToken();
        Instant expiresAt = now.plus(lifetime);
        accounts.insertChallenge(challengeId, accountId, purpose, AccountSecrets.linkTokenDigest(token), now, expiresAt);
        outbox.publish("account", accountId, eventType, Map.of(
                "challengeId", challengeId.toString(),
                "tokenCiphertext", secrets.encryptLinkToken(challengeId, token),
                "expiresAt", expiresAt.toString()), purpose.toLowerCase(java.util.Locale.ROOT) + ":" + challengeId);
    }
}
