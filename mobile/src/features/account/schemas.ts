import { z } from "zod";

// Limits from the contract (RegistrationInput, LoginInput). The server checks them again.
export const PASSWORD_MIN = 15;
export const PASSWORD_MAX = 128;
const EMAIL_MAX = 254;
const NAME_MAX = 100;

/** JSON Schema counts code points, not UTF-16 units, so an emoji counts as one character. */
export function lengthOf(value: string): number {
  return [...value].length;
}

const email = z
  .string()
  .trim()
  .min(1, "Vui lòng nhập email.")
  .refine((value) => lengthOf(value) <= EMAIL_MAX && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value), "Email không hợp lệ.");

export const registrationSchema = z.object({
  email,
  displayName: z
    .string()
    .trim()
    .min(1, "Vui lòng nhập tên hiển thị.")
    .refine((value) => lengthOf(value) <= NAME_MAX, `Tên hiển thị tối đa ${NAME_MAX} ký tự.`),
  // Not trimmed: spaces can be part of a passphrase.
  password: z
    .string()
    .refine(
      (value) => lengthOf(value) >= PASSWORD_MIN && lengthOf(value) <= PASSWORD_MAX,
      `Mật khẩu cần từ ${PASSWORD_MIN} đến ${PASSWORD_MAX} ký tự.`,
    ),
  // Self registration offers only these two roles (AC-UC01-01); the server enforces it too.
  role: z.enum(["PARENT", "TUTOR"], { error: "Vui lòng chọn bạn là phụ huynh hay gia sư." }),
  termsAccepted: z.literal(true, { error: "Bạn cần đồng ý với điều khoản để đăng ký." }),
});
export type RegistrationFormInput = z.input<typeof registrationSchema>;
export type RegistrationForm = z.output<typeof registrationSchema>;

export const signInSchema = z.object({
  email,
  password: z
    .string()
    .min(1, "Vui lòng nhập mật khẩu.")
    .refine((value) => lengthOf(value) <= PASSWORD_MAX, `Mật khẩu tối đa ${PASSWORD_MAX} ký tự.`),
});
export type SignInForm = z.infer<typeof signInSchema>;
