import { useQuery } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import { call } from "@/shared/api/errors";
import type { Session } from "./weeks";

const PAGE_LIMIT = 50; // maxItems of SessionPage
// A class in Lát 1 has one cycle of at most 12 sessions (BR04), so one page holds them all. The cap
// keeps a server that kept sending cursors from looping forever.
const MAX_PAGES = 10;

export const classKey = (classId: string) => ["classes", classId] as const;
export const sessionsKey = (classId: string) => ["classes", classId, "sessions"] as const;

/** getClass. The server answers only the class's parent or tutor (TX20); anyone else gets 403 or 404. */
export function useClass(classId: string) {
  return useQuery({
    queryKey: classKey(classId),
    queryFn: () => call(api.GET("/classes/{classId}", { params: { path: { classId } } })),
  });
}

/** Every session of the class: the week view needs all of them to number weeks and sessions. */
export function useSessions(classId: string) {
  return useQuery({
    queryKey: sessionsKey(classId),
    queryFn: async () => {
      const sessions: Session[] = [];
      let cursor: string | null = null;
      for (let page = 0; page < MAX_PAGES; page += 1) {
        const result: { items: Session[]; nextCursor: string | null } = await call(
          api.GET("/classes/{classId}/sessions", {
            params: { path: { classId }, query: { limit: PAGE_LIMIT, ...(cursor ? { cursor } : {}) } },
          }),
        );
        sessions.push(...result.items);
        cursor = result.nextCursor;
        if (!cursor) break;
      }
      return sessions;
    },
  });
}

/** The tutor's public name for the parent's view (getTutorPublic, PARENT only). */
export function useTutorName(tutorId: string | undefined) {
  return useQuery({
    queryKey: ["tutors", "public", tutorId] as const,
    queryFn: () => call(api.GET("/tutors/{tutorId}/public-profile", { params: { path: { tutorId: tutorId! } } })),
    enabled: tutorId !== undefined,
    select: (tutor) => tutor.displayName,
  });
}
