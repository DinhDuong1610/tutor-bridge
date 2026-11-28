/**
 * The weekly pattern behind PH04 and GS04 (`WeekAvailability`), and the finite windows the calendar
 * API stores (`CalendarWindowInput`, UTC, `[start, end)`).
 *
 * OP-26 (Đính, 12/11/2026): the pattern is spread over 56 days from today, Vietnam time, at most 200
 * windows. Asia/Ho_Chi_Minh is UTC+7 all year, so a fixed offset is exact. Dates are "YYYY-MM-DD"
 * in Vietnam; times are "HH:MM", 24 hours, within one day.
 */

export type Slot = { start: string; end: string };

/** Seven lists of slots, index 0 = Thứ 2 … 6 = Chủ nhật, each sorted by start. */
export type WeekPattern = Slot[][];

export type WindowKind = "AVAILABLE" | "PREFERRED" | "BLOCKED";

/** `CalendarWindowInput`. */
export type CalendarWindow = { start: string; end: string; kind: WindowKind };

export const HORIZON_DAYS = 56;
export const MAX_WINDOWS = 200;
export const DAY_NAMES = ["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"] as const;

const OFFSET_MS = 7 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const TIME = /^([01]\d|2[0-3]):([0-5]\d)$/;

export function emptyWeek(): WeekPattern {
  return [[], [], [], [], [], [], []];
}

/** Minutes after midnight of "HH:MM", or null when it is not a 24-hour time. */
export function minutesOf(time: string): number | null {
  const m = TIME.exec(time.trim());
  return m ? Number(m[1]) * 60 + Number(m[2]) : null;
}

function hhmm(minutes: number): string {
  return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
}

/** A slot of the add sheet: both 24-hour times, the end after the start (AC-UC07-01). */
export function slotError(start: string, end: string): string | null {
  const from = minutesOf(start);
  const to = minutesOf(end);
  if (from === null || to === null) return "Nhập giờ theo dạng 24 giờ, ví dụ 18:00.";
  if (to <= from) return "Giờ kết thúc phải sau giờ bắt đầu.";
  return null;
}

/** The pattern with this slot added to that day, kept sorted; an identical slot is not added twice. */
export function addSlot(week: WeekPattern, day: number, slot: Slot): WeekPattern {
  const clean = { start: slot.start.trim(), end: slot.end.trim() };
  if (week[day].some((s) => s.start === clean.start && s.end === clean.end)) return week;
  return week.map((slots, d) =>
    d === day ? [...slots, clean].sort((a, b) => a.start.localeCompare(b.start) || a.end.localeCompare(b.end)) : slots,
  );
}

export function removeSlot(week: WeekPattern, day: number, slot: Slot): WeekPattern {
  return week.map((slots, d) => (d === day ? slots.filter((s) => s.start !== slot.start || s.end !== slot.end) : slots));
}

/** Today in Vietnam. */
export function vietnamDate(now: Date): string {
  return new Date(now.getTime() + OFFSET_MS).toISOString().slice(0, 10);
}

export function addDays(date: string, days: number): string {
  return new Date(Date.parse(`${date}T00:00:00Z`) + days * DAY_MS).toISOString().slice(0, 10);
}

/** 0 = Thứ 2 … 6 = Chủ nhật. */
export function weekdayOf(date: string): number {
  return (new Date(`${date}T00:00:00Z`).getUTCDay() + 6) % 7;
}

/** The UTC instant of a Vietnam date and minute of that day. */
function instant(date: string, minutes: number): number {
  return Date.parse(`${date}T00:00:00Z`) - OFFSET_MS + minutes * 60 * 1000;
}

function iso(ms: number): string {
  return new Date(ms).toISOString().replace(".000Z", "Z");
}

/**
 * OP-26: the available and preferred patterns as windows on each of the 56 days from {@code today}.
 * PREFERRED is free time too, only ranked higher (Đính, 12/11/2026), so a preferred slot need not
 * repeat as AVAILABLE.
 */
export function spreadWeek(available: WeekPattern, preferred: WeekPattern, today: string): CalendarWindow[] {
  const windows: CalendarWindow[] = [];
  for (let i = 0; i < HORIZON_DAYS; i++) {
    const date = addDays(today, i);
    const day = weekdayOf(date);
    for (const [kind, week] of [["AVAILABLE", available], ["PREFERRED", preferred]] as const) {
      for (const slot of week[day]) {
        windows.push({
          start: iso(instant(date, minutesOf(slot.start)!)),
          end: iso(instant(date, minutesOf(slot.end)!)),
          kind,
        });
      }
    }
  }
  return windows;
}

export type ReadWeek = {
  available: WeekPattern;
  preferred: WeekPattern;
  /**
   * The stored calendar is not that weekly pattern repeated: a BLOCKED window, a window over
   * midnight, or a day that differs from the same weekday of the first week. Saving from the
   * pattern would then replace those days (#70 Q2, wording pending Đính).
   */
  irregular: boolean;
};

const key = (w: CalendarWindow) => `${w.kind}|${Date.parse(w.start)}|${Date.parse(w.end)}`;

/**
 * The weekly pattern of a stored calendar, read from the 7 days starting today. Windows before today
 * are history and do not count; the comparison stops at the last stored day, since a calendar saved
 * some days ago covers fewer days ahead.
 */
export function readWeek(windows: readonly CalendarWindow[], today: string): ReadWeek {
  const available = emptyWeek();
  const preferred = emptyWeek();
  const from = instant(today, 0);
  const firstWeekEnd = instant(addDays(today, 7), 0);
  const ahead = windows.filter((w) => Date.parse(w.end) > from);
  let irregular = false;
  for (const w of ahead) {
    const start = Date.parse(w.start);
    const end = Date.parse(w.end);
    if (w.kind === "BLOCKED") {
      irregular = true;
      continue;
    }
    const date = vietnamDate(new Date(start));
    const startMin = Math.round((start - instant(date, 0)) / 60000);
    const endMin = Math.round((end - instant(date, 0)) / 60000);
    if (start < from || endMin > 24 * 60 - 1) {
      // Began before today or runs past 23:59 of its day: not a slot of one day.
      irregular = true;
      continue;
    }
    if (start < firstWeekEnd) {
      const week = w.kind === "PREFERRED" ? preferred : available;
      const day = weekdayOf(date);
      week[day] = addSlot(week, day, { start: hhmm(startMin), end: hhmm(endMin) })[day];
    }
  }
  if (!irregular && ahead.length > 0) {
    const lastEnd = Math.max(...ahead.map((w) => Date.parse(w.end)));
    const stored = new Set(ahead.map(key));
    const expected = spreadWeek(available, preferred, today).filter((w) => Date.parse(w.end) <= lastEnd);
    irregular = expected.length !== stored.size || expected.some((w) => !stored.has(key(w)));
  }
  return { available, preferred, irregular };
}

/** Free intervals as `confirmNeed` sees them: every non-BLOCKED window, less every BLOCKED one. */
function freeIntervals(windows: readonly CalendarWindow[]): [number, number][] {
  const merge = (kind: (k: WindowKind) => boolean) => {
    const spans = windows
      .filter((w) => kind(w.kind))
      .map((w) => [Date.parse(w.start), Date.parse(w.end)] as [number, number])
      .sort((a, b) => a[0] - b[0]);
    const merged: [number, number][] = [];
    for (const [s, e] of spans) {
      const last = merged[merged.length - 1];
      if (last && s <= last[1]) last[1] = Math.max(last[1], e);
      else merged.push([s, e]);
    }
    return merged;
  };
  let free = merge((k) => k !== "BLOCKED");
  for (const [bs, be] of merge((k) => k === "BLOCKED")) {
    free = free.flatMap(([s, e]): [number, number][] =>
      be <= s || bs >= e ? [[s, e]] : ([[s, bs], [be, e]] as [number, number][]).filter(([a, b]) => b > a),
    );
  }
  return free;
}

/**
 * How many sessions of {@code durationMinutes} fit in each of the 4 blocks of 7 days from
 * {@code cycleStart}, counting only time after {@code now}: the same count as `confirmNeed`
 * (`requireEnoughSlots`, #16 Q5). The server still decides; this only warns before saving.
 */
export function slotsPerBlock(
  windows: readonly CalendarWindow[],
  cycleStart: string,
  durationMinutes: number,
  now: Date,
): number[] {
  const free = freeIntervals(windows);
  const slotMs = durationMinutes * 60 * 1000;
  return [0, 1, 2, 3].map((block) => {
    const from = Math.max(instant(addDays(cycleStart, 7 * block), 0), now.getTime());
    const to = instant(addDays(cycleStart, 7 * block + 7), 0);
    return free.reduce((sum, [s, e]) => {
      const lo = Math.max(s, from);
      const hi = Math.min(e, to);
      return hi > lo ? sum + Math.floor((hi - lo) / slotMs) : sum;
    }, 0);
  });
}
