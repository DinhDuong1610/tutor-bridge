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
  { value: "PARENT", label: "Phụ huynh", description: "Tìm gia sư và theo dõi việc học của con." },
  { value: "TUTOR", label: "Gia sư", description: "Nhận lớp và chuẩn bị bài cho học sinh." },
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
    // The server's sentence stays instead of AUTH01's "Đã gửi email xác minh tới địa chỉ bạn vừa nhập.":
    // a taken email gets no email, so that sentence would be untrue there (#142, for Đính).
    return (
      <View style={styles.container}>
        <Text accessibilityRole="header" style={styles.title}>
          Kiểm tra email của bạn
        </Text>
        <Notice tone="success" message={register.result.message} />
        <Text style={styles.body}>Liên kết có hạn 30 phút.</Text>
        <Text style={styles.body}>
          Chưa xác minh email thì chưa đăng nhập được. Không thấy email? Xem thư mục Spam rồi gửi lại.
        </Text>
        <Button title="Gửi lại email xác minh" onPress={onResendVerification} />
        <Button variant="secondary" title="Đến trang đăng nhập" onPress={onGoToSignIn} />
      </View>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      <Text accessibilityRole="header" style={styles.title}>
        Tạo tài khoản
      </Text>
      <RadioField
        control={form.control}
        name="role"
        label="Chỉ phụ huynh và gia sư tự đăng ký. Bạn là:"
        options={ROLES}
      />
      <AccountTextField
        control={form.control}
        name="email"
        label="Email"
        autoCapitalize="none"
        autoComplete="email"
        keyboardType="email-address"
        inputMode="email"
        hint="Dùng để đăng nhập và nhận liên kết xác minh."
      />
      <AccountTextField
        control={form.control}
        name="displayName"
        label="Tên hiển thị"
        hint="Gia sư và phụ huynh khác thấy tên này."
        autoComplete="name"
      />
      <AccountTextField
        control={form.control}
        name="password"
        label="Mật khẩu"
        hint={`${PASSWORD_MIN}–${PASSWORD_MAX} ký tự.`}
        secureTextEntry
        autoCapitalize="none"
        autoComplete="new-password"
      />
      <CheckboxField
        control={form.control}
        name="termsAccepted"
        label={termsVersion ? `Tôi đồng ý với Điều khoản sử dụng (phiên bản ${termsVersion}).` : "Tôi đồng ý với Điều khoản sử dụng."}
      />
      {termsVersion ? null : <Notice tone="danger" message="Ứng dụng chưa cấu hình phiên bản điều khoản." />}
      {otherErrors.map((message) => (
        <Notice key={message} tone="danger" message={message} />
      ))}
      <CommandStatus command={register} confirmedMessage="Đã gửi đăng ký." />
      <Button
        title="Tạo tài khoản"
        busy={register.state === "sending"}
        disabled={!termsVersion}
        onPress={form.handleSubmit((values) => register.submit(values))}
      />
      <Button variant="secondary" title="Tôi đã có tài khoản" onPress={onGoToSignIn} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flexGrow: 1, gap: spacing.md, padding: spacing.md, backgroundColor: colors.background },
  title: { ...typography.title, color: colors.text },
  body: { ...typography.body, color: colors.text },
});
