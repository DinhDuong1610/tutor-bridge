import { describe, expect, it } from "vitest";
import { clientIpFrom, clientIpHops } from "./client-ip";

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

  it("reads TB_CLIENT_IP_HOPS as a whole number of 0 or more, and refuses anything else", () => {
    expect(clientIpHops(undefined)).toBe(0);
    expect(clientIpHops("")).toBe(0);
    expect(clientIpHops("2")).toBe(2);
    // A typo must stop the server, not quietly send no address and count every admin as one again.
    for (const bad of ["one", "-1", "1.5", "1 ", " 1", "0x1"]) {
      expect(() => clientIpHops(bad)).toThrow("TB_CLIENT_IP_HOPS");
    }
  });
});
