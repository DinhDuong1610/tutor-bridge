package com.tutorbridge.identity.internal;

import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The email of {@link PasswordRecovery#RESET_REQUESTED}: while the account is ACTIVE or UNVERIFIED
 * (Q2 of TB-9). Opens {@code /reset-password} of the app (Q7 of TB-10).
 */
@Component
@ConditionalOnBooleanProperty("tutorbridge.worker.enabled")
class PasswordResetMailSender extends LinkMailSender {

    static final String SUBJECT = "Đặt lại mật khẩu TutorBridge";

    PasswordResetMailSender(JavaMailSender mail, AccountRepository accounts, AccountSecrets secrets,
            JsonMapper json, Clock clock, VerificationMailProperties properties) {
        super(mail, accounts, secrets, json, clock, properties, new Kind(
                PasswordRecovery.RESET_REQUESTED, AccountRepository.RESET_PASSWORD, List.of("ACTIVE", "UNVERIFIED"),
                "reset-password", SUBJECT, """
                        Chào bạn,

                        Có yêu cầu đặt lại mật khẩu cho tài khoản TutorBridge dùng email này. Mở liên kết sau trên điện thoại đã cài TutorBridge để đặt mật khẩu mới:

                        %s

                        Liên kết có hiệu lực trong 15 phút và chỉ dùng được một lần. Đặt lại mật khẩu sẽ đăng xuất mọi thiết bị. Nếu bạn không yêu cầu, hãy bỏ qua email này; mật khẩu hiện tại vẫn giữ nguyên.
                        """));
    }
}
