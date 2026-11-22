import type { StatusTone } from "@/shared/ui/StatusLabel";
import type { IconName } from "@/shared/ui/Icon";
import type { TutorProfile, TutorProfileData } from "./api";

/** `03-thanh-phan.md`, "Hồ sơ gia sư `TutorProfileDraft.status`". */
export const STATUS_LABELS: Record<TutorProfile["status"], { label: string; tone: StatusTone; icon: IconName }> = {
  DRAFT: { label: "Nháp, chưa gửi duyệt", tone: "neutral", icon: "edit" },
  PENDING: { label: "Đang chờ xác minh", tone: "info", icon: "schedule" },
  APPROVED: { label: "Đã duyệt", tone: "success", icon: "verified_fill" },
  REJECTED: { label: "Chưa được duyệt", tone: "danger", icon: "error" },
  RETIRED: { label: "Bản cũ", tone: "neutral", icon: "info" },
};

export type ProfileMode =
  /** OP-24: a version waiting for its verifier cannot be edited; GS02 shows it read-only. */
  | { kind: "waiting"; profile: TutorProfile; stillPublic: boolean }
  | {
      kind: "edit";
      /** Null before the first save: the save sends If-None-Match: *. */
      profile: TutorProfile | null;
      etag: string | null;
      /**
       * The approved version parents see, when it is the version being edited (frame "Đã duyệt, đang
       * sửa"). Once a newer draft exists its text is no longer readable here (TBD Q1).
       */
      publicVersion: TutorProfile | null;
      publicScope: string | null;
      /** Editing while an approved version stays public: the frame's "Bạn đang sửa phần chuyên môn" notice. */
      editingPublic: boolean;
      /** The label above the form; none before the first save. */
      label: { label: string; tone: StatusTone; icon: IconName } | null;
      /** The verifier's answer to this version, in GS01's approved words (Đính, 17/11/2026), reason verbatim. */
      verdict: { tone: "warning" | "danger"; message: string } | null;
    };

const DRAFT_LABEL = STATUS_LABELS.DRAFT;
const NEEDS_INFO_LABEL = { label: "Cần bổ sung", tone: "warning", icon: "warning" } as const;

/**
 * What GS02 shows for the newest own version and the own verification (OP-24, contract
 * `TutorVerificationView`). PENDING is locked unless its own verification needs information: the safe
 * reading, the same as saveTutorProfile's (a PENDING version without such a verification is waiting).
 */
export function deriveProfileMode({ profile, verification }: TutorProfileData): ProfileMode {
  const approvedVersion = verification?.approvedVersion ?? null;
  if (profile === null) {
    return {
      kind: "edit",
      profile: null,
      etag: null,
      publicVersion: null,
      publicScope: null,
      editingPublic: false,
      label: null,
      verdict: null,
    };
  }
  const own = verification !== null && verification.profileVersionId === profile.data.id ? verification : null;
  if (profile.data.status === "PENDING" && own?.state !== "NEEDS_INFO") {
    return { kind: "waiting", profile: profile.data, stillPublic: approvedVersion !== null };
  }
  const isPublic = profile.data.status === "APPROVED";
  return {
    kind: "edit",
    profile: profile.data,
    etag: profile.etag,
    publicVersion: isPublic ? profile.data : null,
    publicScope: isPublic && own?.state === "VERIFIED" ? own.scope : null,
    editingPublic: isPublic || approvedVersion !== null,
    // The form is the next draft when the newest version is approved (frame "Đã duyệt, đang sửa").
    label: own?.state === "NEEDS_INFO" ? NEEDS_INFO_LABEL : isPublic ? DRAFT_LABEL : STATUS_LABELS[profile.data.status],
    verdict:
      own?.state === "NEEDS_INFO"
        ? { tone: "warning", message: `Người xác minh cần thêm thông tin. Lý do: ${own.reason}` }
        : own?.state === "REJECTED"
          ? { tone: "danger", message: `Hồ sơ chưa được duyệt. Lý do: ${own.reason}` }
          : null,
  };
}
