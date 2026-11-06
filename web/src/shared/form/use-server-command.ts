"use client";

import { newIdempotencyKey } from "@tutorbridge/shared";
import { useCallback, useRef, useState, useTransition } from "react";
import type { ActionResult } from "@/shared/api/result";

export type CommandState = "idle" | "sending" | "confirmed" | "failed" | "unknown";

/**
 * Runs a Server Action that writes, with one Idempotency-Key per user action (INV-20), the same
 * rules as mobile `useCommand`: keep the key to retry an unknown outcome, take a new key after a
 * confirmed or failed answer.
 */
export function useServerCommand<TInput, TData>(
  action: (input: TInput, idempotencyKey: string) => Promise<ActionResult<TData>>,
) {
  const key = useRef<string | null>(null);
  const lastInput = useRef<TInput | null>(null);
  const [result, setResult] = useState<ActionResult<TData> | null>(null);
  const [pending, startTransition] = useTransition();

  const run = useCallback(
    (input: TInput, idempotencyKey: string) => {
      startTransition(async () => {
        let answer: ActionResult<TData>;
        try {
          answer = await action(input, idempotencyKey);
        } catch {
          // The browser lost Next, or the action crashed: the API may still have run the command.
          answer = { state: "unknown" };
        }
        if (answer.state !== "unknown") {
          key.current = null;
        }
        setResult(answer);
      });
    },
    [action],
  );

  const submit = useCallback(
    (input: TInput) => {
      key.current ??= newIdempotencyKey();
      lastInput.current = input;
      run(input, key.current);
    },
    [run],
  );

  const retry = useCallback(() => {
    if (key.current !== null && lastInput.current !== null) {
      run(lastInput.current, key.current);
    }
  }, [run]);

  const state: CommandState = pending ? "sending" : (result?.state ?? "idle");
  return { state, result, submit, retry };
}
