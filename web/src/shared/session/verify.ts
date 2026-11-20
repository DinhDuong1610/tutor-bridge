import "server-only";
import { redirect } from "next/navigation";
import { cache } from "react";
import { adminApi } from "@/shared/api/server";
import { readSessionToken } from "./cookies";

export const SIGN_IN_PATH = "/admin/sign-in";
/** Route handler that clears the cookie, since a Server Component cannot. */
export const SESSION_EXPIRED_PATH = "/admin/session-expired";

/**
 * The real session check for the admin console (Next "Data Access Layer"). The proxy only looks
 * for the cookie; this asks the API with getAdminSession (TB-8 Q17), which answers 200 only for an
 * ADMIN session that completed MFA (G3-D16-02) and checks revocation and expiry on every request
 * (ADR-0004). Memoized for one render pass.
 *
 * 401 (no live session) and 403 (not ADMIN, or MFA not completed) both go to the session-expired
 * route, which asks the API again before it deletes the cookie (#82, #86).
 */
export const verifyAdminSession = cache(async (): Promise<void> => {
  const token = await readSessionToken();
  if (!token) {
    redirect(SIGN_IN_PATH);
  }
  const api = await adminApi({ token });
  const { response } = await api.GET("/admin/session");
  if (response.status === 401 || response.status === 403) {
    redirect(SESSION_EXPIRED_PATH);
  }
  if (!response.ok) {
    throw new Error(`Session check failed with HTTP ${response.status}`);
  }
});
