import { describe, expect, it } from "vitest";
import { adminApi } from "./server";

async function capture(token: string | null, send: (api: Awaited<ReturnType<typeof adminApi>>) => Promise<unknown>) {
  const requests: Request[] = [];
  const api = await adminApi({
    token,
    fetch: async (request) => {
      requests.push(request);
      return new Response(JSON.stringify({ items: [], nextCursor: null }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    },
  });
  await send(api);
  return requests[0];
}

describe("adminApi", () => {
  it("sends the session cookie on reads, without CSRF", async () => {
    const request = await capture("session-token", (api) => api.GET("/me/sessions", {}));

    expect(request.headers.get("Cookie")).toBe("tb_session=session-token");
    expect(request.headers.has("X-CSRF-Token")).toBe(false);
    expect(request.headers.has("Authorization")).toBe(false);
  });

  it("adds a matching CSRF pair and the web origin to writes", async () => {
    const request = await capture("session-token", (api) =>
      api.POST("/students", {
        params: { header: { "Idempotency-Key": "k".repeat(16) } },
        body: { displayName: "Minh", grade: 8, shortGoal: "" },
      }),
    );

    const csrf = request.headers.get("X-CSRF-Token");
    expect(csrf).toMatch(/^[0-9a-f-]{36}$/);
    expect(request.headers.get("Cookie")).toBe(`tb_session=session-token; tb_csrf=${csrf}`);
    expect(request.headers.get("Origin")).toBe("http://localhost:3000");
  });

  it("sends no session cookie when signed out", async () => {
    const request = await capture(null, (api) => api.GET("/me/sessions", {}));

    expect(request.headers.has("Cookie")).toBe(false);
  });
});
