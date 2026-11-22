import { useQuery } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import { ApiError, call } from "@/shared/api/errors";
import type { TutorHomeData } from "./derive";

/** GS02, GS03 and GS04 invalidate this key after a save so the home shows the new state. */
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
 */
export function useTutorHome() {
  return useQuery({
    queryKey: TUTOR_HOME_KEY,
    queryFn: async (): Promise<TutorHomeData> => {
      const [profile, verification, calendar] = await Promise.all([
        orNull(call(api.GET("/me/tutor-profile", {}))),
        orNull(call(api.GET("/me/tutor-profile/verification", {}))),
        orNull(call(api.GET("/me/tutor-calendar", {}))),
      ]);
      return { profile, verification, calendar };
    },
  });
}
