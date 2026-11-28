import type { components } from "@tutorbridge/api-client";
import { z } from "zod";
import { DAY_NAMES, addDays, weekdayOf } from "@/features/calendar/weekly";
import { formatThousands, priceNumber } from "@/features/tutor-profile/schemas";
import { lengthOf } from "@/shared/form/length";

export type NeedDraftInput = components["schemas"]["NeedDraftInput"];

// Limits of NeedDraftInput and confirmNeed (goal 20–2000, start in today..today+28). The server checks them again.
export const GOAL_MIN = 20;
export const GOAL_MAX = 2000;
const START_WINDOW_DAYS = 28;
export const DURATIONS = ["60", "90", "120"] as const;
export const FREQUENCIES = ["1", "2", "3"] as const;

/** What the form holds: text, as typed, so a half-typed value stays as typed. Empty is a valid draft. */
export type DraftFormValues = {
  goal: string;
  durationMinutes: string;
  frequencyPer7Days: string;
  budgetVndSession: string;
  /** dd/mm/yyyy (TBD #70 Q5: no approved date picker frame). */
  cycleStartDate: string;
};

export function emptyForm(): DraftFormValues {
  return { goal: "", durationMinutes: "", frequencyPer7Days: "", budgetVndSession: "", cycleStartDate: "" };
}

/** "18/11/2026" from "2026-11-18". */
function dateLabel(date: string): string {
  const [year, month, day] = date.split("-");
  return `${day}/${month}/${year}`;
}

/** "Thứ 4, 18/11/2026", as the approved frames show the start date. */
export function weekdayLabel(date: string): string {
  return `${DAY_NAMES[weekdayOf(date)]}, ${dateLabel(date)}`;
}

/** "Ngân sách tối đa cho một buổi 90 phút (VND)": the label follows the chosen duration (PH04 "Hành vi"). */
export function budgetLabel(duration: string): string {
  return duration ? `Ngân sách tối đa cho một buổi ${duration} phút (VND)` : "Ngân sách tối đa cho một buổi (VND)";
}

export function startDateHint(today: string): string {
  return `Từ hôm nay đến ${dateLabel(addDays(today, START_WINDOW_DAYS))}. Chu kỳ gồm 4 tuần liên tiếp kể từ ngày này.`;
}

/** "18/11/2026" to "2026-11-18", or null when it is not a real date in that form. */
function typedDate(value: string): string | null {
  const match = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(value.trim());
  if (!match) return null;
  const [, d, m, y] = match;
  const iso = `${y}-${m.padStart(2, "0")}-${d.padStart(2, "0")}`;
  const parsed = new Date(`${iso}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().startsWith(iso) ? iso : null;
}

const optional = <T>(value: T | null): T | undefined => (value === null ? undefined : value);

/**
 * PH04's fields for {@code today} (Vietnam date). An empty field is no value, so an incomplete draft still
 * saves (PH04 "Hành vi"). The copy for a short goal and a bad budget is PH04.md verbatim; the long goal and the
 * date have no approved copy (TBD #70 Q5).
 */
export function draftFormSchema(today: string) {
  const last = addDays(today, START_WINDOW_DAYS);
  return z.object({
    goal: z
      .string()
      .superRefine((value, ctx) => {
        const length = lengthOf(value.trim());
        if (length > 0 && length < GOAL_MIN) {
          ctx.addIssue({ code: "custom", message: `Mục tiêu cần ít nhất 20 ký tự (hiện ${length}).` });
        } else if (length > GOAL_MAX) {
          ctx.addIssue({ code: "custom", message: "Mục tiêu tối đa 2.000 ký tự." });
        }
      })
      .transform((value) => optional(value.trim() || null)),
    durationMinutes: z.string().transform((value) => optional(value ? Number(value) : null)),
    frequencyPer7Days: z.string().transform((value) => optional(value ? Number(value) : null)),
    budgetVndSession: z.string().transform((value, ctx) => {
      if (!value.trim()) return undefined;
      const budget = priceNumber(value);
      if (budget === null || budget < 1 || !Number.isSafeInteger(budget)) {
        ctx.addIssue({ code: "custom", message: "Ngân sách phải là số nguyên lớn hơn 0." });
        return z.NEVER;
      }
      return budget;
    }),
    cycleStartDate: z.string().transform((value, ctx) => {
      if (!value.trim()) return undefined;
      const date = typedDate(value);
      if (date === null) {
        ctx.addIssue({ code: "custom", message: "Nhập ngày theo dạng ngày/tháng/năm, ví dụ 18/11/2026." });
        return z.NEVER;
      }
      if (date < today || date > last) {
        ctx.addIssue({ code: "custom", message: `Chọn ngày từ ${dateLabel(today)} đến ${dateLabel(last)}.` });
        return z.NEVER;
      }
      return date;
    }),
  });
}

export type DraftForm = z.output<ReturnType<typeof draftFormSchema>>;

/** The body of createNeedDraft or updateNeedDraft: the whole draft, without the fields left empty (#16 Q3). */
export function toDraftInput(form: DraftForm): NeedDraftInput {
  const input: NeedDraftInput = {};
  if (form.goal !== undefined) input.goal = form.goal;
  if (form.durationMinutes !== undefined) input.durationMinutes = form.durationMinutes as 60 | 90 | 120;
  if (form.frequencyPer7Days !== undefined) input.frequencyPer7Days = form.frequencyPer7Days as 1 | 2 | 3;
  if (form.budgetVndSession !== undefined) input.budgetVndSession = form.budgetVndSession;
  if (form.cycleStartDate !== undefined) input.cycleStartDate = form.cycleStartDate;
  return input;
}

/** A stored draft as the form shows it. */
export function toFormValues(draft: NeedDraftInput): DraftFormValues {
  return {
    goal: draft.goal ?? "",
    durationMinutes: draft.durationMinutes ? String(draft.durationMinutes) : "",
    frequencyPer7Days: draft.frequencyPer7Days ? String(draft.frequencyPer7Days) : "",
    budgetVndSession: draft.budgetVndSession ? formatThousands(draft.budgetVndSession) : "",
    cycleStartDate: draft.cycleStartDate ? dateLabel(draft.cycleStartDate) : "",
  };
}
