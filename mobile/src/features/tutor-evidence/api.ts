import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { api } from "@/shared/api/client";
import { useCommand } from "@/shared/api/command";
import { call } from "@/shared/api/errors";
import { TUTOR_HOME_KEY } from "../tutor-home/api";
import { TUTOR_PROFILE_KEY } from "../tutor-profile/api";

export type Submission = { profileVersionId: string; etag: string; evidenceUploadIds: string[] };

/**
 * submitTutorProfile (UC03, FR-07): freezes the newest draft with its READY evidence. If-Match is the
 * ETag of the draft the screen read (INV-23); the input carries it, so a retry after an unknown outcome
 * resends the same key, body and precondition (INV-20). A confirmed submission refreshes GS01 and GS02.
 */
export function useSubmitTutorProfile() {
  const queryClient = useQueryClient();
  const command = useCommand(({ profileVersionId, etag, evidenceUploadIds }: Submission, key: string) =>
    call(
      api.POST("/me/tutor-profile/submit", {
        params: { header: { "Idempotency-Key": key, "If-Match": etag } },
        body: { profileVersionId, evidenceUploadIds },
      }),
    ),
  );
  const submitted = command.state === "confirmed" ? command.result : undefined;
  useEffect(() => {
    if (submitted) {
      void queryClient.invalidateQueries({ queryKey: TUTOR_HOME_KEY });
      void queryClient.invalidateQueries({ queryKey: TUTOR_PROFILE_KEY });
    }
  }, [submitted, queryClient]);
  return command;
}
