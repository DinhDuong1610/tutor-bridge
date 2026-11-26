import { useQueries, useQueryClient } from "@tanstack/react-query";
import type { components } from "@tutorbridge/api-client";
import { useEffect } from "react";
import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { ApiError, call, callWithEtag } from "@/shared/api/errors";
import { isSessionEnd } from "@/shared/api/queryClient";
import type { QueryState } from "@/shared/ui/QueryScreen";
import { TUTOR_HOME_KEY } from "../tutor-home/api";
import { SUBJECT, type TutorProfileForm } from "./schemas";

export type TutorProfile = components["schemas"]["TutorProfileDraft"];
export type OwnVerification = components["schemas"]["TutorVerificationView"];
/** A profile version with the ETag of its revision, for the If-Match of the next save (INV-23). */
export type ProfileRevision = { data: TutorProfile; etag: string };
export type TutorProfileData = { profile: ProfileRevision | null; verification: OwnVerification | null };

export const TUTOR_PROFILE_KEY = ["tutor-profile"] as const;

/** A 404 means "not there yet": never saved, or never submitted. */
async function orNull<T>(request: Promise<T>): Promise<T | null> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof ApiError && error.kind === "not-found") return null;
    throw error;
  }
}

/**
 * GS02 reads the newest own profile version with its ETag, and the own verification, which tells a
 * version waiting for its verifier (locked, OP-24) from one that needs more information (editable).
 * The verification shares GS01's query, so both screens read the same answer. Two queries for the same
 * reason as GS01: a 401 reaches the Query Cache as soon as it arrives (Codex, #206 P1).
 */
export type TutorProfileQuery = QueryState<TutorProfileData> & {
  /** Reads both again; "Tải lại" after a 412 moves the form to what this returns. */
  refetch: () => Promise<{ isSuccess: boolean; data?: TutorProfileData }>;
};

export function useTutorProfile(): TutorProfileQuery {
  return useQueries({
    queries: [
      {
        queryKey: [...TUTOR_PROFILE_KEY, "own"],
        queryFn: () => orNull(callWithEtag(api.GET("/me/tutor-profile", {}))),
      },
      {
        queryKey: [...TUTOR_HOME_KEY, "verification"],
        queryFn: () => orNull(call(api.GET("/me/tutor-profile/verification", {}))),
      },
    ],
    combine: ([profile, verification]) => {
      const all = [profile, verification];
      const errors = all.flatMap((result) => (result.error ? [result.error] : []));
      const loaded = profile.data !== undefined && verification.data !== undefined;
      return {
        data: loaded ? { profile: profile.data, verification: verification.data } : undefined,
        isPending: errors.length === 0 && all.some((result) => result.isPending),
        isError: errors.length > 0,
        error: errors.find(isSessionEnd) ?? errors[0] ?? null,
        refetch: () =>
          Promise.all([profile.refetch(), verification.refetch()]).then(([own, ver]) => ({
            isSuccess: own.isSuccess && ver.isSuccess,
            data: own.data !== undefined && ver.data !== undefined ? { profile: own.data, verification: ver.data } : undefined,
          })),
      };
    },
  });
}

/** The revision a save starts from: none before the first save (If-None-Match: *), else its ETag. */
export type ProfileSave = { etag: string | null; values: TutorProfileForm };

/**
 * saveTutorProfile. The first save sends If-None-Match: *, every later one If-Match with the ETag the
 * form was filled from, never one guessed from the cache (contract). The precondition travels in the
 * input, so a retry after an unknown outcome resends the same key, body and precondition (INV-20).
 */
export function useSaveTutorProfile() {
  const queryClient = useQueryClient();
  const command = useCommand(({ etag, values }: ProfileSave, key: string) =>
    callWithEtag(
      api.PUT("/me/tutor-profile", {
        params: {
          header: etag === null ? { "Idempotency-Key": key, "If-None-Match": "*" } : { "Idempotency-Key": key, "If-Match": etag },
        },
        body: { ...values, subject: SUBJECT },
      }),
    ),
  );
  const saved = command.state === "confirmed" ? command.result : undefined;
  useEffect(() => {
    if (saved) {
      // The saved revision and its new ETag, so the next save starts from it (Codex review of #138).
      queryClient.setQueryData([...TUTOR_PROFILE_KEY, "own"], saved);
      void queryClient.invalidateQueries({ queryKey: TUTOR_HOME_KEY });
    }
  }, [saved, queryClient]);
  return command;
}
