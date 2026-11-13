import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import type { components } from "@tutorbridge/api-client";
import { useEffect } from "react";
import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { call, callWithEtag } from "@/shared/api/errors";
import { GRADE, type StudentForm } from "./schemas";

export const STUDENTS_KEY = ["students"] as const;

/**
 * The profile the server just confirmed, kept for the "Đã lưu hồ sơ {tên}." notice so it does not
 * depend on the list reloading. In memory only; cleared with the rest of the cache on sign-out.
 */
export const savedStudentKey = (studentId: string) => ["students", "saved", studentId] as const;
const STUDENTS_PAGE_LIMIT = 50; // maxItems of StudentPage

/** The signed-in parent's children. The server takes the owner from the session (AC-UC02-02). */
export function useStudents() {
  return useInfiniteQuery({
    queryKey: STUDENTS_KEY,
    initialPageParam: null as string | null,
    queryFn: ({ pageParam }) =>
      call(api.GET("/students", { params: { query: { limit: STUDENTS_PAGE_LIMIT, ...(pageParam ? { cursor: pageParam } : {}) } } })),
    getNextPageParam: (page) => page.nextCursor,
  });
}

export const studentProfileKey = (studentId: string) => ["students", "profile", studentId] as const;

/** getStudentProfile with its ETag. The server checks the owner; another family's id gets 403 or 404 (AC-UC02-02). */
export function useStudentProfile(studentId: string) {
  return useQuery({
    queryKey: studentProfileKey(studentId),
    queryFn: () => callWithEtag(api.GET("/students/{studentId}", { params: { path: { studentId } } })),
  });
}

export type Student = components["schemas"]["Student"];
type StudentPatch = components["schemas"]["StudentPatchInput"];

/**
 * Only the fields that differ from the loaded profile (StudentPatchInput). Empty when nothing
 * changed; the contract needs at least one field. Grade is fixed at 8, so it is never sent.
 */
export function changedFields(loaded: Student, form: StudentForm): StudentPatch {
  const changes: StudentPatch = {};
  if (form.displayName !== loaded.displayName) changes.displayName = form.displayName;
  if (form.shortGoal !== (loaded.shortGoal ?? "")) changes.shortGoal = form.shortGoal;
  return changes;
}

export type StudentUpdate = { studentId: string; etag: string; changes: StudentPatch };

/**
 * patchStudent with If-Match (INV-23). The ETag travels in the input, so a retry after an unknown
 * outcome resends the same key, the same changes and the same ETag.
 */
export function useUpdateStudent() {
  const queryClient = useQueryClient();
  const command = useCommand(({ studentId, etag, changes }: StudentUpdate, key: string) =>
    callWithEtag(
      api.PATCH("/students/{studentId}", {
        params: { path: { studentId }, header: { "Idempotency-Key": key, "If-Match": etag } },
        body: changes,
      }),
    ),
  );
  const updated = command.state === "confirmed" ? command.result : undefined;
  useEffect(() => {
    if (updated) {
      queryClient.setQueryData(savedStudentKey(updated.data.id), updated.data);
      // The saved revision with the server's new ETag, so the next edit starts from it instead of
      // an older cached revision (Codex review of #138).
      queryClient.setQueryData(studentProfileKey(updated.data.id), updated);
      void queryClient.invalidateQueries({ queryKey: STUDENTS_KEY, exact: true });
    }
  }, [updated, queryClient]);
  return command;
}

/** createStudent. No owner in the body: the contract does not accept one (INV-03). */
export function useCreateStudent() {
  const queryClient = useQueryClient();
  const command = useCommand((input: StudentForm, key: string) =>
    call(
      api.POST("/students", {
        params: { header: { "Idempotency-Key": key } },
        body: { displayName: input.displayName, grade: GRADE, shortGoal: input.shortGoal },
      }),
    ),
  );
  const created = command.state === "confirmed" ? command.result : undefined;
  useEffect(() => {
    if (created) {
      queryClient.setQueryData(savedStudentKey(created.id), created);
      void queryClient.invalidateQueries({ queryKey: STUDENTS_KEY, exact: true });
    }
  }, [created, queryClient]);
  return command;
}
