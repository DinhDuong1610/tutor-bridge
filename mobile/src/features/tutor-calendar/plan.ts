import {
  DAY_NAMES,
  HORIZON_DAYS,
  MAX_WINDOWS,
  addDays,
  spreadWeek,
  type CalendarWindow,
  type Slot,
  type WeekPattern,
} from "@/features/calendar/weekly";

/**
 * From this many windows GS04 warns before saving ("Dữ liệu xấu nhất", 196 in the frame).
 * TBD #69: the frame shows the warning but no threshold; 180 is the agent's choice.
 */
export const NEAR_LIMIT = 180;

const label = (s: Slot) => `${s.start}–${s.end}`;
const same = (a: Slot, b: Slot) => a.start === b.start && a.end === b.end;

/** "18/11/2026" from "2026-11-18". */
function dateLabel(date: string): string {
  const [year, month, day] = date.split("-");
  return `${day}/${month}/${year}`;
}

/** Start of {@code date} in Vietnam time, which has no daylight saving. */
function dayStart(date: string): number {
  return Date.parse(`${date}T00:00:00+07:00`);
}

const iso = (ms: number) => new Date(ms).toISOString().replace(".000Z", "Z");

/** "Xem lại": the 56 days the pattern is spread over (OP-26), first and last day. */
export function applyRange(today: string): string {
  return `Áp dụng từ ${dateLabel(today)} đến ${dateLabel(addDays(today, HORIZON_DAYS - 1))}`;
}

/** "Xem lại": "Mỗi tuần: Thứ 2 18:00–21:00; Thứ 7 08:00–11:00 và 14:00–17:00." Days without a slot are left out. */
export function weeklySummary(week: WeekPattern): string {
  const days = DAY_NAMES.flatMap((name, day) =>
    week[day].length === 0 ? [] : [`${name} ${week[day].map(label).join(" và ")}`],
  );
  // TBD #69 (Đính): no approved copy for a week with no free time; this is the proposal.
  return days.length === 0 ? "Mỗi tuần: không có giờ rảnh." : `Mỗi tuần: ${days.join("; ")}.`;
}

/** Slots on the screen the server does not hold yet: the draft chips ("Bạn có {n} khung giờ chưa xác nhận"). */
export function draftCount(value: WeekPattern, saved: WeekPattern): number {
  return value.reduce((sum, slots, day) => sum + slots.filter((s) => !saved[day].some((t) => same(s, t))).length, 0);
}

export function sameWeek(a: WeekPattern, b: WeekPattern): boolean {
  return a.every((slots, day) => slots.length === b[day].length && slots.every((s) => b[day].some((t) => same(s, t))));
}

/**
 * What a save sends: the available pattern, and the PREFERRED one read from the stored calendar (GS04 v1 has
 * no input for it), over the 56 days from today (OP-26); plus every stored BLOCKED window still ahead, cut to
 * those days, so saving never drops a day off or a busy time (#69 Q2, TBD Đính). The server takes windows from
 * the start of today to the start of day 57, Vietnam time (AC-UC07-01).
 */
export function windowsToSave(
  available: WeekPattern,
  preferred: WeekPattern,
  stored: readonly CalendarWindow[],
  today: string,
): CalendarWindow[] {
  const from = dayStart(today);
  const to = dayStart(addDays(today, HORIZON_DAYS));
  const blocked = stored.flatMap((w): CalendarWindow[] => {
    if (w.kind !== "BLOCKED") return [];
    const start = Math.max(Date.parse(w.start), from);
    const end = Math.min(Date.parse(w.end), to);
    if (end <= start) return [];
    return [{
      start: start === Date.parse(w.start) ? w.start : iso(start),
      end: end === Date.parse(w.end) ? w.end : iso(end),
      kind: "BLOCKED",
    }];
  });
  return [...spreadWeek(available, preferred, today), ...blocked];
}

/** "Tổng {n} khoảng rảnh": the windows that are free time, AVAILABLE or PREFERRED. */
export function freeCount(windows: readonly CalendarWindow[]): number {
  return windows.filter((w) => w.kind !== "BLOCKED").length;
}

/**
 * The warning of the "Dữ liệu xấu nhất" frame, in 8 weeks since OP-26 (the frame still says 28 days). Past 200
 * the server refuses the calendar, so "Xem lại" is blocked; TBD #69: no approved copy for that case, so it
 * keeps the frame's words with "vượt" for "gần".
 */
export function limitNotice(windows: number): { blocks: boolean; message: string } | null {
  if (windows < NEAR_LIMIT) return null;
  if (windows > MAX_WINDOWS) {
    return {
      blocks: true,
      message: `Lịch này tạo ${windows} khoảng rảnh trong 8 tuần, vượt mức tối đa ${MAX_WINDOWS}. Gộp các khung sát nhau hoặc bỏ bớt giờ.`,
    };
  }
  return {
    blocks: false,
    message: `Lịch này tạo ${windows} khoảng rảnh trong 8 tuần, gần mức tối đa ${MAX_WINDOWS}. Gộp các khung sát nhau để thêm được giờ.`,
  };
}
