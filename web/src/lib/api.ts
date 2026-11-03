import "server-only";
import { createApiClient } from "@tutorbridge/api-client";

// Server side only: the admin session cookie must never be readable from browser code.
export const api = createApiClient({
  baseUrl: process.env.TB_API_URL ?? "http://127.0.0.1:8080/api/v1",
});
