import { useEffect, useMemo } from "react";
import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import type { ApiError } from "@/shared/api/errors";

/** `items[0].name` from the server becomes `items.0.name` for react-hook-form. */
function toFormPath(path: string) {
  return path.replace(/\[(\d+)]/g, ".$1");
}

/** Splits the server's field messages into those for the form's inputs and the rest. */
export function splitServerErrors(error: ApiError | null, fieldNames: readonly string[]) {
  const matched: { path: string; message: string }[] = [];
  const unmatched: string[] = [];
  for (const [path, message] of Object.entries(error?.fields ?? {})) {
    const formPath = toFormPath(path);
    if (fieldNames.some((name) => formPath === name || formPath.startsWith(`${name}.`))) {
      matched.push({ path: formPath, message });
    } else {
      unmatched.push(message);
    }
  }
  return { matched, unmatched };
}

/**
 * Puts the server's field messages (Problem `fields`) next to the matching inputs; what the user
 * typed stays in the form. Returns the messages for paths the form does not have, to show above it.
 */
export function useServerErrors<T extends FieldValues>(
  error: ApiError | null,
  setError: UseFormSetError<T>,
  fieldNames: readonly Path<T>[],
): string[] {
  const key = fieldNames.join("|");
  // eslint-disable-next-line react-hooks/exhaustive-deps -- fieldNames is compared by content through key
  const split = useMemo(() => splitServerErrors(error, fieldNames), [error, key]);
  useEffect(() => {
    split.matched.forEach(({ path, message }, index) =>
      setError(path as Path<T>, { type: "server", message }, { shouldFocus: index === 0 }),
    );
  }, [split, setError]);
  return split.unmatched;
}
