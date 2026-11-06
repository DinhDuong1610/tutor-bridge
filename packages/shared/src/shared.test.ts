import { afterEach, describe, expect, it, vi } from "vitest";
import {
  fieldErrors,
  formatDate,
  formatDateTime,
  formatTime,
  formatVnd,
  isProblem,
  isValidIdempotencyKey,
  newIdempotencyKey,
  problemKind,
  type Problem,
} from "./index";

const problem: Problem = {
  type: "urn:tutorbridge:problem:VALIDATION_FAILED",
  title: "Dữ liệu chưa hợp lệ.",
  status: 422,
  code: "VALIDATION_FAILED",
  detail: "Dữ liệu chưa hợp lệ.",
  traceId: "0123456789abcdef0123456789abcdef",
  fields: [
    { path: "displayName", message: "Nhập tên hiển thị." },
    { path: "displayName", message: "Tên quá dài." },
    { path: "grade", message: "Chỉ hỗ trợ lớp 8." },
  ],
};

describe("problem", () => {
  it("recognises a problem body and rejects other values", () => {
    expect(isProblem(problem)).toBe(true);
    expect(isProblem({ ...problem, traceId: undefined })).toBe(false);
    expect(isProblem("error")).toBe(false);
    expect(isProblem(null)).toBe(false);
  });

  it.each([
    [401, "session-expired"],
    [403, "forbidden"],
    [404, "not-found"],
    [409, "conflict"],
    [412, "stale"],
    [400, "invalid"],
    [422, "invalid"],
    [428, "precondition-required"],
    [429, "rate-limited"],
    [500, "server"],
    [503, "server"],
  ] as const)("maps status %i to %s", (status, kind) => {
    expect(problemKind({ status })).toBe(kind);
  });

  it("keys field errors by path and keeps the first message", () => {
    expect(fieldErrors(problem)).toEqual({
      displayName: "Nhập tên hiển thị.",
      grade: "Chỉ hỗ trợ lớp 8.",
    });
    expect(fieldErrors({})).toEqual({});
  });
});

describe("idempotency key", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("creates distinct keys within the contract length", () => {
    const keys = new Set(Array.from({ length: 50 }, () => newIdempotencyKey()));
    expect(keys.size).toBe(50);
    for (const key of keys) {
      expect(key).toMatch(/^[0-9a-f]{32}$/);
      expect(isValidIdempotencyKey(key)).toBe(true);
    }
  });

  it("uses the random source it is given", () => {
    const key = newIdempotencyKey((bytes) => bytes.fill(0xab));
    expect(key).toBe("ab".repeat(16));
  });

  it("refuses to create a key without a secure random source", () => {
    vi.stubGlobal("crypto", undefined);
    expect(() => newIdempotencyKey()).toThrow(/secure random/);
  });

  it("checks the 16 to 128 character limit", () => {
    expect(isValidIdempotencyKey("a".repeat(15))).toBe(false);
    expect(isValidIdempotencyKey("a".repeat(16))).toBe(true);
    expect(isValidIdempotencyKey("a".repeat(128))).toBe(true);
    expect(isValidIdempotencyKey("a".repeat(129))).toBe(false);
  });
});

describe("format", () => {
  it("shows UTC instants in Vietnam time, 24-hour", () => {
    expect(formatDateTime("2026-11-06T13:05:00Z")).toBe("20:05 06/11/2026");
    expect(formatTime("2026-11-06T13:05:00Z")).toBe("20:05");
    expect(formatDate(new Date("2026-11-06T13:05:00Z"))).toBe("06/11/2026");
  });

  it("moves to the next day after 17:00 UTC", () => {
    expect(formatDateTime("2026-12-31T17:00:00Z")).toBe("00:00 01/01/2027");
    expect(formatDateTime("2026-12-31T16:59:00+00:00")).toBe("23:59 31/12/2026");
  });

  it("rejects an invalid instant", () => {
    expect(() => formatDate("not a date")).toThrow(RangeError);
  });

  it("formats whole VND with dot grouping", () => {
    expect(formatVnd(0)).toBe("0 ₫");
    expect(formatVnd(150000)).toBe("150.000 ₫");
    expect(formatVnd(1234567)).toBe("1.234.567 ₫");
    expect(formatVnd(-5000)).toBe("-5.000 ₫");
  });

  it("rejects amounts that are not whole VND", () => {
    expect(() => formatVnd(1500.5)).toThrow(RangeError);
    expect(() => formatVnd(Number.NaN)).toThrow(RangeError);
  });
});
