import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { components } from "@tutorbridge/api-client";
import { useEffect } from "react";
import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { ApiError, callWithEtag } from "@/shared/api/errors";

export type TutorCalendar = components["schemas"]["EffectiveCalendar"];
export type CalendarWindowInput = components["schemas"]["CalendarWindowInput"];
/** A calendar version with its ETag, for the If-Match of the next save (INV-23). */
export type CalendarRevision = { data: TutorCalendar; etag: string };

export const TUTOR_CALENDAR_KEY = ["tutor-calendar"] as const;

/** getTutorCalendar. A 404 (CALENDAR_NOT_FOUND) means the tutor never saved one: `null`. */
export function useTutorCalendar() {
  return useQuery({
    queryKey: TUTOR_CALENDAR_KEY,
    queryFn: async (): Promise<CalendarRevision | null> => {
      try {
        return await callWithEtag(api.GET("/me/tutor-calendar", {}));
      } catch (error) {
        if (error instanceof ApiError && error.kind === "not-found") return null;
        throw error;
      }
    },
  });
}

/** The revision a save starts from: none before the first save (If-None-Match: *), else its ETag. */
export type CalendarSave = { etag: string | null; windows: CalendarWindowInput[] };

/**
 * replaceTutorCalendar. The first save sends If-None-Match: *, every later one If-Match with the ETag the
 * screen was filled from (contract). The precondition and the windows travel in the input, so a retry after
 * an unknown outcome resends the same key, body and precondition (INV-20).
 */
export function useReplaceTutorCalendar() {
  const queryClient = useQueryClient();
  const command = useCommand(({ etag, windows }: CalendarSave, key: string) =>
    callWithEtag(
      api.PUT("/me/tutor-calendar", {
        params: {
          header: etag === null ? { "Idempotency-Key": key, "If-None-Match": "*" } : { "Idempotency-Key": key, "If-Match": etag },
        },
        body: { timezone: "Asia/Ho_Chi_Minh", windows },
      }),
    ),
  );
  const saved = command.state === "confirmed" ? command.result : undefined;
  useEffect(() => {
    if (saved) queryClient.setQueryData(TUTOR_CALENDAR_KEY, saved);
  }, [saved, queryClient]);
  return command;
}
