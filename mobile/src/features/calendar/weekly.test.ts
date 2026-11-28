import {
  HORIZON_DAYS,
  addDays,
  addSlot,
  emptyWeek,
  readWeek,
  removeSlot,
  slotError,
  slotsPerBlock,
  spreadWeek,
  vietnamDate,
  weekdayOf,
  type CalendarWindow,
  type WeekPattern,
} from "./weekly";

// Thứ 4, 18/11/2026 in Vietnam (the date of the approved PH04 frames).
const TODAY = "2026-11-18";

/** PH04 frame "Đang nhập": Thứ 2, 4, 6 18:00–21:00 and Thứ 7 08:00–11:00. */
function framePattern(): WeekPattern {
  let week = emptyWeek();
  for (const day of [0, 2, 4]) week = addSlot(week, day, { start: "18:00", end: "21:00" });
  return addSlot(week, 5, { start: "08:00", end: "11:00" });
}

describe("Vietnam dates", () => {
  it("reads today in Vietnam, which is ahead of UTC by 7 hours", () => {
    expect(vietnamDate(new Date("2026-11-17T16:59:00Z"))).toBe("2026-11-17");
    expect(vietnamDate(new Date("2026-11-17T17:00:00Z"))).toBe("2026-11-18");
  });

  it("names weekdays from Thứ 2 = 0 to Chủ nhật = 6", () => {
    expect(weekdayOf("2026-11-16")).toBe(0); // Thứ 2
    expect(weekdayOf(TODAY)).toBe(2); // Thứ 4
    expect(weekdayOf("2026-11-22")).toBe(6); // Chủ nhật
    expect(addDays(TODAY, 14)).toBe("2026-12-02");
  });
});

describe("slots of the add sheet (AC-UC07-01)", () => {
  it("refuses an end at or before the start, and anything not a 24-hour time", () => {
    expect(slotError("18:00", "21:00")).toBeNull();
    expect(slotError("20:30", "19:00")).toBe("Giờ kết thúc phải sau giờ bắt đầu.");
    expect(slotError("19:00", "19:00")).toBe("Giờ kết thúc phải sau giờ bắt đầu.");
    expect(slotError("7h", "09:00")).toBe("Nhập giờ theo dạng 24 giờ, ví dụ 18:00.");
    expect(slotError("24:00", "24:30")).not.toBeNull();
  });

  it("keeps a day sorted, adds an identical slot once and removes exactly that slot", () => {
    let week = addSlot(emptyWeek(), 0, { start: "19:00", end: "21:00" });
    week = addSlot(week, 0, { start: "07:00", end: "09:00" });
    week = addSlot(week, 0, { start: "19:00", end: "21:00" });
    expect(week[0]).toEqual([
      { start: "07:00", end: "09:00" },
      { start: "19:00", end: "21:00" },
    ]);
    expect(removeSlot(week, 0, { start: "07:00", end: "09:00" })[0]).toEqual([{ start: "19:00", end: "21:00" }]);
  });
});

describe("spreadWeek (OP-26)", () => {
  it("repeats the pattern on each of the 56 days from today, in UTC", () => {
    const windows = spreadWeek(framePattern(), emptyWeek(), TODAY);

    // 8 weeks × 4 slots.
    expect(windows).toHaveLength(32);
    expect(windows.every((w) => w.kind === "AVAILABLE")).toBe(true);
    // Thứ 4 18/11 18:00–21:00 Vietnam = 11:00–14:00 UTC.
    expect(windows[0]).toEqual({ start: "2026-11-18T11:00:00Z", end: "2026-11-18T14:00:00Z", kind: "AVAILABLE" });
    // Thứ 7 21/11 08:00 Vietnam = 01:00 UTC.
    expect(windows).toContainEqual({ start: "2026-11-21T01:00:00Z", end: "2026-11-21T04:00:00Z", kind: "AVAILABLE" });
    // The last day is today + 55, the server's horizon is 57 days (CalendarService).
    const last = windows.map((w) => w.end).sort().at(-1)!;
    expect(Date.parse(last)).toBeLessThan(Date.parse(`${addDays(TODAY, HORIZON_DAYS)}T00:00:00+07:00`));
  });

  it("writes preferred slots as PREFERRED, free time ranked higher", () => {
    const preferred = addSlot(emptyWeek(), 2, { start: "19:00", end: "20:30" });
    const windows = spreadWeek(emptyWeek(), preferred, TODAY);
    expect(windows).toHaveLength(8);
    expect(windows[0]).toEqual({ start: "2026-11-18T12:00:00Z", end: "2026-11-18T13:30:00Z", kind: "PREFERRED" });
  });
});

describe("readWeek", () => {
  it("reads back the pattern it spread, as regular", () => {
    const preferred = addSlot(emptyWeek(), 5, { start: "09:00", end: "10:00" });
    const read = readWeek(spreadWeek(framePattern(), preferred, TODAY), TODAY);
    expect(read).toEqual({ available: framePattern(), preferred, irregular: false });
  });

  it("reads a calendar saved days ago from today, ignoring the past", () => {
    const savedEarlier = spreadWeek(framePattern(), emptyWeek(), "2026-11-10");
    const read = readWeek(savedEarlier, TODAY);
    expect(read.available).toEqual(framePattern());
    expect(read.irregular).toBe(false);
  });

  it("calls a BLOCKED window, a day unlike the first week, or a window past midnight irregular", () => {
    const base = spreadWeek(framePattern(), emptyWeek(), TODAY);
    const blocked: CalendarWindow = { start: "2026-11-22T00:00:00Z", end: "2026-11-22T10:00:00Z", kind: "BLOCKED" };
    expect(readWeek([...base, blocked], TODAY).irregular).toBe(true);

    const missingOneMonday = base.filter((w) => w.start !== "2026-11-30T11:00:00Z");
    expect(missingOneMonday).toHaveLength(base.length - 1);
    expect(readWeek(missingOneMonday, TODAY).irregular).toBe(true);

    const overMidnight: CalendarWindow = { start: "2026-11-19T15:00:00Z", end: "2026-11-19T18:00:00Z", kind: "AVAILABLE" };
    expect(readWeek([overMidnight], TODAY).irregular).toBe(true);
  });

  it("reads an empty calendar as an empty, regular week", () => {
    expect(readWeek([], TODAY)).toEqual({ available: emptyWeek(), preferred: emptyWeek(), irregular: false });
  });
});

describe("slotsPerBlock (as confirmNeed, #16 Q5)", () => {
  const morning = new Date("2026-11-18T00:00:00Z"); // 07:00 in Vietnam

  it("counts the 90-minute sessions that fit in each 7-day block", () => {
    // 4 slots of 3 hours a week: two 90-minute sessions each.
    const windows = spreadWeek(framePattern(), emptyWeek(), TODAY);
    expect(slotsPerBlock(windows, TODAY, 90, morning)).toEqual([8, 8, 8, 8]);
    expect(slotsPerBlock(windows, TODAY, 120, morning)).toEqual([4, 4, 4, 4]);
  });

  it("PH04 'Giờ chưa đủ': one 18:00–19:00 slot a week holds no 90-minute session", () => {
    const week = addSlot(emptyWeek(), 0, { start: "18:00", end: "19:00" });
    expect(slotsPerBlock(spreadWeek(week, emptyWeek(), TODAY), TODAY, 90, morning)).toEqual([0, 0, 0, 0]);
  });

  it("joins touching windows, takes BLOCKED time out and skips time already past", () => {
    let week = addSlot(emptyWeek(), 2, { start: "18:00", end: "19:00" });
    week = addSlot(week, 2, { start: "19:00", end: "20:00" });
    const windows = spreadWeek(week, emptyWeek(), TODAY);
    // 18:00–20:00 as one interval: one 90-minute session a week.
    expect(slotsPerBlock(windows, TODAY, 90, morning)).toEqual([1, 1, 1, 1]);

    // Blocked on the second Thứ 4 (25/11) 18:30–19:00 Vietnam.
    const blocked: CalendarWindow = { start: "2026-11-25T11:30:00Z", end: "2026-11-25T12:00:00Z", kind: "BLOCKED" };
    expect(slotsPerBlock([...windows, blocked], TODAY, 90, morning)).toEqual([1, 0, 1, 1]);

    // At 18:31 on 18/11 the first Thứ 4 has only 89 minutes left.
    expect(slotsPerBlock(windows, TODAY, 90, new Date("2026-11-18T11:31:00Z"))).toEqual([0, 1, 1, 1]);
  });
});
