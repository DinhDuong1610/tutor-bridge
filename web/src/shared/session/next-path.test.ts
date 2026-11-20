import { describe, expect, it } from "vitest";
import { CONSOLE_HOME, safeNextPath } from "./next-path";

describe("safeNextPath (#86)", () => {
  it("keeps a console path with its query and fragment", () => {
    expect(safeNextPath("/admin")).toBe("/admin");
    expect(safeNextPath("/admin/verifications")).toBe("/admin/verifications");
    expect(safeNextPath("/admin/verifications?status=PENDING#top")).toBe("/admin/verifications?status=PENDING#top");
  });

  it.each([
    ["missing", null],
    ["empty", ""],
    ["protocol-relative", "//example.com"],
    ["protocol-relative to a console path", "//example.com/admin/verifications"],
    ["backslash", "/\\example.com"],
    ["backslash after a slash", "/\\/example.com"],
    ["tab inside the slashes", "/\t/example.com"],
    ["newline inside the slashes", "/\n/example.com"],
    ["absolute URL", "https://example.com/admin"],
    ["script URL", "javascript:alert(1)"],
    ["relative path", "admin/verifications"],
    ["path outside the console", "/huong-dan"],
    ["console look-alike", "/administrator"],
    ["dot segments out of the console", "/admin/../huong-dan"],
    ["sign-in itself", "/admin/sign-in?next=/admin"],
    ["session-expired itself", "/admin/session-expired"],
  ])("sends a %s next to the console home", (_case, raw) => {
    expect(safeNextPath(raw)).toBe(CONSOLE_HOME);
  });
});
