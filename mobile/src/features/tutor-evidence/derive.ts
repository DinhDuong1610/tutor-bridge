import type { IconName } from "@/shared/ui/Icon";
import type { StatusTone } from "@/shared/ui/StatusLabel";
import type { TutorProfile, TutorProfileData } from "../tutor-profile/api";
import { scopeLabel } from "../tutor-profile/schemas";

type Label = { label: string; tone: StatusTone; icon: IconName };

// Labels as the GS03 frames draw them.
export const NOT_SENT: Label = { label: "Chưa gửi duyệt", tone: "neutral", icon: "edit" };
export const WAITING: Label = { label: "Đang chờ xác minh", tone: "info", icon: "schedule" };
export const APPROVED: Label = { label: "Đã duyệt", tone: "success", icon: "check_circle" };
export const NEEDS_INFO: Label = { label: "Cần bổ sung", tone: "warning", icon: "warning" };
export const REJECTED: Label = { label: "Chưa được duyệt", tone: "danger", icon: "error" };

export type EvidenceMode =
  /** No profile version yet: GS03 has nothing to send (Đính, Q14 (1) on #14). */
  | { kind: "noProfile" }
  /**
   * Evidence can be added and sent. `draft` is the newest version when it is a DRAFT; null when the newest
   * version needs information and is still PENDING, so "Gửi lại" saves it as the next draft first.
   */
  | {
      kind: "collect";
      draft: { profile: TutorProfile; etag: string } | null;
      needsInfo: { profile: TutorProfile; etag: string; reason: string } | null;
    }
  | { kind: "waiting"; scope: string }
  | { kind: "verified"; scope: string | null }
  | { kind: "rejected"; reason: string | null; stillPublic: boolean };

/**
 * What GS03 shows for the newest own version and the own verification (OP-23, OP-24). The verification
 * read is the newest one, so it may belong to an earlier version than the newest draft.
 */
export function deriveEvidenceMode({ profile, verification }: TutorProfileData): EvidenceMode {
  if (profile === null) return { kind: "noProfile" };
  const newest = profile.data;
  const ofNewest = verification !== null && verification.profileVersionId === newest.id ? verification : null;
  const stillPublic = (verification?.approvedVersion ?? null) !== null;

  switch (newest.status) {
    case "DRAFT": {
      // A draft written after "Cần bổ sung": the older version waits for this one (OP-24).
      const asked = verification?.state === "NEEDS_INFO" ? verification.reason : null;
      return {
        kind: "collect",
        draft: { profile: newest, etag: profile.etag },
        needsInfo: asked === null ? null : { profile: newest, etag: profile.etag, reason: asked },
      };
    }
    case "PENDING":
      if (ofNewest?.state === "NEEDS_INFO") {
        return { kind: "collect", draft: null, needsInfo: { profile: newest, etag: profile.etag, reason: ofNewest.reason ?? "" } };
      }
      return { kind: "waiting", scope: scopeLabel(ofNewest?.scope) };
    case "APPROVED":
      return { kind: "verified", scope: ofNewest?.state === "VERIFIED" ? ofNewest.scope : null };
    default:
      return { kind: "rejected", reason: ofNewest?.state === "REJECTED" ? ofNewest.reason : null, stillPublic };
  }
}

/** "PDF, 2,4 MB" / "DOCX, 640 KB" as in the frames. */
export function describeFile(name: string, mediaType: string, sizeBytes: number): string {
  const kind =
    mediaType === "application/pdf" ? "PDF" : mediaType === "image/jpeg" ? "JPEG" : mediaType === "image/png" ? "PNG" : extension(name);
  const size =
    sizeBytes >= 1024 * 1024
      ? `${(sizeBytes / (1024 * 1024)).toFixed(1).replace(".", ",")} MB`
      : `${Math.max(1, Math.round(sizeBytes / 1024))} KB`;
  return kind ? `${kind}, ${size}` : size;
}

function extension(name: string) {
  const dot = name.lastIndexOf(".");
  return dot > 0 ? name.slice(dot + 1).toUpperCase() : "";
}
