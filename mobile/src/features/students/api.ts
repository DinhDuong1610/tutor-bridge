import { useInfiniteQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { call } from "@/shared/api/errors";
import { GRADE, type StudentForm } from "./schemas";

export const STUDENTS_KEY = ["students"] as const;
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
  useEffect(() => {
    if (command.state === "confirmed") {
      void queryClient.invalidateQueries({ queryKey: STUDENTS_KEY });
    }
  }, [command.state, queryClient]);
  return command;
}
