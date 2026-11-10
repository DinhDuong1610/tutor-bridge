package com.tutorbridge.identity.internal;

import com.tutorbridge.platform.api.OutboxHandler;
import com.tutorbridge.platform.api.OutboxQueue.ClaimedEvent;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends the email of {@link AccountRegistration#VERIFICATION_REQUESTED} on the worker role.
 *
 * <p>The code is decrypted only here, just before sending, with the key bound to its challenge
 * (Huy, 11/11/2026). Nothing is sent when the code is already used or expired, or the account is no
 * longer UNVERIFIED, so a late retry never mails a dead link. A failed send throws and is retried by
 * the dispatcher; a send that succeeded but could not be marked delivered may reach the user twice,
 * with the same link.
 */
@Component
@ConditionalOnBooleanProperty("tutorbridge.worker.enabled")
class VerificationMailSender implements OutboxHandler {

    static final String SUBJECT = "Xác minh email TutorBridge";

    private final JavaMailSender mail;
    private final AccountRepository accounts;
    private final AccountSecrets secrets;
    private final JsonMapper json;
    private final Clock clock;
    private final String from;
    private final String linkBase;

    VerificationMailSender(JavaMailSender mail, AccountRepository accounts, AccountSecrets secrets,
            JsonMapper json, Clock clock, VerificationMailProperties properties) {
        this.mail = mail;
        this.accounts = accounts;
        this.secrets = secrets;
        this.json = json;
        this.clock = clock;
        this.from = required(properties.from(), "tutorbridge.identity.mail.from");
        this.linkBase = required(properties.linkBase(), "tutorbridge.identity.mail.link-base");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set on the worker role");
        }
        return value.strip();
    }

    @Override
    public String eventType() {
        return AccountRegistration.VERIFICATION_REQUESTED;
    }

    @Override
    public void handle(ClaimedEvent event) {
        JsonNode payload = json.readTree(event.payload());
        UUID challengeId = UUID.fromString(payload.get("challengeId").asString());
        Instant now = clock.instant();
        Optional<byte[]> emailCiphertext = accounts.emailToVerify(challengeId, now);
        if (emailCiphertext.isEmpty()) {
            return;
        }
        String token = secrets.decryptLinkToken(challengeId, payload.get("tokenCiphertext").asString());
        String to = secrets.decryptEmail(event.aggregateId(), emailCiphertext.get());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(SUBJECT);
        message.setText(body(link(token)));
        mail.send(message);
    }

    String link(String token) {
        return linkBase + "verify-email?token=" + URLEncoder.encode(token, StandardCharsets.US_ASCII);
    }

    private static String body(String link) {
        return """
                Chào bạn,

                Vui lòng mở liên kết sau trên điện thoại đã cài TutorBridge để xác minh email:

                %s

                Liên kết có hiệu lực trong 30 phút và chỉ dùng được một lần. Nếu bạn không đăng ký TutorBridge, hãy bỏ qua email này.
                """.formatted(link);
    }
}
