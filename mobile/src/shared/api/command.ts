import { newIdempotencyKey } from "@tutorbridge/shared";
import { useMutation } from "@tanstack/react-query";
import { getRandomValues } from "expo-crypto";
import { useCallback, useRef } from "react";
import { ApiError, UnknownOutcomeError } from "./errors";

/**
 * The states every write shows (FR-37): sending, confirmed by the server, failed with a known
 * answer, or unknown outcome. Never show success before the server confirms.
 */
export type CommandState = "idle" | "sending" | "confirmed" | "failed" | "unknown";

/** The outcome is read from `state` and `error`; the promise only says when the attempt ended. */
function settle<T>(attempt: Promise<T>): Promise<T | undefined> {
  return attempt.catch(() => undefined);
}

/**
 * A write that gets any 5xx, with or without a problem body, has an unknown outcome: the command
 * may have committed before the error, so the retry keeps the same Idempotency-Key (INV-20, Đính
 * decided on #83). Only a 4xx is a known failure. Reads keep the {@link ApiError}.
 */
function rethrowAsCommandOutcome(error: unknown): never {
  if (error instanceof ApiError && error.problem.status >= 500) {
    throw new UnknownOutcomeError(error);
  }
  throw error;
}

export function newCommandKey(): string {
  return newIdempotencyKey((bytes) => getRandomValues(bytes));
}

/**
 * Runs a write command with an Idempotency-Key per user action (INV-20).
 *
 * - `submit` starts an action. The key is created on the first send.
 * - `retry` resends after an unknown outcome with the same key and input, so the server returns
 *   the first result instead of running the command twice.
 * - After a confirmed or failed answer the next `submit` uses a new key: the server keeps the
 *   failed receipt, and the same key with corrected input would be rejected as reused.
 */
export function useCommand<TInput, TResult>(
  send: (input: TInput, idempotencyKey: string) => Promise<TResult>,
) {
  const key = useRef<string | null>(null);
  const lastInput = useRef<TInput | null>(null);

  const mutation = useMutation<TResult, Error, { input: TInput; key: string }>({
    mutationFn: ({ input, key: idempotencyKey }) => send(input, idempotencyKey).catch(rethrowAsCommandOutcome),
    retry: false,
    onSettled: (_data, error) => {
      if (!(error instanceof UnknownOutcomeError)) {
        key.current = null;
      }
    },
  });

  const submit = useCallback(
    (input: TInput) => {
      key.current ??= newCommandKey();
      lastInput.current = input;
      return settle(mutation.mutateAsync({ input, key: key.current }));
    },
    [mutation],
  );

  const retry = useCallback(() => {
    if (key.current === null || lastInput.current === null) {
      throw new Error("Nothing to retry: the last command has a known outcome.");
    }
    return settle(mutation.mutateAsync({ input: lastInput.current, key: key.current }));
  }, [mutation]);

  const reset = useCallback(() => {
    key.current = null;
    lastInput.current = null;
    mutation.reset();
  }, [mutation]);

  const state: CommandState = mutation.isPending
    ? "sending"
    : mutation.isSuccess
      ? "confirmed"
      : mutation.error instanceof UnknownOutcomeError
        ? "unknown"
        : mutation.error
          ? "failed"
          : "idle";

  return {
    state,
    submit,
    retry,
    reset,
    result: mutation.data,
    error: mutation.error instanceof ApiError ? mutation.error : null,
  };
}
