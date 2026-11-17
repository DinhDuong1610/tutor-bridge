// AUTH04 Yêu cầu khôi phục mật khẩu (UC01, FR-39)
import { ScrollView, StyleSheet, Text } from "react-native";
import { Button } from "@/shared/ui/Button";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useRequestRecovery } from "../api";
import { EmailRequestForm } from "../components/EmailRequestForm";

type Props = {
  onGoToSignIn: () => void;
};

// AUTH04 "Đã gửi": conditional, so it confirms no account either.
const SENT =
  "Kiểm tra email của bạn. Nếu email này có tài khoản, chúng tôi đã gửi liên kết đặt lại mật khẩu. Liên kết có hạn 15 phút. Không thấy email? Xem thư mục Spam, hoặc gửi lại sau ít phút.";

/** The answer is the same whether or not the email has an account (UC01), so it never confirms one. */
export function RecoveryScreen({ onGoToSignIn }: Props) {
  const recovery = useRequestRecovery();

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <Text accessibilityRole="header" style={styles.title}>
        Quên mật khẩu
      </Text>
      <Text style={styles.body}>Nhập email của tài khoản. Chúng tôi sẽ gửi liên kết đặt lại mật khẩu, có hạn 15 phút.</Text>
      <EmailRequestForm command={recovery} submitTitle="Gửi liên kết" confirmedMessage={SENT} />
      <Button variant="secondary" title="Quay lại đăng nhập" onPress={onGoToSignIn} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, gap: spacing.md, padding: spacing.md, backgroundColor: colors.background },
  title: { ...typography.title, color: colors.text },
  body: { ...typography.body, color: colors.text },
});
