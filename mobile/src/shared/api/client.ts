import { createApiClient, type ApiClient } from "@tutorbridge/api-client";

// Expo inlines EXPO_PUBLIC_ variables at build time, so this must never hold a secret.
const DEFAULT_BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? "http://10.0.2.2:8080/api/v1";

/** Returns the session token to send, or null when signed out. The session module (M3) provides it. */
export type TokenSource = () => string | null | Promise<string | null>;

let currentTokenSource: TokenSource = () => null;

/** Called once by the session module. Until then requests go out without a session. */
export function setTokenSource(source: TokenSource) {
  currentTokenSource = source;
}

/** A contract-typed client that sends `Authorization: Bearer` when there is a session (ADR-0004). */
export function createClient(options: {
  baseUrl?: string;
  fetch?: (request: Request) => Promise<Response>;
  token?: TokenSource;
} = {}): ApiClient {
  const client = createApiClient({ baseUrl: options.baseUrl ?? DEFAULT_BASE_URL, fetch: options.fetch });
  const token = options.token ?? (() => currentTokenSource());
  client.use({
    async onRequest({ request }) {
      const value = await token();
      if (value) {
        request.headers.set("Authorization", `Bearer ${value}`);
      }
      return request;
    },
  });
  return client;
}

/** The app's client. Feature code calls it through `call` (errors.ts), never `fetch` directly. */
export const api = createClient();
