import { z } from "zod";
import { lengthOf } from "@/shared/form/length";

// Limits from the contract (StudentProfileInput, StudentPatchInput). The server checks them again.
export const NAME_MAX = 100;
export const GOAL_MAX = 2000;
/** Fixed by the contract (`grade: const 8`): shown as text, never an input (PH02). */
export const GRADE = 8;

export const studentSchema = z.object({
  displayName: z
    .string()
    .trim()
    .refine((value) => lengthOf(value) >= 1 && lengthOf(value) <= NAME_MAX, "Nhập tên hiển thị của con (1–100 ký tự)."),
  // Trimmed so a goal of only spaces is saved as "no goal" instead of a blank line.
  // Copy confirmed by Đính on 12/11/2026 (review of #119); added to PH02.md in Đính's docs PR.
  shortGoal: z
    .string()
    .trim()
    .refine((value) => lengthOf(value) <= GOAL_MAX, "Mục tiêu ngắn tối đa 2.000 ký tự."),
});
export type StudentFormInput = z.input<typeof studentSchema>;
export type StudentForm = z.output<typeof studentSchema>;
