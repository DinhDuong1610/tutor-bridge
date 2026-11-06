import type { Role, Session } from "@/shared/session/storage";

// eslint-disable-next-line @typescript-eslint/no-require-imports -- reaches the Jest mock in __mocks__
const store: Map<string, string> = require("expo-secure-store").__store;

/** A session as login would return it, valid for a day. Fake values only. */
export function fakeSession(role: Role, overrides: Partial<Session> = {}): Session {
  const inADay = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
  return {
    sessionId: "00000000-0000-4000-8000-000000000001",
    accessToken: "test-access-token-not-a-secret",
    role,
    idleExpiresAt: inADay,
    absoluteExpiresAt: inADay,
    ...overrides,
  };
}

export function storeSession(session: Session | string) {
  store.set("tb.session", typeof session === "string" ? session : JSON.stringify(session));
}

export function storedSession(): string | undefined {
  return store.get("tb.session");
}

export function clearStore() {
  store.clear();
}
