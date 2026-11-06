import { NextResponse, type NextRequest } from "next/server";

/**
 * Where the console goes when the API answers 401: forget the cookie, then sign in again with a
 * notice. A Server Component cannot delete cookies, a route handler can.
 */
export function GET(request: NextRequest) {
  const signIn = new URL("/admin/sign-in", request.url);
  signIn.searchParams.set("reason", "expired");
  const response = NextResponse.redirect(signIn);
  response.cookies.delete({ name: "tb_session", path: "/" });
  return response;
}
