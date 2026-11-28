import { formatDate, formatTime } from "@tutorbridge/shared";

/** A session the run proposes, as `Window` of the contract: start and end instants. */
export type Window = { start: string; end: string };

const VIETNAM_OFFSET_MS = 7 * 60 * 60 * 1000;
/** Monday first, as the approved frames list days. */
const DAY_NAMES = ["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"] as const;

/** 0 for Monday .. 6 for Sunday, in Vietnam time. */
function vietnamWeekday(instant: string): number {
  const day = new Date(new Date(instant).getTime() + VIETNAM_OFFSET_MS).getUTCDay();
  return (day + 6) % 7;
}

/** "Thứ 2", "Thứ 2 và Thứ 4", "Thứ 2, Thứ 4 và Thứ 6". */
function joinDays(days: readonly number[]): string {
  const names = days.map((day) => DAY_NAMES[day]);
  return names.length <= 1 ? (names[0] ?? "") : `${names.slice(0, -1).join(", ")} và ${names[names.length - 1]}`;
}

/**
 * The card's common time, as the approved frame writes it: "Giờ chung: đủ 8 buổi, Thứ 2 và Thứ 4,
 * 18:00–19:30". Sessions that share a time are grouped by weekday; groups of different times are joined
 * by "; ", earliest weekday first.
 */
export function commonScheduleText(windows: readonly Window[]): string {
  return `Giờ chung: đủ ${windows.length} buổi, ${scheduleGroups(windows)}`;
}

/**
 * PH07 "Giờ chung trong chu kỳ": "Đủ 8 buổi: Thứ 2 và Thứ 4, 18:00–19:30, từ 18/11 đến 14/12/2026. Giờ này
 * chưa giữ chỗ." The first day drops its year when the last one is in the same year.
 */
export function detailScheduleText(windows: readonly Window[]): string {
  const starts = windows.map((w) => w.start).sort((a, b) => new Date(a).getTime() - new Date(b).getTime());
  const first = formatDate(starts[0]);
  const last = formatDate(starts[starts.length - 1]);
  const from = first.slice(6) === last.slice(6) ? first.slice(0, 5) : first;
  return `Đủ ${windows.length} buổi: ${scheduleGroups(windows)}, từ ${from} đến ${last}. Giờ này chưa giữ chỗ.`;
}

/** "Thứ 2 và Thứ 4, 18:00–19:30; Thứ 6, 19:30–21:00": days grouped by time, earliest weekday first. */
function scheduleGroups(windows: readonly Window[]): string {
  const days = new Map<string, Set<number>>();
  for (const w of windows) {
    const time = `${formatTime(w.start)}–${formatTime(w.end)}`;
    const set = days.get(time) ?? new Set<number>();
    set.add(vietnamWeekday(w.start));
    days.set(time, set);
  }
  const groups = [...days.entries()]
    .map(([time, set]) => ({ time, days: [...set].sort((a, b) => a - b) }))
    .sort((a, b) => a.days[0] - b.days[0] || a.time.localeCompare(b.time))
    .map((group) => `${joinDays(group.days)}, ${group.time}`);
  return groups.join("; ");
}

const SHORT_DAYS = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"] as const;

/** The date block of a session card, Vietnam time: { weekday: "T4", day: "18" }. */
export function sessionDate(instant: string): { weekday: string; day: string } {
  const local = new Date(new Date(instant).getTime() + VIETNAM_OFFSET_MS);
  return { weekday: SHORT_DAYS[(local.getUTCDay() + 6) % 7], day: String(local.getUTCDate()) };
}

/** "18:00–19:30", Vietnam time. */
export function sessionTime(window: Window): string {
  return `${formatTime(window.start)}–${formatTime(window.end)}`;
}

/** Scope codes the server sends in `verificationScopes`, by the name the approved frame shows. */
const SCOPE_NAMES: Record<string, string> = { MATH8_ALGEBRA: "Toán 8, đại số" };

/** "Đã xác minh: Toán 8, đại số" for the scopes the app knows; a scope it does not know shows nothing. */
export function verifiedText(scopes: readonly string[]): string | null {
  const names = scopes.map((scope) => SCOPE_NAMES[scope]).filter((name): name is string => name !== undefined);
  return names.length === 0 ? null : `Đã xác minh: ${names.join("; ")}`;
}

/** PH07 lists each verified scope on its own line: the first two, then "và n phạm vi khác" (worst-case frame). */
export function verifiedLabels(scopes: readonly string[]): { labels: string[]; more: string | null } {
  const names = scopes.map((scope) => SCOPE_NAMES[scope]).filter((name): name is string => name !== undefined);
  const labels = names.slice(0, 2).map((name) => `Đã xác minh: ${name}`);
  const rest = names.length - labels.length;
  return { labels, more: rest > 0 ? `và ${rest} phạm vi khác` : null };
}

export const MAX_REASONS = 3;

/** The reasons the card lists: the first three, then "và n lý do khác" (PH06.md, Hành vi). */
export function shownReasons(reasons: readonly string[]): { lines: string[]; more: string | null } {
  const lines = reasons.slice(0, MAX_REASONS);
  const rest = reasons.length - lines.length;
  return { lines, more: rest > 0 ? `và ${rest} lý do khác` : null };
}
