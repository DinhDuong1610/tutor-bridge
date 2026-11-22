import { z } from "zod";
import { lengthOf } from "@/shared/form/length";

// Limits from the contract (TutorProfileInput, AC-UC03-01). The server checks them again.
export const DESCRIPTION_MIN = 50;
export const DESCRIPTION_MAX = 3000;
export const EXPERIENCE_MAX = 600;
/** Fixed by the contract (`subject: const MATH8_ALGEBRA`): shown as text, never an input (GS02). */
export const SUBJECT = "MATH8_ALGEBRA";

/** "250.000" or "250 000" as typed or shown; only digits are kept. Anything else is not a whole number. */
function wholeNumber(value: string): number | null {
  const digits = value.trim().replace(/[.\s]/g, "");
  return /^\d+$/.test(digits) ? Number(digits) : null;
}

/** 250000 → "250.000", as in the approved copy. */
export function formatThousands(value: number): string {
  return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
}

/**
 * The three fields of GS02. Numbers are typed as text so a half-typed value stays as typed; they become
 * integers only when valid. The copy for a short description and a bad price is GS02.md verbatim; the
 * other two cases have no approved copy and use the server's own message (TutorProfiles.validate).
 */
export const tutorProfileSchema = z.object({
  description: z.string().superRefine((value, ctx) => {
    const length = lengthOf(value.trim());
    if (length < DESCRIPTION_MIN) {
      ctx.addIssue({ code: "custom", message: `Mô tả cần ít nhất 50 ký tự (hiện ${length}).` });
    } else if (length > DESCRIPTION_MAX) {
      ctx.addIssue({ code: "custom", message: "Mô tả cần từ 50 đến 3000 ký tự." });
    }
  }).transform((value) => value.trim()),
  experienceMonths: z.string().transform((value, ctx) => {
    const months = wholeNumber(value);
    if (months === null || months > EXPERIENCE_MAX) {
      ctx.addIssue({ code: "custom", message: "Kinh nghiệm cần từ 0 đến 600 tháng." });
      return z.NEVER;
    }
    return months;
  }),
  rateVnd60: z.string().transform((value, ctx) => {
    const rate = wholeNumber(value);
    if (rate === null || rate < 1 || !Number.isSafeInteger(rate)) {
      ctx.addIssue({ code: "custom", message: "Học phí phải là số nguyên lớn hơn 0." });
      return z.NEVER;
    }
    return rate;
  }),
});
export type TutorProfileFormInput = z.input<typeof tutorProfileSchema>;
export type TutorProfileForm = z.output<typeof tutorProfileSchema>;
