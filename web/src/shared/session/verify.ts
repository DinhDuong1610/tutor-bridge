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
 * for the cookie; this asks the API, which checks revocation and expiry on every request (ADR-0004).
 * Memoized for one render pass.
 */
export const verifyAdminSession = cache(async (): Promise<void> => {
  const token = await readSessionToken();
  if (!token) {
    redirect(SIGN_IN_PATH);
  }
  const api = await adminApi({ token });
  const { response } = await api.GET("/me/sessions", { params: { query: { limit: 1 } } });
  if (response.status === 401) {
    redirect(SESSION_EXPIRED_PATH);
  }
  if (!response.ok) {
    throw new Error(`Session check failed with HTTP ${response.status}`);
  }
});
