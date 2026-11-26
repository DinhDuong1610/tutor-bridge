import "server-only";
import { createApiClient, type ApiClient } from "@tutorbridge/api-client";
import { headers } from "next/headers";
import { readSessionToken, SESSION_COOKIE } from "@/shared/session/cookies";
import { clientIpFrom, clientIpHops } from "./client-ip";

const API_URL = process.env.TB_API_URL ?? "http://127.0.0.1:8080/api/v1";
/** This web's origin. The API accepts cookie writes only from origins in tutorbridge.security.admin-origins. */
const WEB_ORIGIN = process.env.TB_WEB_ORIGIN ?? "http://localhost:3000";

/** Trusted proxies in front of the one that saw the browser (#252, client-ip.ts). */
const CLIENT_IP_HOPS = clientIpHops(process.env.TB_CLIENT_IP_HOPS);
const CLIENT_IP_HEADER = "X-TB-Client-IP";

const CSRF_COOKIE = "tb_csrf";
const CSRF_HEADER = "X-CSRF-Token";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

/**
 * A contract-typed client for one server request, carrying the admin session cookie (ADR-0010 mục 3).
 *
 * Writes also carry the API's CSRF pair and Origin. This server is the trusted caller here: the
 * browser-to-Next hop is protected by Next's Server Action origin check and the SameSite=Strict
 * cookie, so a fresh random token for both halves of the pair is enough.
 */
export async function adminApi(
  options: { fetch?: (request: Request) => Promise<Response>; token?: string | null } = {},
): Promise<ApiClient> {
  const token = options.token !== undefined ? options.token : await readSessionToken();
  // The API counts sign-in failures by this address, and believes it only from this server (#252).
  const clientIp = clientIpFrom((await headers()).get("x-forwarded-for"), CLIENT_IP_HOPS);
  const client = createApiClient({ baseUrl: API_URL, fetch: options.fetch ?? ((request) => globalThis.fetch(request)) });
  client.use({
    onRequest({ request }) {
      const cookies: string[] = [];
      if (clientIp) {
        request.headers.set(CLIENT_IP_HEADER, clientIp);
      }
      if (token) {
        cookies.push(`${SESSION_COOKIE}=${token}`);
      }
      if (!SAFE_METHODS.has(request.method)) {
        const csrf = crypto.randomUUID();
        cookies.push(`${CSRF_COOKIE}=${csrf}`);
        request.headers.set(CSRF_HEADER, csrf);
        request.headers.set("Origin", WEB_ORIGIN);
      }
      if (cookies.length > 0) {
        request.headers.set("Cookie", cookies.join("; "));
      }
      return request;
    },
  });
  return client;
}
