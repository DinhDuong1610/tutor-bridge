import { useInfiniteQuery, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import type { components } from "@tutorbridge/api-client";
import { useEffect } from "react";
import type { Candidate } from "@/features/matching/api";
import { OFFERS_KEY } from "@/features/needs/api";
import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { call, callWithEtag } from "@/shared/api/errors";

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

const PAGE_LIMIT = 50; // maxItems of OfferPage

/**
 * The parent's offers of one need. listOffers does not filter by need (#70 Q1), so every page is read and the
 * app keeps this need's; a parent has few offers. Keyed under OFFERS_KEY, so a send or a cancel refreshes it.
 */
export function useNeedOffers(needId: string) {
  const query = useInfiniteQuery({
    queryKey: [...OFFERS_KEY, "all"],
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) =>
      call(api.GET("/me/offers", { params: { query: { limit: PAGE_LIMIT, ...(pageParam ? { cursor: pageParam } : {}) } } })),
    getNextPageParam: (page) => page.nextCursor,
  });
  const { hasNextPage, isFetchingNextPage, fetchNextPage } = query;
  useEffect(() => {
    if (hasNextPage && !isFetchingNextPage) void fetchNextPage();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);
  const offers = query.data?.pages.flatMap((page) => page.items).filter((offer) => offer.needId === needId);
  return { ...query, offers: hasNextPage ? undefined : offers };
}

/** getOffer with the ETag of its revision, the If-Match of a cancel. The server checks the parties (INV-06). */
export function useOffer(offerId: string) {
  return useQuery({
    queryKey: [...OFFERS_KEY, "one", offerId],
    queryFn: () => callWithEtag(api.GET("/offers/{offerId}", { params: { path: { offerId } } })),
  });
}

/** Public names of tutors by id. A tutor whose profile is hidden now has no name here. */
export function useTutorNamesOf(tutorIds: readonly string[]): Map<string, string> {
  const ids = [...new Set(tutorIds)];
  return useQueries({
    queries: ids.map((tutorId) => ({
      queryKey: ["tutors", "public", tutorId] as const,
      queryFn: () => call(api.GET("/tutors/{tutorId}/public-profile", { params: { path: { tutorId } } })),
    })),
    combine: (results) => {
      const names = new Map<string, string>();
      results.forEach((result, index) => {
        if (result.data) names.set(ids[index], result.data.displayName);
      });
      return names;
    },
  });
}

/**
 * cancelOffer by the parent (TX06) with the reason the parent's side has, against the ETag of the offer on
 * screen (INV-23). A retry after an unknown outcome resends the same key and precondition (INV-20).
 */
export function useCancelOffer() {
  const queryClient = useQueryClient();
  const command = useCommand(({ offerId, etag }: { offerId: string; etag: string }, key: string): Promise<Offer> =>
    call(
      api.POST("/offers/{offerId}/cancel", {
        params: { path: { offerId }, header: { "Idempotency-Key": key, "If-Match": etag } },
        body: { reasonCode: "PARENT_CANCELLED" },
      }),
    ),
  );
  const done = command.state === "confirmed";
  useEffect(() => {
    if (done) void queryClient.invalidateQueries({ queryKey: OFFERS_KEY });
  }, [done, queryClient]);
  return command;
}
