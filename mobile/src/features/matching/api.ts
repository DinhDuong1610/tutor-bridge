import type { components } from "@tutorbridge/api-client";
import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { call } from "@/shared/api/errors";

export type MatchingRun = components["schemas"]["MatchingRun"];
export type Candidate = components["schemas"]["Candidate"];

/** What PH06 searches from: the confirmed version on screen and the ETag of its revision. */
export type RunInput = { needId: string; needVersionId: string; etag: string };

/**
 * createMatchingRun B (UC05, TX03). Lát 1 has no AI ranking (OP-11), so the parent's tap on "Xem danh sách
 * lọc theo điều kiện" is the explicit choice of B that BR03 asks for. The If-Match is the ETag of the need
 * on screen, so a need changed since it loaded answers 412 instead of searching an old version (INV-23).
 * Everything travels in the input: a retry after an unknown outcome resends the same key and body (INV-20).
 */
export function useCreateMatchingRun() {
  return useCommand(({ needId, needVersionId, etag }: RunInput, key: string): Promise<MatchingRun> =>
    call(
      api.POST("/needs/{needId}/matching-runs", {
        params: { path: { needId }, header: { "Idempotency-Key": key, "If-Match": etag } },
        body: { mode: "B", needVersionId, fallbackExplicitlyChosen: true },
      }),
    ),
  );
}
