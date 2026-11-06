import { describe, expect, it, vi } from "vitest";

vi.mock("next/navigation", () => ({
  redirect: (url: string) => {
    throw new Error(`REDIRECT ${url}`);
  },
}));

const { load, runCommand } = await import("./result");

const PROBLEM = {
  type: "urn:tutorbridge:problem:VALIDATION_FAILED",
  title: "Dữ liệu chưa hợp lệ.",
  status: 422,
  code: "VALIDATION_FAILED",
  detail: "Dữ liệu chưa hợp lệ.",
  traceId: "0123456789abcdef0123456789abcdef",
  fields: [{ path: "reason", message: "Nhập lý do." }],
};

function answer(status: number, error?: unknown, data?: unknown) {
  return Promise.resolve({ data, error, response: new Response(null, { status }) });
}

describe("load", () => {
  it("returns the data of a successful read", async () => {
    expect(await load(answer(200, undefined, { items: [] }))).toEqual({ status: "ok", data: { items: [] } });
  });

  it.each([
    [403, "forbidden"],
    [404, "not-found"],
    [429, "rate-limited"],
    [500, "error"],
  ])("turns %i into the %s state", async (status, state) => {
    expect((await load(answer(status, { ...PROBLEM, status }))).status).toBe(state);
  });

  it("treats a lost connection as an error state", async () => {
    expect(await load(Promise.reject(new TypeError("fetch failed")))).toEqual({ status: "error" });
  });

  it("ends the session on a 401", async () => {
    await expect(load(answer(401, { ...PROBLEM, status: 401 }))).rejects.toThrow("REDIRECT /admin/session-expired");
  });
});

describe("runCommand", () => {
  it("confirms a successful write", async () => {
    expect(await runCommand(answer(201, undefined, { id: "x" }))).toEqual({ state: "confirmed", data: { id: "x" } });
  });

  it("returns the problem title and field messages of a failed write", async () => {
    expect(await runCommand(answer(422, PROBLEM))).toEqual({
      state: "failed",
      title: "Dữ liệu chưa hợp lệ.",
      fields: { reason: "Nhập lý do." },
    });
  });

  it("treats a lost connection or a gateway page as an unknown outcome", async () => {
    expect(await runCommand(Promise.reject(new TypeError("fetch failed")))).toEqual({ state: "unknown" });
    expect(await runCommand(answer(502, "<html>Bad gateway</html>"))).toEqual({ state: "unknown" });
  });

  it("ends the session on a 401", async () => {
    await expect(runCommand(answer(401, { ...PROBLEM, status: 401 }))).rejects.toThrow("REDIRECT /admin/session-expired");
  });
});
