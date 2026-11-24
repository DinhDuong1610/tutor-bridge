import { z } from "zod";
import { lengthOf } from "@/shared/form/length";

// Limits from the contract (TutorProfileInput, AC-UC03-01). The server checks them again.
export const DESCRIPTION_MIN = 50;
export const DESCRIPTION_MAX = 3000;
export const EXPERIENCE_MAX = 600;
/** Fixed by the contract (`subject: const MATH8_ALGEBRA`): shown as text, never an input (GS02). */
export const SUBJECT = "MATH8_ALGEBRA";
/** The subject as GS02 and GS03 write it (approved frames). */
export const SUBJECT_LABEL = "Toán 8, đại số";

/**
 * The scope a tutor reads. A verification that is still waiting carries the subject code of its version
 * (`MATH8_ALGEBRA`), not text; only a verifier's decision writes words. Never show the code.
 */
export function scopeLabel(scope: string | null | undefined): string {
  return !scope || scope === SUBJECT ? SUBJECT_LABEL : scope;
}

/** Digits only, as months are typed ("24"). "0.5" is not a number of months: it is refused, never read as 5. */
function plainNumber(value: string): number | null {
  const typed = value.trim();
  return /^\d+$/.test(typed) ? Number(typed) : null;
}

/**
 * A price as typed or shown: digits only ("250000"), or thousands grouped by one "." or one space each three
 * digits ("250.000", "1 500 000"). Anything else, "1.5" or "25.00" included, is refused, never read as
 * another number (Codex review of #209).
 */
function priceNumber(value: string): number | null {
  const typed = value.trim();
  if (/^\d+$/.test(typed)) return Number(typed);
  return /^\d{1,3}(\.\d{3})+$/.test(typed) || /^\d{1,3}( \d{3})+$/.test(typed)
    ? Number(typed.replace(/[. ]/g, ""))
    : null;
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
    const months = plainNumber(value);
    if (months === null || months > EXPERIENCE_MAX) {
      ctx.addIssue({ code: "custom", message: "Kinh nghiệm cần từ 0 đến 600 tháng." });
      return z.NEVER;
    }
    return months;
  }),
  rateVnd60: z.string().transform((value, ctx) => {
    const rate = priceNumber(value);
    if (rate === null || rate < 1 || !Number.isSafeInteger(rate)) {
      ctx.addIssue({ code: "custom", message: "Học phí phải là số nguyên lớn hơn 0." });
      return z.NEVER;
    }
    return rate;
  }),
});
export type TutorProfileFormInput = z.input<typeof tutorProfileSchema>;
export type TutorProfileForm = z.output<typeof tutorProfileSchema>;
