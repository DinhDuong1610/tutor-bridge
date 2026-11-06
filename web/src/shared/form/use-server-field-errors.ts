"use client";

import { useEffect, useMemo } from "react";
import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import type { ActionResult } from "@/shared/api/result";

/**
 * Puts the server's field messages next to the matching inputs; what the user typed stays.
 * Returns messages for fields the form does not have, to show above it.
 */
export function useServerFieldErrors<T extends FieldValues>(
  result: ActionResult<unknown> | null,
  setError: UseFormSetError<T>,
  fieldNames: readonly Path<T>[],
): string[] {
  const key = fieldNames.join("|");
  const split = useMemo(() => {
    const matched: { path: Path<T>; message: string }[] = [];
    const unmatched: string[] = [];
    const fields = result?.state === "failed" ? result.fields : {};
    for (const [path, message] of Object.entries(fields)) {
      const formPath = path.replace(/\[(\d+)]/g, ".$1");
      if (fieldNames.some((name) => formPath === name || formPath.startsWith(`${name}.`))) {
        matched.push({ path: formPath as Path<T>, message });
      } else {
        unmatched.push(message);
      }
    }
    return { matched, unmatched };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- fieldNames is compared by content through key
  }, [result, key]);

  useEffect(() => {
    split.matched.forEach(({ path, message }, index) =>
      setError(path, { type: "server", message }, { shouldFocus: index === 0 }),
    );
  }, [split, setError]);

  return split.unmatched;
}
