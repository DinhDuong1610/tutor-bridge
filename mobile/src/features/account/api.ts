import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { call } from "@/shared/api/errors";
import type { EmailOnlyForm, RegistrationForm, ResetPasswordForm, SignInForm } from "./schemas";

// The contract declares no Idempotency-Key for these public commands (handoff TB-10, Q4), so the
// key from useCommand is not sent. Retrying register cannot create a second account: the email
// is unique and the answer is the same either way.

export function useRegister(termsVersion: string) {
  return useCommand((input: RegistrationForm) =>
    call(
      api.POST("/auth/register", {
        body: {
          email: input.email,
          password: input.password,
          displayName: input.displayName,
          role: input.role,
          termsVersion,
        },
      }),
    ),
  );
}

export function useSignIn() {
  return useCommand((input: SignInForm) => call(api.POST("/auth/login", { body: input })));
}

// Verification and reset links are single use. Without an Idempotency-Key in the contract, a retry
// after an unknown outcome may answer "already used" although the first attempt worked; the
// screens say so instead of claiming failure.

export function useVerifyEmail(token: string) {
  return useCommand(() => call(api.POST("/auth/verify", { body: { token } })));
}

/** Answers the same whether or not the email has an account, so the screen shows the server's message. */
export function useResendVerification() {
  return useCommand((input: EmailOnlyForm) => call(api.POST("/auth/verification-resend", { body: input })));
}

/** Same generic answer for a missing email (UC01). */
export function useRequestRecovery() {
  return useCommand((input: EmailOnlyForm) => call(api.POST("/auth/recovery", { body: input })));
}

export function useResetPassword(token: string) {
  return useCommand((input: ResetPasswordForm) =>
    call(api.POST("/auth/reset", { body: { token, newPassword: input.newPassword } })),
  );
}
