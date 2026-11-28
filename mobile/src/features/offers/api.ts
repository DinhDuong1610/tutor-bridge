import { useQueryClient } from "@tanstack/react-query";
import type { components } from "@tutorbridge/api-client";
import { useEffect } from "react";
import type { Candidate } from "@/features/matching/api";
import { OFFERS_KEY } from "@/features/needs/api";
import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { call } from "@/shared/api/errors";

export type Offer = components["schemas"]["Offer"];

/** What PH08 sends: the candidate exactly as the run found it, against the need revision on screen. */
export type SendInput = {
  needId: string;
  etag: string;
  needVersionId: string;
  runId: string;
  studentCalendarVersionId: string;
  candidate: Candidate;
};

/**
 * sendOffer (UC06, TX04). The body is the candidate of the run unchanged, so the server can check that the
 * offer is the one the parent saw (Q2 of #18); `acceptUnchangedTerms` states that the parent agrees to these
 * terms. The If-Match is the ETag of the need on screen (INV-23). Everything travels in the input: a retry
 * after an unknown outcome resends the same key, precondition and body, and gets the first offer (INV-20).
 */
export function useSendOffer() {
  const queryClient = useQueryClient();
  const command = useCommand((input: SendInput, key: string): Promise<Offer> =>
    call(
      api.POST("/needs/{needId}/offers", {
        params: { path: { needId: input.needId }, header: { "Idempotency-Key": key, "If-Match": input.etag } },
        body: {
          needVersionId: input.needVersionId,
          matchingRunId: input.runId,
          tutorId: input.candidate.tutor.tutorId,
          profileVersionId: input.candidate.tutor.profileVersionId,
          tutorCalendarVersionId: input.candidate.tutorCalendarVersionId,
          studentCalendarVersionId: input.studentCalendarVersionId,
          schedule: input.candidate.commonSchedule,
          acceptUnchangedTerms: true,
        },
      }),
    ),
  );
  const sent = command.state === "confirmed";
  useEffect(() => {
    // PH03 counts offers and PH09 lists them: both read the new one next time.
    if (sent) void queryClient.invalidateQueries({ queryKey: OFFERS_KEY });
  }, [sent, queryClient]);
  return command;
}

/** Codes of a refusal because what the parent saw has changed: the tutor's price or calendar, or the run. */
export const TERMS_CHANGED = new Set(["OFFER_TERMS_CHANGED", "SCHEDULE_CONFLICT", "MATCHING_RUN_EXPIRED"]);
