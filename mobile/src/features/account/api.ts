import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { call } from "@/shared/api/errors";
import type { RegistrationForm, SignInForm } from "./schemas";

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
