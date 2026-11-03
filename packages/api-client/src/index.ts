import createClient, { type Client, type ClientOptions } from "openapi-fetch";
import type { components, operations, paths } from "./schema";

export type { components, operations, paths };

export type Problem = components["schemas"]["Problem"];
export type ApiClient = Client<paths>;

/** `baseUrl` includes the `/api/v1` prefix, as in the servers list of contracts/openapi.yaml. */
export function createApiClient(options: ClientOptions & { baseUrl: string }): ApiClient {
  return createClient<paths>(options);
}
