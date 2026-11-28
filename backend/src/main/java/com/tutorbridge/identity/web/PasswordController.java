package com.tutorbridge.identity.web;

import com.tutorbridge.identity.internal.Masked;
import com.tutorbridge.identity.internal.PasswordChange;
import com.tutorbridge.platform.api.ClientAddress;
import com.tutorbridge.platform.api.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** changePassword and reauthenticate: need a session; no Idempotency-Key (CR-014). */
@RestController
class PasswordController {

    // Length in code points is checked by the service; UTF-16 length bounds it from above.
    record ChangePasswordInput(@NotNull @Size(max = 256) String currentPassword,
            @NotNull @Size(max = 256) String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordInput[currentPassword=%s, newPassword=%s]".formatted(Masked.VALUE, Masked.VALUE);
        }
    }

    record ReauthInput(@NotNull @Size(max = 256) String password,
            @NotNull @Pattern(regexp = "PRIVACY|CHANGE_PASSWORD|REVOKE_SESSIONS") String purpose) {

        @Override
        public String toString() {
            return "ReauthInput[password=%s, purpose=%s]".formatted(Masked.VALUE, purpose);
        }
    }

    private final PasswordChange passwords;

    PasswordController(PasswordChange passwords) {
        this.passwords = passwords;
    }

    @PostMapping("/api/v1/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal Actor actor, @Valid @RequestBody ChangePasswordInput input,
            ClientAddress source) {
        passwords.changePassword(actor, input.currentPassword(), input.newPassword(), source.value());
    }

    @PostMapping("/api/v1/auth/reauth")
    PasswordChange.ReauthToken reauthenticate(@AuthenticationPrincipal Actor actor,
            @Valid @RequestBody ReauthInput input, ClientAddress source) {
        return passwords.reauthenticate(actor, input.password(), source.value());
    }
}
