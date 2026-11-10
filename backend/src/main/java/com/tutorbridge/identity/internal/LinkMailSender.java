package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.OutboxHandler;
import com.tutorbridge.platform.api.OutboxQueue.ClaimedEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mails a one-time link on the worker role. The event payload carries the code only encrypted for
 * its challenge (Huy, 11/11/2026); it is decrypted here, just before sending, together with the
 * stored email. Nothing is sent once the code is used or expired, or the account left the statuses
 * that may receive it, so a late retry never mails a dead link. A failed send throws and is retried;
 * a send that succeeded but was not marked delivered may reach the user twice, with the same link.
 */
abstract class LinkMailSender implements OutboxHandler {

    /** What differs between the verification and the reset email. */
    record Kind(String eventType, String purpose, List<String> statuses, String path, String subject, String body) {}

    private final JavaMailSender mail;
    private final AccountRepository accounts;
    private final AccountSecrets secrets;
    private final JsonMapper json;
    private final Clock clock;
    private final String from;
    private final String linkBase;
    private final Kind kind;

    LinkMailSender(JavaMailSender mail, AccountRepository accounts, AccountSecrets secrets, JsonMapper json,
            Clock clock, VerificationMailProperties properties, Kind kind) {
        this.mail = mail;
        this.accounts = accounts;
        this.secrets = secrets;
        this.json = json;
        this.clock = clock;
        this.from = required(properties.from(), "tutorbridge.identity.mail.from");
        this.linkBase = required(properties.linkBase(), "tutorbridge.identity.mail.link-base");
        this.kind = kind;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set on the worker role");
        }
        return value.strip();
    }

    @Override
    public String eventType() {
        return kind.eventType();
    }

    @Override
    public void handle(ClaimedEvent event) {
        JsonNode payload = json.readTree(event.payload());
        UUID challengeId = UUID.fromString(payload.get("challengeId").asString());
        Instant now = clock.instant();
        Optional<byte[]> emailCiphertext = accounts.emailForLink(challengeId, kind.purpose(), kind.statuses(), now);
        if (emailCiphertext.isEmpty()) {
            return;
        }
        String token = secrets.decryptLinkToken(challengeId, payload.get("tokenCiphertext").asString());
        String to = secrets.decryptEmail(event.aggregateId(), emailCiphertext.get());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(kind.subject());
        message.setText(kind.body().formatted(link(token)));
        mail.send(message);
    }

    String link(String token) {
        return linkBase + kind.path() + "?token=" + URLEncoder.encode(token, StandardCharsets.US_ASCII);
    }
}
