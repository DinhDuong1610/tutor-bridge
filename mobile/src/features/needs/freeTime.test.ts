import { emptyWeek, spreadWeek, type WeekPattern } from "@/features/calendar/weekly";
import { FREE_TIME, hasFreeTime, sameWeek, shortfall } from "./freeTime";

// 10:00 on Thứ 4 18/11/2026 in Vietnam.
const NOW = new Date("2026-11-18T03:00:00Z");
const TODAY = "2026-11-18";

function week(entries: Record<number, [string, string][]>): WeekPattern {
  const result = emptyWeek();
  for (const [day, slots] of Object.entries(entries)) {
    result[Number(day)] = slots.map(([start, end]) => ({ start, end }));
  }
  return result;
}

describe("PH04 free time", () => {
  it("names the child's free time as PH04's 'Cần thêm' does", () => {
    expect(FREE_TIME).toBe("giờ rảnh của con");
  });

  it("tells a changed pattern from the saved one, in any order", () => {
    const a = week({ 0: [["18:00", "21:00"], ["08:00", "10:00"]] });
    expect(sameWeek(a, week({ 0: [["08:00", "10:00"], ["18:00", "21:00"]] }))).toBe(true);
    expect(sameWeek(a, week({ 0: [["18:00", "21:00"]] }))).toBe(false);
    expect(sameWeek(emptyWeek(), emptyWeek())).toBe(true);
  });

  it("has free time when any AVAILABLE or PREFERRED window is spread", () => {
    expect(hasFreeTime(spreadWeek(emptyWeek(), emptyWeek(), TODAY))).toBe(false);
    expect(hasFreeTime(spreadWeek(emptyWeek(), week({ 2: [["19:00", "20:00"]] }), TODAY))).toBe(true);
  });

  it("AC-UC04-02 warns when a block of 7 days has fewer slots than the sessions a week, with PH04's copy", () => {
    // One 3-hour evening a week fits two 90-minute sessions; three need more.
    const windows = spreadWeek(week({ 0: [["18:00", "21:00"]] }), emptyWeek(), TODAY);
    expect(shortfall(windows, { durationMinutes: 90, frequencyPer7Days: 2, cycleStartDate: "2026-11-23" }, NOW)).toBeNull();
    expect(shortfall(windows, { durationMinutes: 90, frequencyPer7Days: 3, cycleStartDate: "2026-11-23" }, NOW)).toBe(
      "Giờ rảnh chưa đủ cho 3 buổi 90 phút mỗi tuần. Thêm giờ rảnh hoặc giảm số buổi.",
    );
    // A missing term or no free time at all is "Cần thêm", not this warning.
    expect(shortfall(windows, { durationMinutes: 90, cycleStartDate: "2026-11-23" }, NOW)).toBeNull();
    expect(shortfall([], { durationMinutes: 90, frequencyPer7Days: 2, cycleStartDate: "2026-11-23" }, NOW)).toBeNull();
  });
});
