package com.tutorbridge.identity.web;

import com.tutorbridge.identity.internal.AccountRegistration;
import com.tutorbridge.identity.internal.Masked;
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

/** registerAccount, verifyEmail, resendVerification. Public: no session, no Idempotency-Key (CR-014). */
@RestController
class RegistrationController {

    static final String REGISTERED = "Nếu email hợp lệ, chúng tôi đã gửi liên kết xác minh. Vui lòng kiểm tra hộp thư.";
    static final String RESENT = "Nếu email cần xác minh, chúng tôi đã gửi liên kết mới. Vui lòng kiểm tra hộp thư.";

    record RegistrationInput(
            @NotNull @Email @Size(max = 254) String email,
            // Length in code points is checked by the service; UTF-16 length bounds it from above.
            @NotNull @Size(max = 256) String password,
            @NotNull @Size(min = 1, max = 100) String displayName,
            @NotNull String role,
            @NotNull @Size(min = 1, max = 100) String termsVersion) {

        @Override
        public String toString() {
            return "RegistrationInput[email=%s, password=%s, displayName=%s, role=%s, termsVersion=%s]"
                    .formatted(Masked.VALUE, Masked.VALUE, Masked.VALUE, role, termsVersion);
        }
    }

    record VerifyEmailInput(@NotNull @Size(min = 20, max = 512) String token) {

        @Override
        public String toString() {
            return "VerifyEmailInput[token=%s]".formatted(Masked.VALUE);
        }
    }

    record RecoveryInput(@NotNull @Email @Size(max = 254) String email) {

        @Override
        public String toString() {
            return "RecoveryInput[email=%s]".formatted(Masked.VALUE);
        }
    }

    record GenericAcknowledgment(String message, String traceId) {}

    private final AccountRegistration registration;

    RegistrationController(AccountRegistration registration) {
        this.registration = registration;
    }

    @PostMapping("/api/v1/auth/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    GenericAcknowledgment register(@Valid @RequestBody RegistrationInput input, HttpServletRequest request) {
        registration.register(new AccountRegistration.Registration(input.email(), input.password(),
                input.displayName(), input.role(), input.termsVersion()), request.getRemoteAddr());
        return new GenericAcknowledgment(REGISTERED, TraceContext.current());
    }

    @PostMapping("/api/v1/auth/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verify(@Valid @RequestBody VerifyEmailInput input) {
        registration.verifyEmail(input.token());
    }

    @PostMapping("/api/v1/auth/verification-resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    GenericAcknowledgment resend(@Valid @RequestBody RecoveryInput input, HttpServletRequest request) {
        registration.resendVerification(input.email(), request.getRemoteAddr());
        return new GenericAcknowledgment(RESENT, TraceContext.current());
    }

    @Configuration(proxyBeanMethods = false)
    static class Endpoints {

        @Bean
        PublicEndpoints registrationEndpoints() {
            return () -> List.of(
                    new PublicEndpoints.Endpoint(HttpMethod.POST, "/api/v1/auth/register"),
                    new PublicEndpoints.Endpoint(HttpMethod.POST, "/api/v1/auth/verify"),
                    new PublicEndpoints.Endpoint(HttpMethod.POST, "/api/v1/auth/verification-resend"));
        }
    }
}
