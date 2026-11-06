import { NextResponse, type NextRequest } from "next/server";

const SESSION_COOKIE = "tb_session";
const OPEN_ADMIN_PATHS = ["/admin/sign-in", "/admin/session-expired"];

/**
 * Optimistic check only (Next guide "Optimistic checks with Proxy"): no cookie, no admin console.
 * It never calls the API; the console layout verifies the session for real.
 */
export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  if (OPEN_ADMIN_PATHS.some((open) => pathname === open || pathname.startsWith(`${open}/`))) {
    return NextResponse.next();
  }
  if (!request.cookies.get(SESSION_COOKIE)?.value) {
    const signIn = new URL("/admin/sign-in", request.url);
    // Only a path on this site, so the sign-in page cannot be used to send people elsewhere.
    signIn.searchParams.set("next", `${pathname}${search}`);
    return NextResponse.redirect(signIn);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/admin/:path*"],
};
