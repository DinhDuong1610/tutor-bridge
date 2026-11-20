import { NextResponse, type NextRequest } from "next/server";
import { adminApi } from "@/shared/api/server";
import { SESSION_COOKIE } from "@/shared/session/cookies";

/** Why the console sent the admin back to sign-in; AD01 shows the matching notice (#68). */
const SIGN_IN_REASONS = { 401: "expired", 403: "forbidden" } as const;

/**
 * Where the console goes when the API refuses the session: forget the cookie, then sign in again
 * with a notice. A Server Component cannot delete cookies, a route handler can.
 *
 * Any page can link here, so the cookie is deleted only when the API itself says this session is
 * over (Đính, #86, 21/11/2026): 401 when it is not live, 403 when it is not an ADMIN session that
 * completed MFA (Đính, #82). A live session goes back to the console untouched. A link from another
 * site carries no cookie (SameSite=Strict), so nothing is deleted then either.
 */
export async function GET(request: NextRequest) {
  const token = request.cookies.get(SESSION_COOKIE)?.value;
  if (!token) {
    return toSignIn(request, SIGN_IN_REASONS[401]);
  }
  const api = await adminApi({ token });
  const { response } = await api.GET("/admin/session");
  if (response.ok) {
    return NextResponse.redirect(new URL("/admin", request.url));
  }
  if (response.status === 401 || response.status === 403) {
    const signIn = toSignIn(request, SIGN_IN_REASONS[response.status]);
    signIn.cookies.delete({ name: SESSION_COOKIE, path: "/" });
    return signIn;
  }
  // 429 or 5xx says nothing about the session: keep the cookie and let the console show the error.
  throw new Error(`Session check failed with HTTP ${response.status}`);
}

function toSignIn(request: NextRequest, reason: string) {
  const signIn = new URL("/admin/sign-in", request.url);
  signIn.searchParams.set("reason", reason);
  return NextResponse.redirect(signIn);
}
