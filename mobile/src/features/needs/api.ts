import { useInfiniteQuery, useQueries, useQuery } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import { call } from "@/shared/api/errors";
import type { Offer } from "./summary";

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
