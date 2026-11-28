import { useInfiniteQuery, useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import type { components } from "@tutorbridge/api-client";
import { useEffect } from "react";
import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { call, callWithEtag } from "@/shared/api/errors";
import type { NeedDraftInput } from "./draftForm";
import type { Offer } from "./summary";

export type Need = components["schemas"]["Need"];
/** A need version with the ETag of its revision, for the If-Match of the next save (INV-23). */
export type NeedRevision = { data: Need; etag: string };

const PAGE_LIMIT = 50; // maxItems of NeedPage and OfferPage

export const needsKey = (studentId: string) => ["needs", "student", studentId] as const;
export const OFFERS_KEY = ["offers"] as const;

/**
 * listNeeds for one child: one entry per need with its current version, newest first. Keyed by the
 * child, so changing child never mixes two children's needs (AC-UC02-01). The server checks the owner.
 */
export function useNeeds(studentId: string) {
  return useInfiniteQuery({
    queryKey: needsKey(studentId),
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) =>
      call(
        api.GET("/students/{studentId}/needs", {
          params: { path: { studentId }, query: { limit: PAGE_LIMIT, ...(pageParam ? { cursor: pageParam } : {}) } },
        }),
      ),
    getNextPageParam: (page) => page.nextCursor,
  });
}

/**
 * The first page of listOffers, for the counts on PH03 cards. listOffers does not filter by need, so
 * the app filters what it loaded (#70 Q1); an offer past the first 50 is not counted (#70 PR 2 Q2).
 */
export function useFirstOffers() {
  return useQuery({
    queryKey: [...OFFERS_KEY, "first-page"],
    queryFn: () => call(api.GET("/me/offers", { params: { query: { limit: PAGE_LIMIT } } })),
  });
}

export const needKey = (needId: string) => ["needs", "one", needId] as const;

/** getNeed with its ETag, the revision PH04 edits. The server checks the owner (INV-06). */
export function useNeed(needId: string) {
  return useQuery({
    queryKey: needKey(needId),
    queryFn: () => callWithEtag(api.GET("/needs/{needId}", { params: { path: { needId } } })),
  });
}

/** A save from PH04: a new draft for the child, or the whole draft of an existing need against its ETag. */
export type NeedSave =
  | { kind: "create"; studentId: string; draft: NeedDraftInput }
  | { kind: "update"; needId: string; etag: string; draft: NeedDraftInput };

/**
 * createNeedDraft or updateNeedDraft. The body is the whole draft (#16 Q3). An update sends If-Match with the
 * ETag the form was filled from, never one guessed from the cache (INV-23). Everything travels in the input,
 * so a retry after an unknown outcome resends the same key, body and precondition (INV-20).
 */
export function useSaveNeedDraft() {
  const queryClient = useQueryClient();
  const command = useCommand((save: NeedSave, key: string): Promise<NeedRevision> =>
    save.kind === "create"
      ? callWithEtag(
          api.POST("/students/{studentId}/needs", {
            params: { path: { studentId: save.studentId }, header: { "Idempotency-Key": key } },
            body: save.draft,
          }),
        )
      : callWithEtag(
          api.PATCH("/needs/{needId}", {
            params: { path: { needId: save.needId }, header: { "Idempotency-Key": key, "If-Match": save.etag } },
            body: save.draft,
          }),
        ),
  );
  const saved = command.state === "confirmed" ? command.result : undefined;
  useEffect(() => {
    if (saved) {
      queryClient.setQueryData(needKey(saved.data.id), saved);
      // PH03 lists the child's needs: the new draft or its new summary shows there next time.
      void queryClient.invalidateQueries({ queryKey: needsKey(saved.data.studentId) });
    }
  }, [saved, queryClient]);
  return command;
}

/**
 * confirmNeed (PH05) against the ETag of the version shown (AC-UC04-01: the parent confirms exactly what the
 * server holds). The precondition travels in the input, so a retry after an unknown outcome resends the same
 * key and precondition (INV-20). The confirmed need replaces the cached one, and PH03 lists it anew.
 */
export function useConfirmNeed() {
  const queryClient = useQueryClient();
  const command = useCommand(({ needId, etag }: { needId: string; etag: string }, key: string): Promise<NeedRevision> =>
    callWithEtag(
      api.POST("/needs/{needId}/confirm", {
        params: { path: { needId }, header: { "Idempotency-Key": key, "If-Match": etag } },
        body: { confirmed: true },
      }),
    ),
  );
  const confirmed = command.state === "confirmed" ? command.result : undefined;
  useEffect(() => {
    if (confirmed) {
      queryClient.setQueryData(needKey(confirmed.data.id), confirmed);
      void queryClient.invalidateQueries({ queryKey: needsKey(confirmed.data.studentId) });
    }
  }, [confirmed, queryClient]);
  return command;
}

/** Public names of the tutors of accepted offers, by tutor id. A name that fails to load is left out. */
export function useTutorNames(offers: readonly Offer[]): Map<string, string> {
  const tutorIds = [...new Set(offers.filter((offer) => offer.state === "ACCEPTED").map((offer) => offer.tutorId))];
  return useQueries({
    queries: tutorIds.map((tutorId) => ({
      queryKey: ["tutors", "public", tutorId] as const,
      queryFn: () => call(api.GET("/tutors/{tutorId}/public-profile", { params: { path: { tutorId } } })),
    })),
    combine: (results) => {
      const names = new Map<string, string>();
      results.forEach((result, index) => {
        if (result.data) names.set(tutorIds[index], result.data.displayName);
      });
      return names;
    },
  });
}
