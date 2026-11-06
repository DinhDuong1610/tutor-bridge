import { QueryClient } from "@tanstack/react-query";
import { ApiError, UnknownOutcomeError } from "./errors";

const MAX_READ_RETRIES = 2;

/**
 * Reads retry only when trying again can help: a lost connection or a server error. A 4xx answer
 * will not change by asking again.
 */
export function shouldRetryRead(failureCount: number, error: unknown): boolean {
  if (failureCount >= MAX_READ_RETRIES) {
    return false;
  }
  if (error instanceof UnknownOutcomeError) {
    return true;
  }
  return error instanceof ApiError && error.kind === "server";
}

/**
 * Writes never retry by themselves (ADR-0009): a command with an unknown outcome goes through
 * `useCommand`, which keeps the same Idempotency-Key and lets the user decide.
 */
export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetryRead, staleTime: 30_000 },
      mutations: { retry: false },
    },
  });
}
