import { emptyWeek, type WeekPattern } from "@/features/calendar/weekly";
import { budgetText, cycleText, sessionText, weekText } from "./review";

function week(entries: Record<number, [string, string][]>): WeekPattern {
  const result = emptyWeek();
  for (const [day, slots] of Object.entries(entries)) {
    result[Number(day)] = slots.map(([start, end]) => ({ start, end }));
  }
  return result;
}

describe("PH05 review text", () => {
  it("writes the session and the budget as the approved frame does", () => {
    expect(sessionText({ durationMinutes: 90, frequencyPer7Days: 2 })).toBe("90 phút, 2 buổi mỗi tuần");
    expect(budgetText({ budgetVndSession: 400000, durationMinutes: 90 })).toBe("400.000 ₫ / buổi 90 phút");
  });

  it("groups the weekdays that share the same slots, as 'Thứ 2, 4, 6: 18:00–21:00; Thứ 7: 08:00–11:00'", () => {
    const evenings: [string, string][] = [["18:00", "21:00"]];
    expect(weekText(week({ 0: evenings, 2: evenings, 4: evenings, 5: [["08:00", "11:00"]] }))).toBe(
      "Thứ 2, 4, 6: 18:00–21:00; Thứ 7: 08:00–11:00",
    );
    expect(weekText(week({ 5: [["08:00", "10:00"]], 6: [["08:00", "10:00"]] }))).toBe("Thứ 7, Chủ nhật: 08:00–10:00");
    expect(weekText(week({ 6: [["08:00", "10:00"]] }))).toBe("Chủ nhật: 08:00–10:00");
    expect(weekText(week({ 1: [["08:00", "10:00"], ["18:00", "20:00"]] }))).toBe("Thứ 3: 08:00–10:00 và 18:00–20:00");
    // "Giờ ưu tiên" left empty is "Không chọn" in the frame.
    expect(weekText(emptyWeek())).toBe("Không chọn");
  });

  it("names the 28 days of the cycle and its sessions", () => {
    expect(cycleText({ frequencyPer7Days: 2, cycleStartDate: "2026-11-18" })).toBe(
      "8 buổi trong 28 ngày, 2 buổi mỗi tuần, từ 18/11 đến 15/12/2026",
    );
    // Across a new year the first date keeps its year.
    expect(cycleText({ frequencyPer7Days: 1, cycleStartDate: "2026-12-20" })).toBe(
      "4 buổi trong 28 ngày, 1 buổi mỗi tuần, từ 20/12/2026 đến 16/01/2027",
    );
  });
});
