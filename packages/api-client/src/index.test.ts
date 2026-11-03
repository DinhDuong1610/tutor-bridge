import { describe, expect, it } from "vitest";
import { createApiClient, type Problem } from "./index";

const OFFER_ID = "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10";

function recordingFetch(response: Response) {
  const requests: Request[] = [];
  const fetch = async (input: Request) => {
    requests.push(input);
    return response;
  };
  return { fetch, requests };
}

describe("createApiClient", () => {
  it("sends acceptOffer to the contract path with its required headers", async () => {
    const { fetch, requests } = recordingFetch(new Response("{}", { status: 201 }));
    const client = createApiClient({ baseUrl: "https://api.test/api/v1", fetch });

    await client.POST("/offers/{offerId}/accept", {
      params: {
        path: { offerId: OFFER_ID },
        header: { "Idempotency-Key": "a".repeat(16), "If-Match": '"1"' },
      },
      body: { acceptUnchangedTerms: true },
    });

    const request = requests[0];
    expect(request.method).toBe("POST");
    expect(request.url).toBe(`https://api.test/api/v1/offers/${OFFER_ID}/accept`);
    expect(request.headers.get("Idempotency-Key")).toBe("a".repeat(16));
    expect(request.headers.get("If-Match")).toBe('"1"');
    expect(await request.json()).toEqual({ acceptUnchangedTerms: true });
  });

  it("returns a problem response as a typed error", async () => {
    const problem: Problem = {
      type: "urn:tutorbridge:problem:SCHEDULE_CONFLICT",
      title: "Lịch vừa bị trùng.",
      status: 409,
      code: "SCHEDULE_CONFLICT",
      detail: "Lịch vừa bị trùng.",
      traceId: "0123456789abcdef0123456789abcdef",
    };
    const { fetch } = recordingFetch(
      new Response(JSON.stringify(problem), {
        status: 409,
        headers: { "Content-Type": "application/problem+json" },
      }),
    );
    const client = createApiClient({ baseUrl: "https://api.test/api/v1", fetch });

    const { data, error } = await client.POST("/offers/{offerId}/accept", {
      params: {
        path: { offerId: OFFER_ID },
        header: { "Idempotency-Key": "a".repeat(16), "If-Match": '"1"' },
      },
      body: { acceptUnchangedTerms: true },
    });

    expect(data).toBeUndefined();
    expect(error).toEqual(problem);
  });

  it("rejects paths and bodies outside the contract at compile time", () => {
    const client = createApiClient({ baseUrl: "https://api.test/api/v1" });
    const neverCalled = () => {
      // @ts-expect-error the contract has no such path
      void client.GET("/offers/{offerId}/secret");
      void client.POST("/offers/{offerId}/accept", {
        params: {
          path: { offerId: OFFER_ID },
          header: { "Idempotency-Key": "a".repeat(16), "If-Match": '"1"' },
        },
        // @ts-expect-error acceptUnchangedTerms must be true
        body: { acceptUnchangedTerms: false },
      });
    };
    expect(neverCalled).toBeTypeOf("function");
  });
});
