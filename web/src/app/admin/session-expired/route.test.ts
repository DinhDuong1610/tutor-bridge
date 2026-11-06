import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { GET } from "./route";

describe("session-expired route", () => {
  it("deletes the session cookie and returns to sign-in with a reason", () => {
    const response = GET(new NextRequest("http://localhost:3000/admin/session-expired"));

    const location = new URL(response.headers.get("location")!);
    expect(location.pathname).toBe("/admin/sign-in");
    expect(location.searchParams.get("reason")).toBe("expired");
    expect(response.headers.get("set-cookie")).toMatch(/tb_session=;.*Expires=Thu, 01 Jan 1970/i);
  });
});
