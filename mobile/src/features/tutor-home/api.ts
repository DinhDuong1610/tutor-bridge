import { useQueries } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import { ApiError, call } from "@/shared/api/errors";
import { isSessionEnd } from "@/shared/api/queryClient";
import type { QueryState } from "@/shared/ui/QueryScreen";
import type { TutorHomeData } from "./derive";

/** GS02, GS03 and GS04 invalidate this prefix after a save so the home shows the new state. */
export const TUTOR_HOME_KEY = ["tutor-home"] as const;

/** A 404 here means "not there yet" (never saved, never submitted, no calendar), not a failure. */
async function orNull<T>(request: Promise<T>): Promise<T | null> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof ApiError && error.kind === "not-found") return null;
    throw error;
  }
}

/**
 * The three reads of GS01 that have a merged API (Đính, #14 câu 1): own profile, own verification
 * (OP-23) and own calendar. The server takes the tutor from the session; nothing in the request
 * names them (INV-02).
 *
 * Three queries, not one: each read reaches the Query Cache on its own, so a 401 ends the session
 * as soon as it arrives, whatever the other reads do or however long they take (Codex, #206 P1).
 * `combine` gives `QueryScreen` one state: pending until all three answer, data when all three
 * have it, and the 401 first among the errors.
 */
export function useTutorHome(): QueryState<TutorHomeData> {
  return useQueries({
    queries: [
      { queryKey: [...TUTOR_HOME_KEY, "profile"], queryFn: () => orNull(call(api.GET("/me/tutor-profile", {}))) },
      { queryKey: [...TUTOR_HOME_KEY, "verification"], queryFn: () => orNull(call(api.GET("/me/tutor-profile/verification", {}))) },
      { queryKey: [...TUTOR_HOME_KEY, "calendar"], queryFn: () => orNull(call(api.GET("/me/tutor-calendar", {}))) },
    ],
    combine: ([profile, verification, calendar]) => {
      const all = [profile, verification, calendar];
      const errors = all.flatMap((result) => (result.error ? [result.error] : []));
      const loaded = profile.data !== undefined && verification.data !== undefined && calendar.data !== undefined;
      return {
        data: loaded ? { profile: profile.data, verification: verification.data, calendar: calendar.data } : undefined,
        // A failed first read shows the error at once instead of waiting for the others.
        isPending: errors.length === 0 && all.some((result) => result.isPending),
        isError: errors.length > 0,
        error: errors.find(isSessionEnd) ?? errors[0] ?? null,
        refetch: () => Promise.all(all.map((result) => result.refetch())),
      };
    },
  });
}
