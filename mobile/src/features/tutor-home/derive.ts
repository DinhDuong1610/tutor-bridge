import type { components } from "@tutorbridge/api-client";
import { formatVnd } from "@tutorbridge/shared";

export type TutorProfile = components["schemas"]["TutorProfileDraft"];
export type TutorVerification = components["schemas"]["TutorVerificationView"];
export type TutorCalendar = components["schemas"]["EffectiveCalendar"];

/** What GS01 reads. `null` is "not there yet": never saved, never submitted, no calendar (404). */
export type TutorHomeData = {
  profile: TutorProfile | null;
  verification: TutorVerification | null;
  calendar: TutorCalendar | null;
};

export type TaskTarget = "profile" | "evidence" | "calendar";

/** A card of "Việc cần làm" in the approved GS01 v2 frames. */
export type Task =
  | { kind: "step"; step: 1 | 2 | 3; title: string; body: string; action: string; target: TaskTarget }
  | { kind: "waiting"; title: string; body: string }
  | { kind: "needsInfo" | "rejected"; title: string; reason: string; action: string; target: TaskTarget }
  | { kind: "done"; title: string };

export type Visibility =
  | { kind: "hidden"; message: string }
  | { kind: "verified"; scope: string; message: string };

export type TutorHome = {
  tasks: Task[];
  /** Block "Hồ sơ khi phụ huynh tìm"; null hides the whole block. */
  visibility: Visibility | null;
  /** The approved frames put the block above "Việc cần làm" until the profile is verified, below after. */
  visibilityFirst: boolean;
};

const STEP_1: Task = {
  kind: "step",
  step: 1,
  title: "Hoàn thiện hồ sơ chuyên môn và học phí",
  body: "Mô tả cách dạy, kinh nghiệm, học phí cho 60 phút.",
  action: "Mở hồ sơ",
  target: "profile",
};
const STEP_2: Task = {
  kind: "step",
  step: 2,
  title: "Tải minh chứng và gửi xác minh",
  body: "PDF, JPEG hoặc PNG, tối đa 10 tệp.",
  action: "Tải minh chứng",
  target: "evidence",
};
const STEP_3: Task = {
  kind: "step",
  step: 3,
  title: "Xác nhận lịch rảnh",
  body: "Phụ huynh chỉ thấy bạn khi có khung giờ rảnh đã xác nhận đủ cho chu kỳ 28 ngày.",
  action: "Nhập lịch rảnh",
  target: "calendar",
};
const PROFILE_DONE: Task = { kind: "done", title: "Hồ sơ chuyên môn và học phí" };

const NOT_LISTED = "Hồ sơ của bạn chưa xuất hiện khi phụ huynh tìm gia sư: ";
const NOT_LISTED_SHORT = "Hồ sơ chưa xuất hiện khi phụ huynh tìm: ";

/**
 * Step 3 counts as done when the effective calendar still has free time after `now`: a window that
 * is not BLOCKED, minus every BLOCKED window, as TX05 reads the calendar (a session must not touch a
 * BLOCKED window; Codex, #206 P2). The app cannot check "đủ cho chu kỳ 28 ngày" (BR04); the server
 * filters on it. Agent's choice until Đính answers TB-14 Q4.
 */
export function hasOpenAvailability(calendar: TutorCalendar | null, now: Date): boolean {
  if (!calendar) return false;
  const span = (window: TutorCalendar["windows"][number]) => [new Date(window.start).getTime(), new Date(window.end).getTime()] as const;
  const blocked = calendar.windows
    .filter((window) => window.kind === "BLOCKED")
    .map(span)
    .sort((a, b) => a[0] - b[0]);
  return calendar.windows
    .filter((window) => window.kind !== "BLOCKED")
    .some((window) => {
      const [start, end] = span(window);
      // Walk the blocked windows in order, moving the start of what is left past each one it meets.
      let from = Math.max(start, now.getTime());
      for (const [blockStart, blockEnd] of blocked) {
        if (from >= end) break;
        if (blockStart > from) return true; // a free gap before this block
        from = Math.max(from, blockEnd);
      }
      return from < end;
    });
}

/** Builds GS01 from the three reads, following the state table of `docs/handoff/TB-14.md`. */
export function deriveTutorHome({ profile, verification, calendar }: TutorHomeData, now: Date): TutorHome {
  const hasCalendar = hasOpenAvailability(calendar, now);
  const calendarTasks = hasCalendar ? [] : [STEP_3];

  if (!verification) {
    const notYet = hasCalendar ? "chưa gửi xác minh." : "chưa gửi xác minh và chưa có lịch rảnh.";
    return {
      tasks: profile ? [STEP_2, ...calendarTasks, PROFILE_DONE] : [STEP_1, STEP_2, ...calendarTasks],
      visibility: { kind: "hidden", message: NOT_LISTED + notYet },
      visibilityFirst: true,
    };
  }

  if (verification.state === "VERIFIED") {
    return {
      tasks: calendarTasks,
      visibility: {
        kind: "verified",
        scope: verification.scope,
        message: hasCalendar ? verifiedMessage(profile, verification) : NOT_LISTED + "chưa có lịch rảnh.",
      },
      visibilityFirst: false,
    };
  }

  // While an approved version stays public, parents still find the tutor (OP-24 (1)); the copy for
  // that case is TBD (#166, TB-14 Q1), so the block is hidden instead of saying "chưa xuất hiện".
  const firstSubmission = verification.approvedVersion === null;
  const reason = verification.reason ?? "";
  switch (verification.state) {
    case "WAITING":
      return {
        tasks: [{ kind: "waiting", title: "Hồ sơ đang được xác minh", body: "Bạn sẽ nhận thông báo khi có kết quả." }, ...calendarTasks, PROFILE_DONE],
        visibility: firstSubmission ? { kind: "hidden", message: NOT_LISTED_SHORT + "đang chờ xác minh." } : null,
        visibilityFirst: true,
      };
    case "NEEDS_INFO":
      return {
        tasks: [{ kind: "needsInfo", title: "Người xác minh cần thêm thông tin", reason, action: "Xem và gửi lại", target: "evidence" }, ...calendarTasks, PROFILE_DONE],
        visibility: firstSubmission ? { kind: "hidden", message: NOT_LISTED_SHORT + "cần bổ sung hồ sơ." } : null,
        visibilityFirst: true,
      };
    case "REJECTED":
      return {
        tasks: [{ kind: "rejected", title: "Hồ sơ chưa được duyệt", reason, action: "Sửa hồ sơ và gửi lại", target: "evidence" }, ...calendarTasks, PROFILE_DONE],
        visibility: firstSubmission ? { kind: "hidden", message: NOT_LISTED_SHORT + "hồ sơ chưa được duyệt." } : null,
        visibilityFirst: true,
      };
  }
}

/**
 * The rate is shown only when the latest saved version is the approved one: a newer draft may have
 * another rate than the one parents see (TB-14 Q5).
 */
function verifiedMessage(profile: TutorProfile | null, verification: TutorVerification): string {
  const base = "Phụ huynh có thể thấy và gửi đề nghị cho bạn.";
  if (!profile || profile.version !== verification.approvedVersion) return base;
  return `${base} Học phí ${formatVnd(profile.rateVnd60)} / 60 phút; kinh nghiệm là tự khai.`;
}
