package com.tutorbridge.identity.web;

import com.tutorbridge.identity.internal.AccountSessions;
import com.tutorbridge.identity.internal.AdminSignIn;
import com.tutorbridge.identity.internal.Masked;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.PublicEndpoints;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * loginAdmin and completeAdminMfa (public, no Idempotency-Key per CR-014) and getAdminSession (TB-13). The
 * admin web calls them from its server and keeps the credential in its HttpOnly cookie; nothing here sets a
 * cookie.
 */
@RestController
class AdminSignInController {

    record AdminLoginInput(@NotNull @Email @Size(max = 254) String email,
            @NotNull @Size(min = 1, max = 256) String password) {

        @Override
        public String toString() {
            return "AdminLoginInput[email=%s, password=%s]".formatted(Masked.VALUE, Masked.VALUE);
        }
    }

    /** {@code AdminMfaInput}. */
    record AdminMfaInput(@NotNull @Size(min = 20, max = 512) String challengeToken,
            @NotNull @Pattern(regexp = "^[0-9]{6,8}$") String code) {

        @Override
        public String toString() {
            return "AdminMfaInput[challengeToken=%s, code=%s]".formatted(Masked.VALUE, Masked.VALUE);
        }
    }

    private final AdminSignIn signIn;

    AdminSignInController(AdminSignIn signIn) {
        this.signIn = signIn;
    }

    @PostMapping("/api/v1/auth/admin-login")
    AdminSignIn.Challenge login(@Valid @RequestBody AdminLoginInput input, HttpServletRequest request) {
        return signIn.login(input.email(), input.password(), request.getRemoteAddr());
    }

    @PostMapping("/api/v1/auth/admin-mfa")
    AccountSessions.Credential completeMfa(@Valid @RequestBody AdminMfaInput input, HttpServletRequest request) {
        return signIn.completeMfa(input.challengeToken(), input.code(), request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT));
    }

    @GetMapping("/api/v1/admin/session")
    AdminSignIn.AdminSession session(@AuthenticationPrincipal Actor actor) {
        return signIn.session(actor);
    }

    @Configuration(proxyBeanMethods = false)
    static class Endpoints {

        @Bean
        PublicEndpoints adminSignInEndpoints() {
            return () -> List.of(
                    new PublicEndpoints.Endpoint(HttpMethod.POST, "/api/v1/auth/admin-login"),
                    new PublicEndpoints.Endpoint(HttpMethod.POST, "/api/v1/auth/admin-mfa"));
        }
    }
}
