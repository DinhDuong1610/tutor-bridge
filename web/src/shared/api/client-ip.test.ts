import { describe, expect, it } from "vitest";
import { clientIpFrom } from "./client-ip";

describe("clientIpFrom (#252)", () => {
  it("takes the last address, the one the closest hop wrote", () => {
    expect(clientIpFrom("198.51.100.7", 0)).toBe("198.51.100.7");
    // A browser that sends its own X-Forwarded-For only adds to the left of what the proxy appends.
    expect(clientIpFrom("1.2.3.4, 198.51.100.7", 0)).toBe("198.51.100.7");
    expect(clientIpFrom(" 2001:db8::7 ", 0)).toBe("2001:db8::7");
  });

  it("skips the trusted proxies counted from the right", () => {
    expect(clientIpFrom("1.2.3.4, 198.51.100.7, 10.0.0.2", 1)).toBe("198.51.100.7");
  });

  it("sends nothing when there is no address to take", () => {
    expect(clientIpFrom(null, 0)).toBeNull();
    expect(clientIpFrom("", 0)).toBeNull();
    expect(clientIpFrom("198.51.100.7", 1)).toBeNull();
  });
});
