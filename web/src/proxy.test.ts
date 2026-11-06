import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { config, proxy } from "./proxy";

function request(path: string, cookie?: string) {
  return new NextRequest(`http://localhost:3000${path}`, cookie ? { headers: { cookie } } : undefined);
}

describe("proxy", () => {
  it("sends the console to sign-in without a session cookie, remembering the page", () => {
    const response = proxy(request("/admin/verifications?page=2"));

    expect(response.status).toBe(307);
    const location = new URL(response.headers.get("location")!);
    expect(location.pathname).toBe("/admin/sign-in");
    expect(location.searchParams.get("next")).toBe("/admin/verifications?page=2");
  });

  it("lets a request with a session cookie through to the real check", () => {
    const response = proxy(request("/admin/verifications", "tb_session=abc"));

    expect(response.headers.get("location")).toBeNull();
    expect(response.headers.get("x-middleware-next")).toBe("1");
  });

  it("never redirects the sign-in and session-expired pages", () => {
    for (const path of ["/admin/sign-in", "/admin/session-expired"]) {
      expect(proxy(request(path)).headers.get("location")).toBeNull();
    }
  });

  it("only runs on the admin area", () => {
    expect(config.matcher).toEqual(["/admin/:path*"]);
  });
});
