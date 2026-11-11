import { QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react-native";
import type { ReactNode } from "react";
import { createClient } from "./client";
import { useCommand } from "./command";
import { ApiError, UnknownOutcomeError, call } from "./errors";
import { createQueryClient, shouldRetryRead } from "./queryClient";

const PROBLEM = {
  type: "urn:tutorbridge:problem:VALIDATION_FAILED",
  title: "Dữ liệu chưa hợp lệ.",
  status: 422,
  code: "VALIDATION_FAILED",
  detail: "Dữ liệu chưa hợp lệ.",
  traceId: "0123456789abcdef0123456789abcdef",
  fields: [{ path: "displayName", message: "Nhập tên hiển thị." }],
};

function respond(status: number, body?: unknown, contentType = "application/json") {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": contentType },
  });
}

describe("client", () => {
  it("sends the session as a bearer token", async () => {
    const requests: Request[] = [];
    const client = createClient({
      baseUrl: "https://api.test/api/v1",
      token: () => "session-token",
      fetch: async (request) => {
        requests.push(request);
        return respond(200, { items: [], nextCursor: null });
      },
    });

    await client.GET("/students", {});

    expect(requests[0].headers.get("Authorization")).toBe("Bearer session-token");
  });

  it("sends no Authorization header when signed out", async () => {
    const requests: Request[] = [];
    const client = createClient({
      baseUrl: "https://api.test/api/v1",
      token: async () => null,
      fetch: async (request) => {
        requests.push(request);
        return respond(200, { items: [], nextCursor: null });
      },
    });

    await client.GET("/students", {});

    expect(requests[0].headers.has("Authorization")).toBe(false);
  });
});

describe("call", () => {
  const client = (answer: () => Promise<Response>) =>
    createClient({ baseUrl: "https://api.test/api/v1", token: () => null, fetch: answer });

  it("returns the data of a successful answer", async () => {
    const page = await call(client(async () => respond(200, { items: [], nextCursor: null })).GET("/students", {}));
    expect(page).toEqual({ items: [], nextCursor: null });
  });

  it("turns a problem into an ApiError with its kind and field messages", async () => {
    const failing = client(async () => respond(422, PROBLEM, "application/problem+json"));

    const error = await call(failing.GET("/students", {})).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).kind).toBe("invalid");
    expect((error as ApiError).code).toBe("VALIDATION_FAILED");
    expect((error as ApiError).fields).toEqual({ displayName: "Nhập tên hiển thị." });
  });

  it("treats a lost connection as an unknown outcome", async () => {
    const offline = client(async () => {
      throw new TypeError("Network request failed");
    });

    await expect(call(offline.GET("/students", {}))).rejects.toBeInstanceOf(UnknownOutcomeError);
  });

  it("treats a gateway page without a problem as an unknown outcome", async () => {
    const gateway = client(async () => respond(502, undefined, "text/html"));

    await expect(call(gateway.GET("/students", {}))).rejects.toBeInstanceOf(UnknownOutcomeError);
  });
});

describe("read retry", () => {
  const problem = (status: number) => new ApiError({ ...PROBLEM, status });

  it("retries lost connections and server errors, at most twice", () => {
    expect(shouldRetryRead(0, new UnknownOutcomeError())).toBe(true);
    expect(shouldRetryRead(1, problem(500))).toBe(true);
    expect(shouldRetryRead(2, new UnknownOutcomeError())).toBe(false);
  });

  it("does not retry answers that will not change", () => {
    for (const status of [401, 403, 404, 409, 412, 422, 429]) {
      expect(shouldRetryRead(0, problem(status))).toBe(false);
    }
  });
});

describe("useCommand", () => {
  function setup(send: jest.Mock) {
    // The app's defaults, except that an unmounted mutation schedules no 5 minute cleanup
    // timer, which would keep Jest running after the tests.
    const queryClient = createQueryClient();
    const defaults = queryClient.getDefaultOptions();
    queryClient.setDefaultOptions({ ...defaults, mutations: { ...defaults.mutations, gcTime: Infinity } });
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
    return renderHook(() => useCommand<{ name: string }, { id: string }>(send), { wrapper });
  }

  it("keeps the same key when retrying an unknown outcome, then confirms", async () => {
    const send = jest
      .fn()
      .mockRejectedValueOnce(new UnknownOutcomeError())
      .mockResolvedValueOnce({ id: "s1" });
    const { result } = await setup(send);

    await act(() => result.current.submit({ name: "Minh" }));
    await waitFor(() => expect(result.current.state).toBe("unknown"));
    expect(send).toHaveBeenCalledTimes(1);

    await act(() => result.current.retry());
    await waitFor(() => expect(result.current.state).toBe("confirmed"));

    expect(send).toHaveBeenCalledTimes(2);
    const [firstKey, secondKey] = send.mock.calls.map((args) => args[1]);
    expect(firstKey).toMatch(/^[0-9a-f]{32}$/);
    expect(secondKey).toBe(firstKey);
    expect(send.mock.calls[1][0]).toEqual({ name: "Minh" });
    expect(result.current.result).toEqual({ id: "s1" });
  });

  it("uses a new key for the next action after a confirmed one", async () => {
    const send = jest.fn().mockResolvedValue({ id: "s1" });
    const { result } = await setup(send);

    await act(() => result.current.submit({ name: "Minh" }));
    await act(() => result.current.submit({ name: "Lan" }));

    expect(send.mock.calls[1][1]).not.toBe(send.mock.calls[0][1]);
  });

  it("reports a known failure with its problem and uses a new key next time", async () => {
    const send = jest
      .fn()
      .mockRejectedValueOnce(new ApiError(PROBLEM))
      .mockResolvedValueOnce({ id: "s1" });
    const { result } = await setup(send);

    await act(() => result.current.submit({ name: "" }));
    await waitFor(() => expect(result.current.state).toBe("failed"));
    expect(result.current.error?.fields).toEqual({ displayName: "Nhập tên hiển thị." });
    expect(send).toHaveBeenCalledTimes(1);

    await act(() => result.current.submit({ name: "Minh" }));

    expect(send.mock.calls[1][1]).not.toBe(send.mock.calls[0][1]);
    await waitFor(() => expect(result.current.state).toBe("confirmed"));
  });

  it("shows sending while the server has not answered", async () => {
    let answer: (value: { id: string }) => void = () => undefined;
    const send = jest.fn(() => new Promise<{ id: string }>((resolve) => (answer = resolve)));
    const { result } = await setup(send);

    await act(() => {
      void result.current.submit({ name: "Minh" });
    });
    await waitFor(() => expect(result.current.state).toBe("sending"));

    await act(async () => answer({ id: "s1" }));
    await waitFor(() => expect(result.current.state).toBe("confirmed"));
  });
});
