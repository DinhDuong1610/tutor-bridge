import { NextRequest } from "next/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { GET } from "./route";

const ROUTE = "http://localhost:3000/admin/session-expired";

/** The API's answer to getAdminSession; returns the fetch mock to inspect what was sent. */
function api(status: number) {
  const fetch = vi.fn(async (request: Request) => {
    void request;
    return new Response(status === 200 ? JSON.stringify({}) : JSON.stringify({}), {
      status,
      headers: { "Content-Type": status === 200 ? "application/json" : "application/problem+json" },
    });
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

function withCookie(token: string) {
  return new NextRequest(ROUTE, { headers: { Cookie: `tb_session=${token}` } });
}

const deletesCookie = /tb_session=;.*Expires=Thu, 01 Jan 1970/i;

describe("session-expired route", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("deletes a session the API no longer accepts and returns to sign-in as expired", async () => {
    const fetch = api(401);

    const response = await GET(withCookie("revoked-token"));

    const location = new URL(response.headers.get("location")!);
    expect(location.pathname).toBe("/admin/sign-in");
    expect(location.searchParams.get("reason")).toBe("expired");
    expect(response.headers.get("set-cookie")).toMatch(deletesCookie);
    const request = fetch.mock.calls[0][0];
    expect(new URL(request.url).pathname).toBe("/api/v1/admin/session");
    expect(request.headers.get("Cookie")).toBe("tb_session=revoked-token");
  });

  it("#82: deletes a session that is not an admin one with MFA and says access is gone", async () => {
    api(403);

    const response = await GET(withCookie("parent-token"));

    const location = new URL(response.headers.get("location")!);
    expect(location.pathname).toBe("/admin/sign-in");
    expect(location.searchParams.get("reason")).toBe("forbidden");
    expect(response.headers.get("set-cookie")).toMatch(deletesCookie);
  });

  it("#86: a link to this route cannot sign out an admin whose session is live", async () => {
    api(200);

    const response = await GET(withCookie("live-token"));

    expect(new URL(response.headers.get("location")!).pathname).toBe("/admin");
    expect(response.headers.get("set-cookie")).toBeNull();
  });

  it("#86: a request without the cookie, as from another site, deletes nothing and asks nothing", async () => {
    const fetch = api(401);

    const response = await GET(new NextRequest(ROUTE));

    const location = new URL(response.headers.get("location")!);
    expect(location.pathname).toBe("/admin/sign-in");
    expect(location.searchParams.get("reason")).toBe("expired");
    expect(response.headers.get("set-cookie")).toBeNull();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("keeps the cookie when the API fails, since that says nothing about the session", async () => {
    api(503);

    await expect(GET(withCookie("live-token"))).rejects.toThrow("HTTP 503");
  });
});
