import { useInfiniteQuery } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { call } from "@/shared/api/errors";
import type { ChangePasswordForm, EmailOnlyForm, RegistrationForm, ResetPasswordForm, SignInForm } from "./schemas";

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

export const OWN_SESSIONS_KEY = ["me", "sessions"] as const;
const SESSIONS_PAGE_LIMIT = 50; // maxItems of OwnSessionPage

/** Own sessions, newest page first; more pages through `fetchNextPage` (cursor from the server). */
export function useOwnSessions() {
  return useInfiniteQuery({
    queryKey: OWN_SESSIONS_KEY,
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) =>
      call(api.GET("/me/sessions", { params: { query: { limit: SESSIONS_PAGE_LIMIT, ...(pageParam ? { cursor: pageParam } : {}) } } })),
    getNextPageParam: (page) => page.nextCursor,
  });
}

// The contract declares no Idempotency-Key for logout and revoke. Both are idempotent on the
// server ("idempotent", "enforce revoke on next request"), so a retry cannot do harm.

export function useRevokeSession() {
  return useCommand((sessionId: string) =>
    call(api.DELETE("/me/sessions/{sessionId}", { params: { path: { sessionId } } })),
  );
}

export function useLogout() {
  return useCommand(() => call(api.POST("/auth/logout", {})));
}

/**
 * Changing the password revokes every session, this one too (UC01), so a success is followed by
 * signing in again. No Idempotency-Key in the contract; a retry after an unknown outcome that
 * finds the session gone ends in sign-in, which is the right place anyway.
 */
export function useChangePassword() {
  return useCommand((input: ChangePasswordForm) => call(api.POST("/me/password", { body: input })));
}

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
