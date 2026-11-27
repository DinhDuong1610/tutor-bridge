import { vi } from "vitest";

// connection() throws outside a Next request; server code is tested in plain Node, where every call is a
// request already (#254). The rest of next/server stays real.
vi.mock("next/server", async (original) => ({
  ...(await original<typeof import("next/server")>()),
  connection: async () => {},
}));
