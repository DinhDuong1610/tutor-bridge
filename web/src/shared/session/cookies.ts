import "server-only";
import { cookies } from "next/headers";

/** The admin session (contract `cookieAuth`, ADR-0004). The browser never reads it. */
export const SESSION_COOKIE = "tb_session";

/** Secure, HttpOnly, SameSite=Strict: no script access, never sent from another site. */
export const SESSION_COOKIE_OPTIONS = {
  httpOnly: true,
  secure: true,
  sameSite: "strict",
  path: "/",
} as const;

export async function readSessionToken(): Promise<string | null> {
  return (await cookies()).get(SESSION_COOKIE)?.value || null;
}

/** For the login Server Action (TB-08, GD-13). Expires with the absolute session expiry. */
export async function setSessionCookie(token: string, absoluteExpiresAt: Date) {
  (await cookies()).set(SESSION_COOKIE, token, { ...SESSION_COOKIE_OPTIONS, expires: absoluteExpiresAt });
}

export async function clearSessionCookie() {
  (await cookies()).delete({ name: SESSION_COOKIE, path: "/" });
}

/**
 * The first factor's challenge between the two AD01 steps. Kept on this server's side of the browser: an
 * HttpOnly cookie for the admin paths only, gone when the challenge ends, so the challengeToken is never
 * shown to the page or to its scripts and never logged (AD01).
 */
export const CHALLENGE_COOKIE = "tb_admin_challenge";

export async function setChallengeCookie(challengeToken: string, expiresAt: Date) {
  (await cookies()).set(CHALLENGE_COOKIE, challengeToken, { ...SESSION_COOKIE_OPTIONS, path: "/admin", expires: expiresAt });
}

export async function readChallengeToken(): Promise<string | null> {
  return (await cookies()).get(CHALLENGE_COOKIE)?.value || null;
}

export async function clearChallengeCookie() {
  (await cookies()).delete({ name: CHALLENGE_COOKIE, path: "/admin" });
}
