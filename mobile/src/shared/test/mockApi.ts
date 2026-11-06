import type { Problem } from "@tutorbridge/shared";

type Handler = (request: Request) => Response | Promise<Response>;

/**
 * Replaces fetch for screen tests. Routes use the contract paths without `/api/v1`, with
 * `{param}` for path parameters:
 *
 * <pre>
 * const api = mockApi({
 *   "GET /students": () => json(200, { items: [], nextCursor: null }),
 *   "POST /students": () => json(201, student),
 * });
 * afterEach(() => api.restore());
 * </pre>
 *
 * A request no route matches fails the test, so a screen cannot call an API the test did not expect.
 */
export function mockApi(routes: Record<string, Handler>) {
  const compiled = Object.entries(routes).map(([key, handler]) => {
    const [method, template] = key.split(" ");
    const pattern = new RegExp(`^${template.replace(/\{[^/]+}/g, "[^/]+")}$`);
    return { method, pattern, handler };
  });
  const requests: Request[] = [];
  const unexpected: string[] = [];
  const original = globalThis.fetch;

  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const request = input instanceof Request ? input : new Request(input, init);
    requests.push(request.clone());
    const path = new URL(request.url).pathname.replace(/^\/api\/v1/, "");
    const route = compiled.find((r) => r.method === request.method && r.pattern.test(path));
    if (!route) {
      unexpected.push(`${request.method} ${path}`);
      throw new Error(`Unexpected request in test: ${request.method} ${path}`);
    }
    return route.handler(request);
  }) as typeof fetch;

  return {
    /** Every request the screen sent, in order. */
    requests,
    /** Requests without a route; a passing test leaves this empty. */
    unexpected,
    restore() {
      globalThis.fetch = original;
    },
  };
}

export function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

/** A Problem answer with the six fields the contract requires. */
export function problem(status: number, code: string, title: string, fields?: Problem["fields"]) {
  const body: Problem = { type: `urn:tutorbridge:problem:${code}`, title, status, code, detail: title, traceId: "0123456789abcdef0123456789abcdef" };
  if (fields) body.fields = fields;
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/problem+json" } });
}
