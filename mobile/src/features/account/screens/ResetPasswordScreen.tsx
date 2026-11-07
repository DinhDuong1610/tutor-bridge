// AUTH05 Đặt lại mật khẩu (UC01, FR-39)
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useServerErrors } from "@/shared/form/serverErrors";
import { useSession } from "@/shared/session/SessionProvider";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useResetPassword } from "../api";
import { AccountTextField } from "../components/fields";
import { isLinkToken, PASSWORD_MAX, PASSWORD_MIN, resetPasswordSchema, type ResetPasswordForm } from "../schemas";

type Props = {
  /** The token from the email link (`/reset-password?token=...`). */
  token: unknown;
  onGoToSignIn: () => void;
  onRequestNewLink: () => void;
};

export function ResetPasswordScreen({ token, onGoToSignIn, onRequestNewLink }: Props) {
  const validToken = isLinkToken(token) ? token : null;
  const form = useForm<ResetPasswordForm>({ resolver: zodResolver(resetPasswordSchema), defaultValues: { newPassword: "" } });
  const reset = useResetPassword(validToken ?? "");
  const otherErrors = useServerErrors(reset.error, form.setError, ["newPassword"]);
  const [retriedAfterUnknown, setRetriedAfterUnknown] = useState(false);
  const session = useSession();
  const signedIn = session.status === "signedIn";
  const { signOut } = session;

  // The reset revoked every session on the server. A link opened while signed in (Q8) must not
  // leave this device holding a dead token.
  useEffect(() => {
    if (reset.state === "confirmed" && signedIn) void signOut();
  }, [reset.state, signedIn, signOut]);

  function retryReset() {
    setRetriedAfterUnknown(true);
    return reset.retry();
  }

  if (reset.state === "confirmed") {
    return (
      <View style={styles.container}>
        <Text accessibilityRole="header" style={styles.title}>
          Đã đặt lại mật khẩu
        </Text>
        <Notice tone="success" message="Mọi phiên đăng nhập cũ đã bị đăng xuất. Hãy đăng nhập bằng mật khẩu mới." />
        <Button title="Đăng nhập" onPress={onGoToSignIn} />
      </View>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <Text accessibilityRole="header" style={styles.title}>
        Đặt lại mật khẩu
      </Text>
      {validToken ? (
        <>
          <AccountTextField
            control={form.control}
            name="newPassword"
            label="Mật khẩu mới"
            hint={`Từ ${PASSWORD_MIN} đến ${PASSWORD_MAX} ký tự. Có thể dùng một câu dễ nhớ.`}
            secureTextEntry
            autoCapitalize="none"
            autoComplete="new-password"
          />
          {otherErrors.map((message) => (
            <Notice key={message} tone="danger" message={message} />
          ))}
          <CommandStatus command={{ ...reset, retry: retryReset }} confirmedMessage="" />
          {reset.state === "failed" && retriedAfterUnknown ? (
            // The first attempt may have changed the password before the connection dropped.
            <Text style={styles.body}>Lần gửi trước có thể đã đổi mật khẩu. Hãy thử đăng nhập bằng mật khẩu mới trước.</Text>
          ) : null}
          <Button
            title="Đặt lại mật khẩu"
            busy={reset.state === "sending"}
            onPress={form.handleSubmit((values) => reset.submit(values))}
          />
        </>
      ) : (
        <Notice tone="danger" message="Liên kết đặt lại mật khẩu không hợp lệ. Hãy mở đúng liên kết trong email, hoặc yêu cầu liên kết mới." />
      )}
      {validToken === null || reset.state === "failed" ? (
        <Button variant="secondary" title="Yêu cầu liên kết mới" onPress={onRequestNewLink} />
      ) : null}
      <Button variant="secondary" title="Quay lại đăng nhập" onPress={onGoToSignIn} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, gap: spacing.md, padding: spacing.md, backgroundColor: colors.background },
  title: { ...typography.title, color: colors.text },
  body: { ...typography.body, color: colors.text },
});
