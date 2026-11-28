import { DAY_NAMES, addDays, type WeekPattern } from "@/features/calendar/weekly";
import { formatThousands } from "@/features/tutor-profile/schemas";
import type { NeedDraftInput } from "./draftForm";

/** "90 phút, 2 buổi mỗi tuần" (PH05 "Mỗi buổi"). */
export function sessionText(draft: Pick<NeedDraftInput, "durationMinutes" | "frequencyPer7Days">): string {
  return `${draft.durationMinutes} phút, ${draft.frequencyPer7Days} buổi mỗi tuần`;
}

/** "400.000 ₫ / buổi 90 phút" (PH05 "Ngân sách tối đa"). */
export function budgetText(draft: Pick<NeedDraftInput, "budgetVndSession" | "durationMinutes">): string {
  return `${formatThousands(draft.budgetVndSession ?? 0)} ₫ / buổi ${draft.durationMinutes} phút`;
}

const slotsKey = (slots: WeekPattern[number]) => slots.map((s) => `${s.start}–${s.end}`).join(" và ");

/** "Thứ 2, 4, 6" from Monday, Wednesday and Friday; Sunday keeps its name. */
function dayList(days: number[]): string {
  const weekdays = days.filter((day) => day < 6).map((day) => DAY_NAMES[day].replace("Thứ ", ""));
  const parts = weekdays.length > 0 ? [`Thứ ${weekdays.join(", ")}`] : [];
  if (days.includes(6)) parts.push(DAY_NAMES[6]);
  return parts.join(", ");
}

/**
 * PH05 "Giờ con rảnh" and "Giờ ưu tiên": the weekdays that share the same slots, in weekday order,
 * "Thứ 2, 4, 6: 18:00–21:00; Thứ 7: 08:00–11:00". An empty pattern is "Không chọn".
 */
export function weekText(week: WeekPattern): string {
  const groups = new Map<string, number[]>();
  week.forEach((slots, day) => {
    if (slots.length === 0) return;
    const key = slotsKey(slots);
    groups.set(key, [...(groups.get(key) ?? []), day]);
  });
  if (groups.size === 0) return "Không chọn";
  return [...groups.entries()].map(([slots, days]) => `${dayList(days)}: ${slots}`).join("; ");
}

const short = (date: string) => date.split("-").slice(1).reverse().join("/");
const full = (date: string) => date.split("-").reverse().join("/");

/** The CycleStrip heading: "8 buổi trong 28 ngày, 2 buổi mỗi tuần, từ 18/11 đến 15/12/2026" (BR04). */
export function cycleText(draft: { frequencyPer7Days: number; cycleStartDate: string }): string {
  const last = addDays(draft.cycleStartDate, 27);
  const from = draft.cycleStartDate.slice(0, 4) === last.slice(0, 4) ? short(draft.cycleStartDate) : full(draft.cycleStartDate);
  return `${draft.frequencyPer7Days * 4} buổi trong 28 ngày, ${draft.frequencyPer7Days} buổi mỗi tuần, từ ${from} đến ${full(last)}`;
}
