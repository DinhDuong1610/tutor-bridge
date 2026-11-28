package com.tutorbridge.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Spring MVC logs the request records it reads at DEBUG; none may print a secret or an email (#197). */
class SecretInputsTest {

    private static final String PASSWORD = "mat-khau-bi-mat-1";
    private static final String OTHER = "mat-khau-moi-bi-mat-2";
    private static final String TOKEN = "token-bi-mat-0123456789";
    private static final String EMAIL = "phu.huynh@example.test";

    @Test
    void request_records_print_no_secret() {
        for (Object input : new Object[] {
            new SessionController.LoginInput(EMAIL, PASSWORD),
            new RegistrationController.RegistrationInput(EMAIL, PASSWORD, "Ten Gia", "PARENT", "2026-11-01"),
            new RegistrationController.VerifyEmailInput(TOKEN),
            new RegistrationController.RecoveryInput(EMAIL),
            new RecoveryController.RecoveryInput(EMAIL),
            new RecoveryController.ResetInput(TOKEN, OTHER),
            new PasswordController.ChangePasswordInput(PASSWORD, OTHER),
            new PasswordController.ReauthInput(PASSWORD, "PRIVACY"),
        }) {
            assertThat(input.toString()).contains("[REDACTED]").doesNotContain(PASSWORD).doesNotContain(OTHER)
                    .doesNotContain(TOKEN).doesNotContain(EMAIL).doesNotContain("Ten Gia");
        }
    }

    @Test
    void request_records_still_print_what_is_not_secret() {
        assertThat(new PasswordController.ReauthInput(PASSWORD, "PRIVACY").toString()).contains("purpose=PRIVACY");
        assertThat(new RegistrationController.RegistrationInput(EMAIL, PASSWORD, "Ten Gia", "TUTOR", "2026-11-01")
                .toString()).contains("role=TUTOR").contains("termsVersion=2026-11-01");
    }
}
