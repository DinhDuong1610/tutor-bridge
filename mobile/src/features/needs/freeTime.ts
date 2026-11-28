import { slotsPerBlock, type CalendarWindow, type WeekPattern } from "@/features/calendar/weekly";
import type { NeedDraftInput } from "./draftForm";

/** The child's free time as PH04's "Cần thêm" names it ("Cần thêm: ngân sách, giờ rảnh của con."). */
export const FREE_TIME = "giờ rảnh của con";

// The same check as tutor-calendar's plan.ts (#263); kept here while the two PRs sit on different branches.
export function sameWeek(a: WeekPattern, b: WeekPattern): boolean {
  return a.every(
    (slots, day) =>
      slots.length === b[day].length && slots.every((s) => b[day].some((t) => t.start === s.start && t.end === s.end)),
  );
}

/** Whether a calendar gives the child any free time: an AVAILABLE or a PREFERRED window. */
export function hasFreeTime(windows: readonly CalendarWindow[]): boolean {
  return windows.some((w) => w.kind !== "BLOCKED");
}

/**
 * PH04 "Giờ chưa đủ": a block of 7 days of the cycle has fewer free slots of the session's length than sessions
 * a week, counted as `confirmNeed` counts them (`slotsPerBlock`, #16 Q5). The server still decides. Null while a
 * term is missing or there is no free time at all: those are "Cần thêm".
 */
export function shortfall(
  windows: readonly CalendarWindow[],
  draft: Pick<NeedDraftInput, "durationMinutes" | "frequencyPer7Days" | "cycleStartDate">,
  now: Date,
): string | null {
  const { durationMinutes, frequencyPer7Days, cycleStartDate } = draft;
  if (!durationMinutes || !frequencyPer7Days || !cycleStartDate || !hasFreeTime(windows)) return null;
  const blocks = slotsPerBlock(windows, cycleStartDate, durationMinutes, now);
  return blocks.some((count) => count < frequencyPer7Days)
    ? `Giờ rảnh chưa đủ cho ${frequencyPer7Days} buổi ${durationMinutes} phút mỗi tuần. Thêm giờ rảnh hoặc giảm số buổi.`
    : null;
}
