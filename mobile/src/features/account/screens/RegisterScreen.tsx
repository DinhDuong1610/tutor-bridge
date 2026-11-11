// AUTH01 Đăng ký và chọn vai trò (UC01, FR-01)
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { useServerErrors } from "@/shared/form/serverErrors";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { useRegister } from "../api";
import { AccountTextField, CheckboxField, RadioField } from "../components/fields";
import {
  PASSWORD_MAX,
  PASSWORD_MIN,
  registrationSchema,
  type RegistrationForm,
  type RegistrationFormInput,
} from "../schemas";

type Props = {
  /** The terms version the user agrees to. Source still TBD (handoff TB-10, Q2); without it nothing is sent. */
  termsVersion: string | undefined;
  onGoToSignIn: () => void;
  /** AUTH02 without a token: ask for a new verification link. */
  onResendVerification: () => void;
};

const ROLES = [
  { value: "PARENT", label: "Tôi là phụ huynh" },
  { value: "TUTOR", label: "Tôi là gia sư" },
] as const;

export function RegisterScreen({ termsVersion, onGoToSignIn, onResendVerification }: Props) {
  const form = useForm<RegistrationFormInput, unknown, RegistrationForm>({
    resolver: zodResolver(registrationSchema),
    defaultValues: { email: "", displayName: "", password: "", role: undefined, termsAccepted: false as true },
  });
  const register = useRegister(termsVersion ?? "");
  const otherErrors = useServerErrors(register.error, form.setError, ["email", "displayName", "password", "role"]);

  if (register.state === "confirmed" && register.result) {
    // The same answer whether or not the email already had an account (AC-UC01-01), so it confirms neither.
    return (
      <View style={styles.container}>
        <Text accessibilityRole="header" style={styles.title}>
          Kiểm tra email của bạn
        </Text>
        <Notice tone="success" message={register.result.message} />
        <Text style={styles.body}>Mở liên kết trong email trong vòng 30 phút để xác minh tài khoản.</Text>
        <Button variant="secondary" title="Chưa nhận được email? Gửi lại liên kết" onPress={onResendVerification} />
        <Button variant="secondary" title="Đến trang đăng nhập" onPress={onGoToSignIn} />
      </View>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <Text accessibilityRole="header" style={styles.title}>
        Đăng ký tài khoản
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
      <AccountTextField control={form.control} name="displayName" label="Tên hiển thị" autoComplete="name" />
      <AccountTextField
        control={form.control}
        name="password"
        label="Mật khẩu"
        hint={`Từ ${PASSWORD_MIN} đến ${PASSWORD_MAX} ký tự. Có thể dùng một câu dễ nhớ.`}
        secureTextEntry
        autoCapitalize="none"
        autoComplete="new-password"
      />
      <RadioField control={form.control} name="role" label="Bạn là" options={ROLES} />
      <CheckboxField control={form.control} name="termsAccepted" label="Tôi đồng ý với điều khoản sử dụng" />
      {termsVersion ? null : <Notice tone="danger" message="Ứng dụng chưa cấu hình phiên bản điều khoản." />}
      {otherErrors.map((message) => (
        <Notice key={message} tone="danger" message={message} />
      ))}
      <CommandStatus command={register} confirmedMessage="Đã gửi đăng ký." />
      <Button
        title="Đăng ký"
        busy={register.state === "sending"}
        disabled={!termsVersion}
        onPress={form.handleSubmit((values) => register.submit(values))}
      />
      <Button variant="secondary" title="Đã có tài khoản? Đăng nhập" onPress={onGoToSignIn} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, gap: spacing.md, padding: spacing.md, backgroundColor: colors.background },
  title: { ...typography.title, color: colors.text },
  body: { ...typography.body, color: colors.text },
});
