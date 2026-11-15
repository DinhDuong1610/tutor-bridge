import { fieldErrors, isProblem, problemKind, type Problem, type ProblemKind } from "@tutorbridge/shared";

/** The server answered with a failure. The command did not take effect. */
export class ApiError extends Error {
  readonly kind: ProblemKind;
  readonly problem: Problem;
  /** Field messages keyed by path, for showing next to the input while keeping what was typed. */
  readonly fields: Record<string, string>;

  constructor(problem: Problem) {
    super(problem.title);
    this.name = "ApiError";
    this.problem = problem;
    this.kind = problemKind(problem);
    this.fields = fieldErrors(problem);
  }

  get code() {
    return this.problem.code;
  }
}

/**
 * No usable answer: the network failed, or a gateway answered instead of the API. A write may or
 * may not have taken effect, so the screen must not say it failed (FR-37 "chưa rõ kết quả").
 * `useCommand` also turns a write's 5xx problem into this error (#83).
 */
export class UnknownOutcomeError extends Error {
  constructor(cause?: unknown) {
    super("Chưa rõ kết quả. Vui lòng kiểm tra lại.", { cause });
    this.name = "UnknownOutcomeError";
  }
}

type FetchResult<T> = { data?: T; error?: unknown; response: Response };

/**
 * Unwraps an api-client call: returns the data, or throws {@link ApiError} or
 * {@link UnknownOutcomeError}. Use it in every query and command.
 *
 * <pre>
 * const page = await call(api.GET("/students", {}));
 * </pre>
 */
export async function call<T>(request: Promise<FetchResult<T>>): Promise<T> {
  return (await answer(request)).data;
}

/**
 * Like {@link call}, and also returns the strong `ETag` of the resource, for an `If-Match` on the
 * next write. The client never builds an ETag itself (G3-D07-06), so a missing header is an error.
 *
 * <pre>
 * const { data, etag } = await callWithEtag(api.GET("/students/{studentId}", { params }));
 * </pre>
 */
export async function callWithEtag<T>(request: Promise<FetchResult<T>>): Promise<{ data: T; etag: string }> {
  const { data, response } = await answer(request);
  const etag = response.headers.get("ETag");
  if (!etag) {
    throw new Error("The answer has no ETag, so the resource cannot be changed safely.");
  }
  return { data, etag };
}

async function answer<T>(request: Promise<FetchResult<T>>): Promise<{ data: T; response: Response }> {
  let result: FetchResult<T>;
  try {
    result = await request;
  } catch (cause) {
    throw new UnknownOutcomeError(cause);
  }
  const { data, error, response } = result;
  if (response.ok) {
    return { data: data as T, response };
  }
  if (isProblem(error)) {
    throw new ApiError(error);
  }
  if (response.status >= 500) {
    // A proxy or gateway page, not our API: the request may still have been processed.
    throw new UnknownOutcomeError(new Error(`HTTP ${response.status} without a problem body`));
  }
  throw new ApiError({
    type: "about:blank",
    title: "Yêu cầu không được chấp nhận.",
    status: response.status,
    code: "UNEXPECTED_RESPONSE",
    detail: "Yêu cầu không được chấp nhận.",
    traceId: "",
  });
}
