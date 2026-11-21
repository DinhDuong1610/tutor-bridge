import { z } from "zod";
import { lengthOf } from "@/shared/form/length";

export { lengthOf };

// Limits from the contract (RegistrationInput, LoginInput). The server checks them again.
export const PASSWORD_MIN = 15;
export const PASSWORD_MAX = 128;
const EMAIL_MAX = 254;
const NAME_MAX = 100;

const email = z
  .string()
  .trim()
  .min(1, "Vui lòng nhập email.")
  .refine((value) => lengthOf(value) <= EMAIL_MAX && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value), "Email chưa đúng dạng, ví dụ an@example.com.");

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
  termsAccepted: z.literal(true, { error: "Bạn cần đồng ý điều khoản để tạo tài khoản." }),
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

// Limits from the contract (VerifyEmailInput, RecoveryInput, ResetInput).
const TOKEN_MIN = 20;
const TOKEN_MAX = 512;

/** Whether a link carried a token the server could accept. Anything else is not sent. */
export function isLinkToken(value: unknown): value is string {
  return typeof value === "string" && lengthOf(value) >= TOKEN_MIN && lengthOf(value) <= TOKEN_MAX;
}

// Resend verification and password recovery both take only the email (RecoveryInput).
export const emailOnlySchema = z.object({ email });
export type EmailOnlyForm = z.infer<typeof emailOnlySchema>;

export const resetPasswordSchema = z.object({
  // Not trimmed, like registration.
  newPassword: z
    .string()
    .refine(
      (value) => lengthOf(value) >= PASSWORD_MIN && lengthOf(value) <= PASSWORD_MAX,
      `Mật khẩu cần từ ${PASSWORD_MIN} đến ${PASSWORD_MAX} ký tự.`,
    ),
});
export type ResetPasswordForm = z.infer<typeof resetPasswordSchema>;

// ChangePasswordInput. The current password is the reauthentication step for this command
// (handoff TB-10, Q10, Huy 07/11/2026).
export const changePasswordSchema = z.object({
  currentPassword: z
    .string()
    .min(1, "Vui lòng nhập mật khẩu hiện tại.")
    .refine((value) => lengthOf(value) <= PASSWORD_MAX, `Mật khẩu tối đa ${PASSWORD_MAX} ký tự.`),
  newPassword: resetPasswordSchema.shape.newPassword,
});
export type ChangePasswordForm = z.infer<typeof changePasswordSchema>;
