package com.tutorbridge.identity.web;

import com.tutorbridge.identity.internal.PasswordRecovery;
import com.tutorbridge.platform.api.PublicEndpoints;
import com.tutorbridge.platform.api.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** requestRecovery and resetPassword. Public: no session, no Idempotency-Key (CR-014). */
@RestController
class RecoveryController {

    static final String RECOVERY_SENT =
            "Nếu email có tài khoản, chúng tôi đã gửi liên kết đặt lại mật khẩu. Vui lòng kiểm tra hộp thư.";

    record RecoveryInput(@NotNull @Email @Size(max = 254) String email) {}

    // Length in code points is checked by the service; UTF-16 length bounds it from above.
    record ResetInput(@NotNull @Size(min = 20, max = 512) String token, @NotNull @Size(max = 256) String newPassword) {}

    record GenericAcknowledgment(String message, String traceId) {}

    private final PasswordRecovery recovery;

    RecoveryController(PasswordRecovery recovery) {
        this.recovery = recovery;
    }

    @PostMapping("/api/v1/auth/recovery")
    @ResponseStatus(HttpStatus.ACCEPTED)
    GenericAcknowledgment requestRecovery(@Valid @RequestBody RecoveryInput input, HttpServletRequest request) {
        recovery.requestRecovery(input.email(), request.getRemoteAddr());
        return new GenericAcknowledgment(RECOVERY_SENT, TraceContext.current());
    }

    @PostMapping("/api/v1/auth/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@Valid @RequestBody ResetInput input) {
        recovery.resetPassword(input.token(), input.newPassword());
    }

    @Configuration(proxyBeanMethods = false)
    static class Endpoints {

        @Bean
        PublicEndpoints recoveryEndpoints() {
            return () -> List.of(
                    new PublicEndpoints.Endpoint(HttpMethod.POST, "/api/v1/auth/recovery"),
                    new PublicEndpoints.Endpoint(HttpMethod.POST, "/api/v1/auth/reset"));
        }
    }
}
