// AUTH02 Xác minh email (UC01, FR-01, FR-39)
import { useState } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useResendVerification, useVerifyEmail } from "../api";
import { EmailRequestForm } from "../components/EmailRequestForm";
import { isLinkToken } from "../schemas";

type Props = {
  /** The token from the email link (`/verify-email?token=...`). Missing when the app was opened directly. */
  token: unknown;
  onGoToSignIn: () => void;
};

/**
 * Verifies the email only when the user presses the button, so opening or re-rendering the link
 * never spends the single-use token by itself. Expired or used links show the server's message
 * and offer a new link.
 */
export function VerifyEmailScreen({ token, onGoToSignIn }: Props) {
  const validToken = isLinkToken(token) ? token : null;
  const verify = useVerifyEmail(validToken ?? "");
  const resend = useResendVerification();
  const [retriedAfterUnknown, setRetriedAfterUnknown] = useState(false);

  function retryVerify() {
    setRetriedAfterUnknown(true);
    return verify.retry();
  }

  if (verify.state === "confirmed") {
    return (
      <View style={styles.container}>
        <Text accessibilityRole="header" style={styles.title}>
          Email đã được xác minh
        </Text>
        <Notice tone="success" message="Tài khoản của bạn đã được kích hoạt. Bạn có thể đăng nhập." />
        <Button title="Đăng nhập" onPress={onGoToSignIn} />
      </View>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <Text accessibilityRole="header" style={styles.title}>
        Xác minh email
      </Text>
      {validToken ? (
        <>
          <Text style={styles.body}>Nhấn nút bên dưới để xác minh email của bạn.</Text>
          <CommandStatus command={{ ...verify, retry: retryVerify }} confirmedMessage="" />
          {verify.state === "failed" && retriedAfterUnknown ? (
            // The first attempt may have worked before the connection dropped, then the retry finds the link used.
            <Text style={styles.body}>Lần gửi trước có thể đã xác minh email của bạn. Hãy thử đăng nhập trước.</Text>
          ) : verify.state === "failed" && verify.error?.code === "LINK_USED" ? (
            // LINK_USED and LINK_EXPIRED differ (TB-8 error codes): a used link usually means the email is verified.
            <Text style={styles.body}>Liên kết này đã được dùng. Nếu email của bạn đã được xác minh, hãy đăng nhập.</Text>
          ) : null}
          <Button title="Xác minh email" busy={verify.state === "sending"} onPress={() => void verify.submit(undefined)} />
        </>
      ) : (
        <Notice tone="danger" message="Liên kết xác minh không hợp lệ. Hãy mở đúng liên kết trong email, hoặc gửi lại liên kết mới." />
      )}
      {validToken === null || verify.state === "failed" ? (
        <View style={styles.section}>
          {/* Not a header role: renderScreen of the foundation expects one header per screen. */}
          <Text style={styles.subtitle}>Gửi lại liên kết xác minh</Text>
          <Text style={styles.body}>Liên kết mới có hạn 30 phút.</Text>
          <EmailRequestForm command={resend} submitTitle="Gửi lại liên kết" />
        </View>
      ) : null}
      <Button variant="secondary" title="Đến trang đăng nhập" onPress={onGoToSignIn} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, gap: spacing.md, padding: spacing.md, backgroundColor: colors.background },
  section: { gap: spacing.md },
  title: { ...typography.title, color: colors.text },
  subtitle: { ...typography.label, color: colors.text },
  body: { ...typography.body, color: colors.text },
});
