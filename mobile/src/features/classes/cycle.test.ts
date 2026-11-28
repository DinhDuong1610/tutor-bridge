import { cycleHeading, cycleMarks, nextSession, sessionWhen } from "./cycle";
import type { Session } from "./weeks";

// Wednesday and Monday 18:00–19:30 Vietnam time from Wed 18/11/2026, as in the CL01 Figma frames.
const DATES = ["2026-11-18", "2026-11-23", "2026-11-25", "2026-11-30", "2026-12-02", "2026-12-07", "2026-12-09", "2026-12-14"];
const session = (ordinal: number, date: string, state: Session["state"] = "SCHEDULED", start = "11:00", end = "12:30"): Session => ({
  id: `00000000-0000-4000-8000-${String(ordinal).padStart(12, "0")}`,
  classId: "3e0f7c61-5d84-4dba-8d76-903c3f5e2143",
  ordinal,
  revision: 1,
  state,
  during: { start: `${date}T${start}:00Z`, end: `${date}T${end}:00Z` },
});
const SESSIONS = DATES.map((date, i) => session(i + 1, date));

describe("cycleHeading", () => {
  it("names the weekdays in the order the class meets, one time for all, as Figma", () => {
    expect(cycleHeading(SESSIONS)).toBe("8 buổi, Thứ 4 và Thứ 2, 18:00–19:30, từ 18/11 đến 14/12/2026");
    // The order does not depend on the order the API listed them.
    expect(cycleHeading([...SESSIONS].reverse())).toBe("8 buổi, Thứ 4 và Thứ 2, 18:00–19:30, từ 18/11 đến 14/12/2026");
  });

  it("gives each weekday its own hours when they differ, and three weekdays with a comma", () => {
    const sessions = [
      session(1, "2026-11-16", "SCHEDULED", "10:00", "11:30"), // Mon 17:00–18:30
      session(2, "2026-11-18"), // Wed 18:00–19:30
      session(3, "2026-11-20"), // Fri 18:00–19:30
    ];
    expect(cycleHeading(sessions)).toBe(
      "3 buổi, Thứ 2 17:00–18:30, Thứ 4 18:00–19:30 và Thứ 6 18:00–19:30, từ 16/11 đến 20/11/2026",
    );
  });

  it("has nothing to say without sessions", () => {
    expect(cycleHeading([])).toBeNull();
  });
});

describe("cycleMarks", () => {
  it("puts each session in its week of the cycle, cancelled and past ones as such", () => {
    const sessions = SESSIONS.map((s) => (s.ordinal === 4 ? { ...s, state: "CANCELLED" as const } : s));
    // Fri 27/11: sessions 1–3 are over.
    expect(cycleMarks(sessions, new Date("2026-11-27T03:00:00Z"))).toEqual([
      ["past", "past"],
      ["past", "cancelled"],
      ["scheduled", "scheduled"],
      ["scheduled", "scheduled"],
    ]);
  });

  it("gives four empty weeks without sessions", () => {
    expect(cycleMarks([], new Date())).toEqual([[], [], [], []]);
  });
});

describe("nextSession", () => {
  it("is the earliest scheduled session not ended: a cancelled one is skipped, one going on still counts", () => {
    const sessions = SESSIONS.map((s) => (s.ordinal === 1 ? { ...s, state: "CANCELLED" as const } : s));
    expect(nextSession(sessions, new Date("2026-11-10T03:00:00Z"))?.ordinal).toBe(2);
    // Mon 23/11 18:30, session 2 still going on; at its end the next one is session 3.
    expect(nextSession(sessions, new Date("2026-11-23T11:30:00Z"))?.ordinal).toBe(2);
    expect(nextSession(sessions, new Date("2026-11-23T12:30:00Z"))?.ordinal).toBe(3);
  });

  it("is none after the last session", () => {
    expect(nextSession(SESSIONS, new Date("2026-12-14T12:30:00Z"))).toBeUndefined();
  });
});

it("sessionWhen reads as CL01's 'Buổi tới' in Vietnam time", () => {
  expect(sessionWhen(SESSIONS[0])).toBe("Thứ 4, 18/11/2026, 18:00–19:30");
});
