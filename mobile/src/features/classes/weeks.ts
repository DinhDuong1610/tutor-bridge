import type { components } from "@tutorbridge/api-client";

export type Session = components["schemas"]["Session"];

// Asia/Ho_Chi_Minh is UTC+7 without daylight saving (same as @tutorbridge/shared's format).
const VIETNAM_OFFSET_MS = 7 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
/** A cycle is 28 days, four numbered weeks (BR04, `CycleStrip`). */
const CYCLE_WEEKS = 4;

/** Days since the epoch on the Vietnam calendar. */
export function vietnamDay(instant: string | Date): number {
  const time = typeof instant === "string" ? Date.parse(instant) : instant.getTime();
  return Math.floor((time + VIETNAM_OFFSET_MS) / DAY_MS);
}

function dayParts(day: number) {
  const date = new Date(day * DAY_MS);
  return { day: date.getUTCDate(), month: date.getUTCMonth() + 1, year: date.getUTCFullYear(), weekday: date.getUTCDay() };
}

const two = (n: number) => String(n).padStart(2, "0");

/** "18/11". */
export function dayMonth(day: number): string {
  const p = dayParts(day);
  return `${two(p.day)}/${two(p.month)}`;
}

/** "14/12/2026". */
export function fullDate(day: number): string {
  return `${dayMonth(day)}/${dayParts(day).year}`;
}

// getUTCDay: 0 is Sunday. Words as CL02 Figma ("Thứ 4", block "T4").
const WEEKDAYS = ["Chủ nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"] as const;
const WEEKDAYS_SHORT = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"] as const;

export function weekdayName(day: number): string {
  return WEEKDAYS[dayParts(day).weekday];
}

/** The date block of a `SessionCard`: "T4", "18", "Th11". */
export function dateBlock(day: number) {
  const p = dayParts(day);
  return { weekday: WEEKDAYS_SHORT[p.weekday], day: String(p.day), month: `Th${p.month}` };
}

/** Weeks of a class: 7-day blocks from the day of its first session, as the cycle counts them. */
export type ClassWeeks = { firstDay: number; lastDay: number; count: number };

export function classWeeks(sessions: readonly Session[]): ClassWeeks | null {
  if (sessions.length === 0) return null;
  const days = sessions.map((session) => vietnamDay(session.during.start));
  const firstDay = Math.min(...days);
  const lastDay = Math.max(...days);
  return { firstDay, lastDay, count: sessions.length };
}

/** The index of the week holding `day` (0 is the first week; negative before the class starts). */
export function weekOf(weeks: ClassWeeks, day: number): number {
  return Math.floor((day - weeks.firstDay) / 7);
}

/**
 * The week the screen opens on: the one holding today, or the first week before the class starts
 * (Huy, #73 Q2, 27/11/2026).
 */
export function openingWeek(weeks: ClassWeeks, today: Date): number {
  return Math.max(0, weekOf(weeks, vietnamDay(today)));
}

/**
 * The last week the parent or tutor can move to: the later of the last session's week and today's,
 * so an empty week after the class (Figma "Tuần không có buổi") is still reachable from today.
 */
export function lastWeek(weeks: ClassWeeks, today: Date): number {
  return Math.max(weekOf(weeks, weeks.lastDay), weekOf(weeks, vietnamDay(today)), 0);
}

/** "Tuần 1: 18/11 – 24/11/2026" in the cycle, "Tuần 16/12 – 22/12/2026" after it (CL02 Figma). */
export function weekLabel(weeks: ClassWeeks, index: number): string {
  const start = weeks.firstDay + index * 7;
  const range = `${dayMonth(start)} – ${fullDate(start + 6)}`;
  return index < CYCLE_WEEKS ? `Tuần ${index + 1}: ${range}` : `Tuần ${range}`;
}

/** The sessions of one week, earliest first. */
export function sessionsInWeek(weeks: ClassWeeks, sessions: readonly Session[], index: number): Session[] {
  return sessions
    .filter((session) => weekOf(weeks, vietnamDay(session.during.start)) === index)
    .sort((a, b) => Date.parse(a.during.start) - Date.parse(b.during.start));
}

/** How a `SessionCard` shows a session (`03`): cancelled, past its end, or scheduled. */
export type SessionLook = "scheduled" | "past" | "cancelled";

export function sessionLook(session: Session, now: Date): SessionLook {
  if (session.state === "CANCELLED") return "cancelled";
  return Date.parse(session.during.end) <= now.getTime() ? "past" : "scheduled";
}
