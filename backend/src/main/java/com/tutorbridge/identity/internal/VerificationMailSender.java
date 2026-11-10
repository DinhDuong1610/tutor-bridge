package com.tutorbridge.identity.internal;

import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** The email of {@link AccountRegistration#VERIFICATION_REQUESTED}: only while the account is UNVERIFIED. */
@Component
@ConditionalOnBooleanProperty("tutorbridge.worker.enabled")
class VerificationMailSender extends LinkMailSender {

    static final String SUBJECT = "Xác minh email TutorBridge";

    VerificationMailSender(JavaMailSender mail, AccountRepository accounts, AccountSecrets secrets,
            JsonMapper json, Clock clock, VerificationMailProperties properties) {
        super(mail, accounts, secrets, json, clock, properties, new Kind(
                AccountRegistration.VERIFICATION_REQUESTED, AccountRepository.VERIFY_EMAIL, List.of("UNVERIFIED"),
                "verify-email", SUBJECT, """
                        Chào bạn,

                        Vui lòng mở liên kết sau trên điện thoại đã cài TutorBridge để xác minh email:

                        %s

                        Liên kết có hiệu lực trong 30 phút và chỉ dùng được một lần. Nếu bạn không đăng ký TutorBridge, hãy bỏ qua email này.
                        """));
    }
}
