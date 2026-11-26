import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const cookieJar = new Map<string, string>();

vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) => (cookieJar.has(name) ? { name, value: cookieJar.get(name) } : undefined),
  }),
}));

vi.mock("next/navigation", () => ({
  redirect: (url: string) => {
    throw new Error(`REDIRECT ${url}`);
  },
}));

const { verifyAdminSession } = await import("./verify");

const ADMIN_SESSION = {
  sessionId: "5f0c2b9e-2a51-4a8e-9a43-6d1f0c2b9e10",
  role: "ADMIN",
  mfaVerifiedAt: "2026-11-21T03:00:00Z",
  idleExpiresAt: "2026-11-21T03:30:00Z",
  absoluteExpiresAt: "2026-11-21T15:00:00Z",
};

function answer(status: number) {
  const fetch = vi.fn(async (request: Request) => {
    void request;
    return new Response(JSON.stringify(status === 200 ? ADMIN_SESSION : {}), {
      status,
      headers: { "Content-Type": status === 200 ? "application/json" : "application/problem+json" },
    });
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

describe("verifyAdminSession", () => {
  beforeEach(() => cookieJar.clear());
  afterEach(() => vi.unstubAllGlobals());

  it("sends a visitor without a cookie to sign-in without calling the API", async () => {
    const fetch = answer(200);

    await expect(verifyAdminSession()).rejects.toThrow("REDIRECT /admin/sign-in");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("asks getAdminSession and accepts an admin session that completed MFA", async () => {
    cookieJar.set("tb_session", "live-token");
    const fetch = answer(200);

    await expect(verifyAdminSession()).resolves.toBeUndefined();
    const request = fetch.mock.calls[0][0];
    expect(request.method).toBe("GET");
    expect(new URL(request.url).pathname).toBe("/api/v1/admin/session");
    expect(request.headers.get("Cookie")).toBe("tb_session=live-token");
  });

  it("clears an ended session through the session-expired route", async () => {
    cookieJar.set("tb_session", "revoked-token");
    answer(401);

    await expect(verifyAdminSession()).rejects.toThrow("REDIRECT /admin/session-expired");
  });

  it("#82: sends a parent, a tutor, or an admin without MFA through the session-expired route", async () => {
    // getAdminSession answers 403 to all three (TB-8 Q17); none of them may see the console shell.
    cookieJar.set("tb_session", "parent-or-tutor-or-no-mfa-token");
    answer(403);

    await expect(verifyAdminSession()).rejects.toThrow("REDIRECT /admin/session-expired");
  });

  it("does not treat an API failure as signed in", async () => {
    cookieJar.set("tb_session", "live-token");
    answer(503);

    await expect(verifyAdminSession()).rejects.toThrow("HTTP 503");
  });
});
