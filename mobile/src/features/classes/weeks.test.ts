import {
  classWeeks,
  dateBlock,
  lastWeek,
  openingWeek,
  sessionLook,
  sessionsInWeek,
  vietnamDay,
  weekdayName,
  weekLabel,
  type Session,
} from "./weeks";

// Two sessions a week, Wednesday and Monday 18:00–19:30 Vietnam time, from Wed 18/11/2026 (CL02 Figma).
const at = (date: string) => ({ start: `${date}T11:00:00Z`, end: `${date}T12:30:00Z` });
const session = (ordinal: number, date: string, state: Session["state"] = "SCHEDULED"): Session => ({
  id: `00000000-0000-4000-8000-${String(ordinal).padStart(12, "0")}`,
  classId: "3e0f7c61-5d84-4dba-8d76-903c3f5e2143",
  ordinal,
  revision: 1,
  state,
  during: at(date),
});
const DATES = ["2026-11-18", "2026-11-23", "2026-11-25", "2026-11-30", "2026-12-02", "2026-12-07", "2026-12-09", "2026-12-14"];
const SESSIONS = DATES.map((date, i) => session(i + 1, date));
const WEEKS = classWeeks(SESSIONS)!;

describe("CL02 weeks", () => {
  it("counts days on the Vietnam calendar, not UTC", () => {
    // 23:30 on 17/11 UTC is 06:30 on 18/11 in Vietnam.
    expect(vietnamDay("2026-11-17T23:30:00Z")).toBe(vietnamDay("2026-11-18T05:00:00Z"));
    expect(weekdayName(vietnamDay("2026-11-18T11:00:00Z"))).toBe("Thứ 4");
    expect(dateBlock(vietnamDay("2026-11-23T11:00:00Z"))).toEqual({ weekday: "T2", day: "23", month: "Th11" });
    expect(weekdayName(vietnamDay("2026-11-22T11:00:00Z"))).toBe("Chủ nhật");
  });

  it("labels weeks from the first session's day, numbered inside the 28-day cycle only (Figma)", () => {
    expect(weekLabel(WEEKS, 0)).toBe("Tuần 1: 18/11 – 24/11/2026");
    expect(weekLabel(WEEKS, 1)).toBe("Tuần 2: 25/11 – 01/12/2026");
    expect(weekLabel(WEEKS, 4)).toBe("Tuần 16/12 – 22/12/2026");
  });

  it("puts each session in its week, earliest first", () => {
    expect(sessionsInWeek(WEEKS, [...SESSIONS].reverse(), 0).map((s) => s.ordinal)).toEqual([1, 2]);
    expect(sessionsInWeek(WEEKS, SESSIONS, 3).map((s) => s.ordinal)).toEqual([7, 8]);
    expect(sessionsInWeek(WEEKS, SESSIONS, 4)).toEqual([]);
  });

  it("opens on the week holding today, or the first week before the class starts (#73 Q2)", () => {
    expect(openingWeek(WEEKS, new Date("2026-11-10T03:00:00Z"))).toBe(0);
    expect(openingWeek(WEEKS, new Date("2026-11-27T03:00:00Z"))).toBe(1);
    expect(openingWeek(WEEKS, new Date("2026-12-17T03:00:00Z"))).toBe(4);
  });

  it("lets the reader move up to the later of the last session's week and today's", () => {
    expect(lastWeek(WEEKS, new Date("2026-11-27T03:00:00Z"))).toBe(3);
    expect(lastWeek(WEEKS, new Date("2026-12-17T03:00:00Z"))).toBe(4);
  });

  it("shows a session as past once it has ended, and cancelled before anything else", () => {
    const first = SESSIONS[0];
    expect(sessionLook(first, new Date("2026-11-18T12:29:00Z"))).toBe("scheduled");
    expect(sessionLook(first, new Date("2026-11-18T12:30:00Z"))).toBe("past");
    expect(sessionLook(session(1, "2026-11-18", "CANCELLED"), new Date("2026-11-01T00:00:00Z"))).toBe("cancelled");
  });

  it("has no weeks for a class without sessions", () => {
    expect(classWeeks([])).toBeNull();
  });
});
