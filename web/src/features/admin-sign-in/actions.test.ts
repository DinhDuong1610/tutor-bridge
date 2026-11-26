import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type Jar = Map<string, { value: string; options: Record<string, unknown> }>;
const jar: Jar = new Map();

vi.mock("next/headers", () => ({
  cookies: async () => ({
    get: (name: string) => (jar.has(name) ? { name, value: jar.get(name)!.value } : undefined),
    set: (name: string, value: string, options: Record<string, unknown>) => jar.set(name, { value, options }),
    delete: ({ name }: { name: string }) => jar.delete(name),
  }),
}));

vi.mock("next/navigation", () => ({
  redirect: (url: string) => {
    throw new Error(`REDIRECT ${url}`);
  },
}));

const { signIn, verifyCode } = await import("./actions");

const CHALLENGE = "c".repeat(43);
const TOKEN = "t".repeat(43);

function problem(status: number, code: string, fields?: { path: string; message: string }[]) {
  return { type: `urn:tutorbridge:problem:${code}`, title: "x", status, code, detail: "x", traceId: "0".repeat(32), fields };
}

/** The API answers once with this status and body; the request is kept for the checks. */
function api(status: number, body: unknown) {
  const fetch = vi.fn(async (request: Request) => {
    void request;
    return new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": status < 400 ? "application/json" : "application/problem+json" },
    });
  });
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

const login = { email: "xacminh01@example.com", password: "mot-mat-khau-du-dai-15" };

describe("signIn (loginAdmin)", () => {
  beforeEach(() => jar.clear());
  afterEach(() => vi.unstubAllGlobals());

  it("keeps the challenge in an HttpOnly cookie for the admin paths and tells the page only the step", async () => {
    const fetch = api(200, { mfaRequired: true, challengeToken: CHALLENGE, expiresAt: "2026-11-26T10:05:00Z" });

    const step = await signIn(login);

    expect(step).toEqual({ state: "mfa" });
    expect(JSON.stringify(step)).not.toContain(CHALLENGE);
    expect(jar.get("tb_admin_challenge")).toEqual({
      value: CHALLENGE,
      options: { httpOnly: true, secure: true, sameSite: "strict", path: "/admin", expires: new Date("2026-11-26T10:05:00Z") },
    });
    const request = fetch.mock.calls[0][0];
    expect(new URL(request.url).pathname).toBe("/api/v1/auth/admin-login");
    expect(await request.json()).toEqual(login);
    expect(request.headers.get("Cookie") ?? "").not.toContain("tb_session");
  });

  it.each([
    [401, problem(401, "AUTHENTICATION_FAILED"), { state: "wrong-credentials" }],
    [403, problem(403, "ACCOUNT_LOCKED"), { state: "locked" }],
    [429, problem(429, "RATE_LIMITED"), { state: "delayed" }],
    [500, problem(500, "INTERNAL_ERROR"), { state: "failed" }],
    [422, problem(422, "VALIDATION_FAILED", [{ path: "email", message: "Email chưa đúng." }]), { state: "invalid", fields: { email: "Email chưa đúng." } }],
  ])("answers %i as the matching AD01 state and keeps no challenge", async (status, body, expected) => {
    api(status, body);

    expect(await signIn(login)).toEqual(expected);
    expect(jar.has("tb_admin_challenge")).toBe(false);
  });

  it("treats a lost connection as a failure to retry, not as a wrong password", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new TypeError("fetch failed"))));

    expect(await signIn(login)).toEqual({ state: "failed" });
  });

  it("does not call the API with an empty form", async () => {
    const fetch = api(200, {});

    expect(await signIn({ email: "", password: "" })).toEqual({ state: "invalid", fields: {} });
    expect(fetch).not.toHaveBeenCalled();
  });
});

describe("verifyCode (completeAdminMfa)", () => {
  beforeEach(() => {
    jar.clear();
    jar.set("tb_admin_challenge", { value: CHALLENGE, options: {} });
  });
  afterEach(() => vi.unstubAllGlobals());

  const session = {
    sessionId: "5f0c2b9e-2a51-4a8e-9a43-6d1f0c2b9e10",
    accessToken: TOKEN,
    role: "ADMIN",
    idleExpiresAt: "2026-11-26T10:30:00Z",
    absoluteExpiresAt: "2026-11-26T22:00:00Z",
  };

  it("sets the session cookie, drops the challenge and goes only to a safe next path", async () => {
    const fetch = api(200, session);

    await expect(verifyCode({ code: "482913" }, "/admin/verifications?status=PENDING")).rejects.toThrow(
      "REDIRECT /admin/verifications?status=PENDING",
    );
    expect(jar.get("tb_session")).toEqual({
      value: TOKEN,
      options: { httpOnly: true, secure: true, sameSite: "strict", path: "/", expires: new Date("2026-11-26T22:00:00Z") },
    });
    expect(jar.has("tb_admin_challenge")).toBe(false);
    expect(await fetch.mock.calls[0][0].json()).toEqual({ challengeToken: CHALLENGE, code: "482913" });
  });

  it("never sends the admin to another site after sign-in (#86)", async () => {
    api(200, session);

    await expect(verifyCode({ code: "482913" }, "//evil.example/admin")).rejects.toThrow("REDIRECT /admin");
  });

  it("keeps the challenge on a wrong code, so the admin can type a new one", async () => {
    api(401, problem(401, "MFA_CODE_INVALID"));

    expect(await verifyCode({ code: "482910" }, null)).toEqual({ state: "wrong-code" });
    expect(jar.get("tb_admin_challenge")?.value).toBe(CHALLENGE);
    expect(jar.has("tb_session")).toBe(false);
  });

  it("goes back to the password step when the challenge ended, and forgets it", async () => {
    api(401, problem(401, "MFA_CHALLENGE_EXPIRED"));

    expect(await verifyCode({ code: "482913" }, null)).toEqual({ state: "challenge-ended" });
    expect(jar.has("tb_admin_challenge")).toBe(false);
  });

  it("goes back to the password step without calling the API when there is no challenge", async () => {
    jar.clear();
    const fetch = api(200, session);

    expect(await verifyCode({ code: "482913" }, null)).toEqual({ state: "challenge-ended" });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("answers a delay and a server error like step 1, and sets no session", async () => {
    api(429, problem(429, "RATE_LIMITED"));
    expect(await verifyCode({ code: "482913" }, null)).toEqual({ state: "delayed" });
    api(503, problem(503, "SERVICE_BUSY"));
    expect(await verifyCode({ code: "482913" }, null)).toEqual({ state: "failed" });
    expect(jar.has("tb_session")).toBe(false);
  });
});
