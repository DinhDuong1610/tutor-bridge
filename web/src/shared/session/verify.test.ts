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

function answer(status: number) {
  const fetch = vi.fn(async (request: Request) => {
    void request;
    return new Response(JSON.stringify(status === 200 ? { items: [], nextCursor: null } : {}), {
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

  it("asks the API and accepts a live session", async () => {
    cookieJar.set("tb_session", "live-token");
    const fetch = answer(200);

    await expect(verifyAdminSession()).resolves.toBeUndefined();
    const request = fetch.mock.calls[0][0];
    expect(new URL(request.url).pathname).toBe("/api/v1/me/sessions");
    expect(request.headers.get("Cookie")).toBe("tb_session=live-token");
  });

  it("clears an ended session through the session-expired route", async () => {
    cookieJar.set("tb_session", "revoked-token");
    answer(401);

    await expect(verifyAdminSession()).rejects.toThrow("REDIRECT /admin/session-expired");
  });

  it("does not treat an API failure as signed in", async () => {
    cookieJar.set("tb_session", "live-token");
    answer(503);

    await expect(verifyAdminSession()).rejects.toThrow("HTTP 503");
  });
});
