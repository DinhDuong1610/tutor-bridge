import { z } from "zod";
import { COPY } from "./copy";

/** loginAdmin's LoginInput: email up to 254, password 1–128. */
export const signInForm = z.object({
  email: z.string().trim().min(1, COPY.emailRequired).max(254, COPY.emailInvalid).email(COPY.emailInvalid),
  password: z.string().min(1, COPY.passwordRequired).max(128, COPY.wrongCredentials),
});
export type SignInForm = z.infer<typeof signInForm>;

/** completeAdminMfa's code: 6 to 8 digits. */
export const mfaForm = z.object({
  code: z.string().trim().regex(/^[0-9]{6,8}$/, COPY.codeInvalid),
});
export type MfaForm = z.infer<typeof mfaForm>;
