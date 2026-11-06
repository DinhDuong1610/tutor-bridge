// AUTH03 Đăng nhập (UC01, FR-02)
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { ScrollView, StyleSheet, Text } from "react-native";
import { useServerErrors } from "@/shared/form/serverErrors";
import { useSession } from "@/shared/session/SessionProvider";
import type { Session } from "@/shared/session/storage";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useSignIn } from "../api";
import { AccountTextField } from "../components/fields";
import { signInSchema, type SignInForm } from "../schemas";

type Props = {
  onGoToRegister: () => void;
};

// Fixed text, so the screen never says whether the email has an account.
const WRONG_CREDENTIALS = "Email hoặc mật khẩu không đúng.";
const ADMIN_ON_WEB = "Tài khoản quản trị đăng nhập trên trang quản trị, không dùng ứng dụng.";
const NOT_SAVED = "Không lưu được phiên đăng nhập trên máy. Vui lòng thử lại.";

export function SignInScreen({ onGoToRegister }: Props) {
  const session = useSession();
  const form = useForm<SignInForm>({ resolver: zodResolver(signInSchema), defaultValues: { email: "", password: "" } });
  const signIn = useSignIn();
  const otherErrors = useServerErrors(signIn.error, form.setError, ["email", "password"]);
  const [problem, setProblem] = useState<string | null>(null);

  async function submit(values: SignInForm) {
    setProblem(null);
    const result = await signIn.submit(values);
    if (result === undefined) {
      return;
    }
    if ("mfaRequired" in result || !result.accessToken) {
      // Admins use the web console with MFA (TB-13); a credential without a token is not for mobile.
      setProblem(ADMIN_ON_WEB);
      return;
    }
    try {
      // The entry route sends the new session to its role's home.
      await session.signIn(result as Session);
    } catch {
      setProblem(NOT_SAVED);
    }
  }

  // A 401 at login is a wrong email or password, never a session ending; say it the same way for both.
  const wrongCredentials = signIn.state === "failed" && signIn.error?.kind === "session-expired";

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <Text accessibilityRole="header" style={styles.title}>
        Đăng nhập
      </Text>
      <AccountTextField
        control={form.control}
        name="email"
        label="Email"
        autoCapitalize="none"
        autoComplete="email"
        keyboardType="email-address"
        inputMode="email"
      />
      <AccountTextField
        control={form.control}
        name="password"
        label="Mật khẩu"
        secureTextEntry
        autoCapitalize="none"
        autoComplete="current-password"
      />
      {otherErrors.map((message) => (
        <Notice key={message} tone="danger" message={message} />
      ))}
      {problem ? (
        <Notice tone="danger" message={problem} />
      ) : wrongCredentials ? (
        <Notice tone="danger" message={WRONG_CREDENTIALS} />
      ) : signIn.state === "unknown" ? (
        // Not CommandStatus: its retry would skip keeping the session. Signing in again is safe;
        // at worst the server holds one unused session that expires.
        <Notice tone="warning" message="Chưa rõ kết quả. Vui lòng kiểm tra kết nối rồi đăng nhập lại." />
      ) : signIn.state === "confirmed" ? null : (
        <CommandStatus command={signIn} confirmedMessage="" />
      )}
      <Button title="Đăng nhập" busy={signIn.state === "sending"} onPress={form.handleSubmit(submit)} />
      <Button variant="secondary" title="Chưa có tài khoản? Đăng ký" onPress={onGoToRegister} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, gap: spacing.md, padding: spacing.md, backgroundColor: colors.background },
  title: { ...typography.title, color: colors.text },
});
