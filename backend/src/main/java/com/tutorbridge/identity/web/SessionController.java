package com.tutorbridge.identity.web;

import com.tutorbridge.identity.internal.AccountSessions;
import com.tutorbridge.identity.internal.Masked;
import com.tutorbridge.platform.api.ClientAddress;
import com.tutorbridge.platform.api.Actor;
import com.tutorbridge.platform.api.Page;
import com.tutorbridge.platform.api.PublicEndpoints;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * loginAccount (public, no Idempotency-Key per CR-014), logoutAccount, listOwnSessions,
 * revokeOwnSession. Mobile sends the access token as a bearer; admin web sign-in is TB-13.
 */
@RestController
class SessionController {

    record LoginInput(@NotNull @Email @Size(max = 254) String email, @NotNull @Size(min = 1, max = 256) String password) {

        @Override
        public String toString() {
            return "LoginInput[email=%s, password=%s]".formatted(Masked.VALUE, Masked.VALUE);
        }
    }

    private final AccountSessions sessions;

    SessionController(AccountSessions sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/api/v1/auth/login")
    AccountSessions.Credential login(@Valid @RequestBody LoginInput input, HttpServletRequest request,
            ClientAddress source) {
        return sessions.login(input.email(), input.password(), source.value(),
                request.getHeader(HttpHeaders.USER_AGENT));
    }

    @PostMapping("/api/v1/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@AuthenticationPrincipal Actor actor) {
        sessions.logout(actor);
    }

    @GetMapping("/api/v1/me/sessions")
    Page<AccountSessions.OwnSession> list(@AuthenticationPrincipal Actor actor,
            @RequestParam(required = false) @Size(max = 500) String cursor,
            @RequestParam(required = false) Integer limit) {
        return sessions.listOwn(actor, cursor, limit);
    }

    @DeleteMapping("/api/v1/me/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal Actor actor, @PathVariable UUID sessionId) {
        sessions.revokeOwn(actor, sessionId);
    }

    @Configuration(proxyBeanMethods = false)
    static class Endpoints {

        @Bean
        PublicEndpoints loginEndpoint() {
            return () -> List.of(new PublicEndpoints.Endpoint(HttpMethod.POST, "/api/v1/auth/login"));
        }
    }
}
