import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { components } from "@tutorbridge/api-client";
import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { ApiError, callWithEtag } from "@/shared/api/errors";
import type { CalendarWindow } from "./weekly";

export type EffectiveCalendar = components["schemas"]["EffectiveCalendar"];
/** A calendar version with its ETag, for the If-Match of the next save (INV-23). */
export type CalendarRevision = { data: EffectiveCalendar; etag: string };

export const studentCalendarKey = (studentId: string) => ["calendars", "student", studentId] as const;

/**
 * getStudentCalendar for the child's free time on PH04 (#16 Q1: the schedule is the child's calendar). A 404
 * CALENDAR_NOT_FOUND means none was saved yet: `null`. Another family's child is STUDENT_NOT_FOUND, an error.
 */
export function useStudentCalendar(studentId: string) {
  return useQuery({
    queryKey: studentCalendarKey(studentId),
    queryFn: async (): Promise<CalendarRevision | null> => {
      try {
        return await callWithEtag(api.GET("/students/{studentId}/calendar", { params: { path: { studentId } } }));
      } catch (error) {
        if (error instanceof ApiError && error.code === "CALENDAR_NOT_FOUND") return null;
        throw error;
      }
    },
  });
}

/** The revision a save starts from: none before the first save (If-None-Match: *), else its ETag. */
export type StudentCalendarSave = { studentId: string; etag: string | null; windows: CalendarWindow[] };

/**
 * replaceStudentCalendar. The first save sends If-None-Match: *, every later one If-Match with the ETag read
 * (contract). Everything travels in the input, so a retry after an unknown outcome resends the same key, body
 * and precondition (INV-20).
 */
export function useReplaceStudentCalendar() {
  const queryClient = useQueryClient();
  return useCommand(async ({ studentId, etag, windows }: StudentCalendarSave, key: string) => {
    const saved = await callWithEtag(
      api.PUT("/students/{studentId}/calendar", {
        params: {
          path: { studentId },
          header: etag === null ? { "Idempotency-Key": key, "If-None-Match": "*" } : { "Idempotency-Key": key, "If-Match": etag },
        },
        body: { timezone: "Asia/Ho_Chi_Minh", windows },
      }),
    );
    // The saved version and its new ETag, so the next save starts from it.
    queryClient.setQueryData(studentCalendarKey(studentId), saved);
    return saved;
  });
}
