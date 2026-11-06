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

export function isSessionEnd(error: unknown) {
  return error instanceof ApiError && error.kind === "session-expired";
}

/**
 * Calls `onEnd` when any read or write answers 401, so every screen handles "hết phiên" the same
 * way. Returns the unsubscribe function.
 */
export function watchSessionEnd(client: QueryClient, onEnd: () => void): () => void {
  const unsubscribeQueries = client.getQueryCache().subscribe((event) => {
    if (event.type === "updated" && event.action.type === "error" && isSessionEnd(event.action.error)) {
      onEnd();
    }
  });
  const unsubscribeMutations = client.getMutationCache().subscribe((event) => {
    if (event.type === "updated" && event.action.type === "error" && isSessionEnd(event.action.error)) {
      onEnd();
    }
  });
  return () => {
    unsubscribeQueries();
    unsubscribeMutations();
  };
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
