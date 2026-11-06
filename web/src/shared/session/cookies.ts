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
