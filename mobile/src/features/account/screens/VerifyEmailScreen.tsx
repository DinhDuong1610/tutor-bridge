// AUTH02 Xác minh email (UC01, FR-01, FR-39)
import { useState } from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
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
// AUTH02 wording. "Sai mục đích" is not used: the server answers LINK_INVALID for any bad link, and
// "không dùng để xác minh email" would be untrue for a broken one (#142).
const LINK_STATES = {
  expired: {
    heading: "Liên kết đã hết hạn",
    body: "Liên kết xác minh chỉ dùng được trong 30 phút. Gửi liên kết mới tới email của bạn.",
  },
  used: {
    heading: "Liên kết này đã được dùng",
    body: "Mỗi liên kết chỉ dùng một lần. Nếu email đã được xác minh, hãy đăng nhập.",
  },
  invalid: {
    heading: "Liên kết không hợp lệ",
    body: "Hãy mở đúng liên kết trong email, hoặc gửi lại liên kết mới.",
  },
} as const;

const LINK_CODES: Record<string, keyof typeof LINK_STATES> = {
  LINK_EXPIRED: "expired",
  LINK_USED: "used",
  LINK_INVALID: "invalid",
};

export function VerifyEmailScreen({ token, onGoToSignIn }: Props) {
  const validToken = isLinkToken(token) ? token : null;
  const verify = useVerifyEmail(validToken ?? "");
  const resend = useResendVerification();
  const [retriedAfterUnknown, setRetriedAfterUnknown] = useState(false);

  // After an unknown outcome the retry may find its own link used; that case has its own sentence below.
  const linkState =
    verify.state === "failed" && !retriedAfterUnknown ? LINK_CODES[verify.error?.code ?? ""] : undefined;

  function retryVerify() {
    setRetriedAfterUnknown(true);
    return verify.retry();
  }

  if (verify.state === "confirmed") {
    return (
      <View style={styles.container}>
        {/* Announced when it appears, as the Notice it replaced was (TalkBack). */}
        <View accessibilityLiveRegion="polite" style={styles.section}>
          <Text accessibilityRole="header" style={styles.title}>
            Đã xác minh email
          </Text>
          <Text style={styles.body}>Bạn có thể đăng nhập bằng email và mật khẩu đã tạo.</Text>
        </View>
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
          {linkState ? (
            // AUTH02 frames: a heading and one sentence per link state (TB-8 error codes).
            // A live region, so the result of pressing the button is read out like a Notice.
            <View accessibilityLiveRegion="polite" style={styles.section}>
              <Text accessibilityRole="header" style={styles.subtitle}>
                {LINK_STATES[linkState].heading}
              </Text>
              <Text style={styles.body}>{LINK_STATES[linkState].body}</Text>
            </View>
          ) : verify.state === "sending" ? (
            <View accessibilityLiveRegion="polite">
              <Text style={styles.body}>Đang xác minh email…</Text>
            </View>
          ) : (
            <>
              <Text style={styles.body}>Nhấn nút bên dưới để xác minh email của bạn.</Text>
              <CommandStatus command={{ ...verify, retry: retryVerify }} confirmedMessage="" />
            </>
          )}
          {verify.state === "failed" && retriedAfterUnknown ? (
            // The first attempt may have worked before the connection dropped, then the retry finds the link used.
            <Text style={styles.body}>Lần gửi trước có thể đã xác minh email của bạn. Hãy thử đăng nhập trước.</Text>
          ) : null}
          {linkState ? null : (
            <Button title="Xác minh email" busy={verify.state === "sending"} onPress={() => void verify.submit(undefined)} />
          )}
        </>
      ) : token === undefined ? (
        // Opened from sign-in or registration to ask for a new link, not from an email link.
        <Text style={styles.body}>Nhập email đã đăng ký để nhận liên kết xác minh mới.</Text>
      ) : (
        <View accessibilityLiveRegion="polite" style={styles.section}>
          <Text accessibilityRole="header" style={styles.subtitle}>
            {LINK_STATES.invalid.heading}
          </Text>
          <Text style={styles.body}>{LINK_STATES.invalid.body}</Text>
        </View>
      )}
      {validToken === null || verify.state === "failed" ? (
        <View style={styles.section}>
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
