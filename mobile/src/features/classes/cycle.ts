import { formatTime } from "@tutorbridge/shared";
import { classWeeks, dayMonth, fullDate, sessionLook, vietnamDay, weekdayName, weekOf, type Session, type SessionLook } from "./weeks";

/** A cycle is 28 days, four numbered weeks (BR04, `CycleStrip`). */
const CYCLE_WEEKS = 4;

const byStart = (a: Session, b: Session) => Date.parse(a.during.start) - Date.parse(b.during.start);
const timeRange = (session: Session) => `${formatTime(session.during.start)}–${formatTime(session.during.end)}`;

/** "A", "A và B", "A, B và C". */
function list(parts: readonly string[]): string {
  return parts.length <= 1 ? (parts[0] ?? "") : `${parts.slice(0, -1).join(", ")} và ${parts[parts.length - 1]}`;
}

/**
 * The CL01 cycle line: "8 buổi, Thứ 4 và Thứ 2, 18:00–19:30, từ 18/11 đến 14/12/2026". Weekdays in the order the
 * class first meets on them. When the weekdays have different hours, each weekday carries its own
 * ("Thứ 2 17:00–18:30 và Thứ 4 18:00–19:30"); Figma only draws one time for all.
 */
export function cycleHeading(sessions: readonly Session[]): string | null {
  const weeks = classWeeks(sessions);
  if (!weeks) return null;
  const firstOfWeekday = new Map<string, string>();
  for (const session of [...sessions].sort(byStart)) {
    const weekday = weekdayName(vietnamDay(session.during.start));
    if (!firstOfWeekday.has(weekday)) firstOfWeekday.set(weekday, timeRange(session));
  }
  const times = new Set(firstOfWeekday.values());
  const when =
    times.size === 1
      ? `${list([...firstOfWeekday.keys()])}, ${[...times][0]}`
      : list([...firstOfWeekday].map(([weekday, time]) => `${weekday} ${time}`));
  return `${sessions.length} buổi, ${when}, từ ${dayMonth(weeks.firstDay)} đến ${fullDate(weeks.lastDay)}`;
}

/** The marks of `Tuần 1`–`Tuần 4`: one per session of that week, earliest first, by how CL02 shows it. */
export function cycleMarks(sessions: readonly Session[], now: Date): SessionLook[][] {
  const weeks = classWeeks(sessions);
  const marks: SessionLook[][] = Array.from({ length: CYCLE_WEEKS }, () => []);
  if (!weeks) return marks;
  for (const session of [...sessions].sort(byStart)) {
    const week = weekOf(weeks, vietnamDay(session.during.start));
    if (week >= 0 && week < CYCLE_WEEKS) marks[week].push(sessionLook(session, now));
  }
  return marks;
}

/** The next session: the earliest SCHEDULED one that has not ended, as listClasses counts it (OP-27). */
export function nextSession(sessions: readonly Session[], now: Date): Session | undefined {
  return [...sessions]
    .sort(byStart)
    .find((session) => session.state === "SCHEDULED" && Date.parse(session.during.end) > now.getTime());
}

/** "Thứ 4, 18/11/2026, 18:00–19:30" (CL01 "Buổi tới"). */
export function sessionWhen(session: Session): string {
  const day = vietnamDay(session.during.start);
  return `${weekdayName(day)}, ${fullDate(day)}, ${timeRange(session)}`;
}
