import "server-only";
import { fieldErrors, isProblem, problemKind, type Problem } from "@tutorbridge/shared";
import { redirect } from "next/navigation";
import { SESSION_EXPIRED_PATH } from "@/shared/session/verify";

type FetchResult<T> = { data?: T; error?: unknown; response: Response };

/** What a page shows after reading from the API. A 401 never comes back: it ends the session. */
export type LoadResult<T> =
  | { status: "ok"; data: T }
  | { status: "forbidden" | "not-found" | "rate-limited" | "error"; problem?: Problem };

/**
 * Unwraps a read for a Server Component:
 *
 * <pre>
 * const result = await load((await adminApi()).GET("/admin/verifications", {}));
 * if (result.status !== "ok") return <StateMessage result={result} />;
 * </pre>
 */
export async function load<T>(request: Promise<FetchResult<T>>): Promise<LoadResult<T>> {
  let result: FetchResult<T>;
  try {
    result = await request;
  } catch {
    return { status: "error" };
  }
  const { data, error, response } = result;
  if (response.ok) {
    return { status: "ok", data: data as T };
  }
  if (response.status === 401) {
    redirect(SESSION_EXPIRED_PATH);
  }
  const problem = isProblem(error) ? error : undefined;
  switch (problemKind({ status: response.status })) {
    case "forbidden":
      return { status: "forbidden", problem };
    case "not-found":
      return { status: "not-found", problem };
    case "rate-limited":
      return { status: "rate-limited", problem };
    default:
      return { status: "error", problem };
  }
}

/** What a Server Action returns to the form. Plain data, so it crosses to the browser. */
export type ActionResult<T> =
  | { state: "confirmed"; data: T }
  | { state: "failed"; title: string; fields: Record<string, string> }
  | { state: "unknown" };

/**
 * Unwraps a write inside a Server Action. A lost connection or a gateway page is "unknown": the
 * command may have run, so the form offers a retry with the same Idempotency-Key (INV-20).
 */
export async function runCommand<T>(request: Promise<FetchResult<T>>): Promise<ActionResult<T>> {
  let result: FetchResult<T>;
  try {
    result = await request;
  } catch {
    return { state: "unknown" };
  }
  const { data, error, response } = result;
  if (response.ok) {
    return { state: "confirmed", data: data as T };
  }
  if (response.status === 401) {
    redirect(SESSION_EXPIRED_PATH);
  }
  if (isProblem(error)) {
    return { state: "failed", title: error.title, fields: fieldErrors(error) };
  }
  if (response.status >= 500) {
    return { state: "unknown" };
  }
  return { state: "failed", title: "Yêu cầu không được chấp nhận.", fields: {} };
}
