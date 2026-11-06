import type { Problem } from "@tutorbridge/api-client";

export type { Problem };

/**
 * What the screen should do with a problem. Mapping follows the status codes in
 * docs/context/conventions.md; the stable `code` stays available for specific messages.
 */
export type ProblemKind =
  | "session-expired" // 401: ask to sign in again
  | "forbidden" // 403: access was lost
  | "not-found" // 404: also used to hide objects the user may not see
  | "conflict" // 409: business conflict or idempotency key reused
  | "stale" // 412: reload, then let the user decide again
  | "invalid" // 400, 422: show field errors, keep the input
  | "precondition-required" // 428: client bug, missing If-Match
  | "rate-limited" // 429: wait for Retry-After
  | "server"; // 5xx and anything else

export function isProblem(value: unknown): value is Problem {
  if (typeof value !== "object" || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v.type === "string" &&
    typeof v.title === "string" &&
    typeof v.status === "number" &&
    typeof v.code === "string" &&
    typeof v.detail === "string" &&
    typeof v.traceId === "string"
  );
}

export function problemKind(problem: Pick<Problem, "status">): ProblemKind {
  switch (problem.status) {
    case 401:
      return "session-expired";
    case 403:
      return "forbidden";
    case 404:
      return "not-found";
    case 409:
      return "conflict";
    case 412:
      return "stale";
    case 400:
    case 422:
      return "invalid";
    case 428:
      return "precondition-required";
    case 429:
      return "rate-limited";
    default:
      return "server";
  }
}

/** Field errors keyed by path. The first message wins when the server repeats a path. */
export function fieldErrors(problem: Pick<Problem, "fields">): Record<string, string> {
  const result: Record<string, string> = {};
  for (const field of problem.fields ?? []) {
    if (!(field.path in result)) result[field.path] = field.message;
  }
  return result;
}
