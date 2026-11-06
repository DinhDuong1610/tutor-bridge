import { createApiClient } from "@tutorbridge/api-client";

// Expo inlines EXPO_PUBLIC_ variables at build time, so this must never hold a secret.
const baseUrl = process.env.EXPO_PUBLIC_API_URL ?? "http://10.0.2.2:8080/api/v1";

export const api = createApiClient({ baseUrl });
