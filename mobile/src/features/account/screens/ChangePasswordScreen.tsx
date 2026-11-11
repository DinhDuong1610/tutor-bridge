// AUTH06 Đổi mật khẩu (UC01, FR-39). Mật khẩu hiện tại là bước tái xác thực (handoff TB-10, Q10).
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useServerErrors } from "@/shared/form/serverErrors";
import { useSession } from "@/shared/session/SessionProvider";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useChangePassword } from "../api";
import { AccountTextField } from "../components/fields";
import { changePasswordSchema, PASSWORD_MAX, PASSWORD_MIN, type ChangePasswordForm } from "../schemas";

type Props = {
  onCancel: () => void;
};

/**
 * A wrong current password must come back as a field error (422), not 401: a 401 ends the session
 * for the whole app (handoff TB-10, Q10, requirement for TB-09).
 */
export function ChangePasswordScreen({ onCancel }: Props) {
  const { signOut } = useSession();
  const form = useForm<ChangePasswordForm>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: "", newPassword: "" },
  });
  const change = useChangePassword();
  const otherErrors = useServerErrors(change.error, form.setError, ["currentPassword", "newPassword"]);

  if (change.state === "confirmed") {
    return <PasswordChanged onSignIn={() => void signOut()} />;
  }

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <Text accessibilityRole="header" style={styles.title}>
        Đổi mật khẩu
      </Text>
      <Text style={styles.body}>Sau khi đổi, mọi phiên đăng nhập, kể cả trên máy này, sẽ bị đăng xuất.</Text>
      <AccountTextField
        control={form.control}
        name="currentPassword"
        label="Mật khẩu hiện tại"
        secureTextEntry
        autoCapitalize="none"
        autoComplete="current-password"
      />
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
      <CommandStatus command={change} confirmedMessage="" />
      <Button title="Đổi mật khẩu" busy={change.state === "sending"} onPress={form.handleSubmit((values) => change.submit(values))} />
      <Button variant="secondary" title="Hủy" onPress={onCancel} />
    </ScrollView>
  );
}

/**
 * The server has revoked every session. The token here is forgotten when the user presses the
 * button, or by the foundation on the next request, which answers 401.
 */
function PasswordChanged({ onSignIn }: { onSignIn: () => void }) {
  return (
    <View style={styles.container}>
      <Text accessibilityRole="header" style={styles.title}>
        Đã đổi mật khẩu
      </Text>
      <Notice tone="success" message="Mọi phiên đăng nhập đã bị đăng xuất. Hãy đăng nhập lại bằng mật khẩu mới." />
      <Button title="Đăng nhập lại" onPress={onSignIn} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, gap: spacing.md, padding: spacing.md, backgroundColor: colors.background },
  title: { ...typography.title, color: colors.text },
  body: { ...typography.body, color: colors.text },
});
