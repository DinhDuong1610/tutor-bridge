import { emptyWeek, spreadWeek, type CalendarWindow, type WeekPattern } from "@/features/calendar/weekly";
import {
  NEAR_LIMIT,
  applyRange,
  draftCount,
  freeCount,
  limitNotice,
  sameWeek,
  weeklySummary,
  windowsToSave,
} from "./plan";

// Thứ 4 18/11/2026, as in the approved frames.
const TODAY = "2026-11-18";

function week(entries: Record<number, [string, string][]>): WeekPattern {
  const result = emptyWeek();
  for (const [day, slots] of Object.entries(entries)) {
    result[Number(day)] = slots.map(([start, end]) => ({ start, end }));
  }
  return result;
}

describe("GS04 plan", () => {
  it("OP-26 the range covers the 56 days from today, as dd/mm/yyyy", () => {
    expect(applyRange(TODAY)).toBe("Áp dụng từ 18/11/2026 đến 12/01/2027");
  });

  it("names each weekday's slots in order, joined as the review frame does", () => {
    const available = week({ 0: [["18:00", "21:00"]], 5: [["08:00", "11:00"], ["14:00", "17:00"]], 6: [["08:00", "10:00"]] });
    expect(weeklySummary(available)).toBe(
      "Mỗi tuần: Thứ 2 18:00–21:00; Thứ 7 08:00–11:00 và 14:00–17:00; Chủ nhật 08:00–10:00.",
    );
  });

  it("says a week with no slot has no free time (TBD #69, proposed wording)", () => {
    expect(weeklySummary(week({}))).toBe("Mỗi tuần: không có giờ rảnh.");
  });

  it("counts the slots not yet confirmed and tells a changed pattern from the saved one", () => {
    const saved = week({ 0: [["18:00", "21:00"]] });
    const edited = week({ 0: [["18:00", "21:00"]], 1: [["18:00", "20:00"]], 6: [["08:00", "10:00"]] });
    expect(draftCount(edited, saved)).toBe(2);
    expect(sameWeek(saved, week({ 0: [["18:00", "21:00"]] }))).toBe(true);
    expect(sameWeek(saved, edited)).toBe(false);
    // Removing a confirmed slot is a change too, with no draft chip to count.
    expect(sameWeek(saved, emptyWeek())).toBe(false);
    expect(draftCount(emptyWeek(), saved)).toBe(0);
  });

  it("AC-UC07-01 saves the pattern over 56 days, keeping PREFERRED and every BLOCKED window still ahead (#69 Q2, Đính 28/11/2026)", () => {
    const available = week({ 0: [["18:00", "21:00"]] });
    const preferred = week({ 2: [["19:00", "20:00"]] });
    const stored: CalendarWindow[] = [
      // Ended before today: history, not sent.
      { start: "2026-11-16T01:00:00Z", end: "2026-11-16T02:00:00Z", kind: "BLOCKED" },
      // Began yesterday, ends today: only the part from today on.
      { start: "2026-11-17T10:00:00Z", end: "2026-11-18T03:00:00Z", kind: "BLOCKED" },
      // Ahead: kept as it is.
      { start: "2026-11-22T00:00:00Z", end: "2026-11-22T12:00:00Z", kind: "BLOCKED" },
      // Runs past the horizon: cut at its end.
      { start: "2027-01-12T10:00:00Z", end: "2027-01-14T00:00:00Z", kind: "BLOCKED" },
      // Not BLOCKED: replaced by the pattern.
      { start: "2026-11-19T11:00:00Z", end: "2026-11-19T12:00:00Z", kind: "AVAILABLE" },
    ];

    const windows = windowsToSave(available, preferred, stored, TODAY);

    expect(windows.filter((w) => w.kind !== "BLOCKED")).toEqual(spreadWeek(available, preferred, TODAY));
    expect(windows.filter((w) => w.kind === "BLOCKED")).toEqual([
      { start: "2026-11-17T17:00:00Z", end: "2026-11-18T03:00:00Z", kind: "BLOCKED" },
      { start: "2026-11-22T00:00:00Z", end: "2026-11-22T12:00:00Z", kind: "BLOCKED" },
      { start: "2027-01-12T10:00:00Z", end: "2027-01-12T17:00:00Z", kind: "BLOCKED" },
    ]);
    // Every window lies in [start of today, start of day 57) Vietnam time, as the server checks.
    for (const w of windows) {
      expect(Date.parse(w.start)).toBeGreaterThanOrEqual(Date.parse("2026-11-17T17:00:00Z"));
      expect(Date.parse(w.end)).toBeLessThanOrEqual(Date.parse("2027-01-12T17:00:00Z"));
    }
    expect(freeCount(windows)).toBe(8 + 8);
  });

  it("warns near the 200 windows the server takes and refuses past them", () => {
    expect(limitNotice(NEAR_LIMIT - 1)).toBeNull();
    expect(limitNotice(196)).toEqual({
      blocks: false,
      message: "Lịch này tạo 196 khoảng rảnh trong 8 tuần, gần mức tối đa 200. Gộp các khung sát nhau để thêm được giờ.",
    });
    expect(limitNotice(200)?.blocks).toBe(false);
    expect(limitNotice(201)?.blocks).toBe(true);
  });
});
