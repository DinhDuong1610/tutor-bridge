// PH02 Hồ sơ các con: thêm hồ sơ con (UC02, FR-03). Thiết kế: docs/design/ui/screens/PH02.md
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect } from "react";
import { useForm, useWatch } from "react-hook-form";
import { KeyboardAvoidingView, ScrollView, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { FormTextField } from "@/shared/form/FormTextField";
import { lengthOf } from "@/shared/form/length";
import { useServerErrors } from "@/shared/form/serverErrors";
import { Button } from "@/shared/ui/Button";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { TopAppBar } from "@/shared/ui/TopAppBar";
import { useCreateStudent } from "../api";
import { GRADE, studentSchema, type StudentForm, type StudentFormInput } from "../schemas";

type Props = {
  onBack: () => void;
  /** Called once the server has confirmed the new profile (INV-72). */
  onSaved: (studentId: string) => void;
};

export function AddStudentScreen({ onBack, onSaved }: Props) {
  const form = useForm<StudentFormInput, unknown, StudentForm>({
    resolver: zodResolver(studentSchema),
    defaultValues: { displayName: "", shortGoal: "" },
  });
  const create = useCreateStudent();
  const otherErrors = useServerErrors(create.error, form.setError, ["displayName", "shortGoal"]);
  const goal = useWatch({ control: form.control, name: "shortGoal" }) ?? "";
  const hasFieldErrors = Object.keys(form.formState.errors).length > 0;
  const savedId = create.state === "confirmed" ? create.result?.id : undefined;

  useEffect(() => {
    if (savedId) {
      onSaved(savedId);
    }
  }, [savedId, onSaved]);

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.top}>
        <TopAppBar title="Thêm hồ sơ con" onBack={onBack} />
      </View>
      <KeyboardAvoidingView style={styles.fill} behavior="padding">
        <ScrollView contentContainerStyle={styles.form} keyboardShouldPersistTaps="handled">
          <FormTextField
            control={form.control}
            name="displayName"
            label="Tên hiển thị của con"
            hint="Tên gọi để bạn và gia sư nhận ra, không cần họ tên đầy đủ."
          />
          <View style={styles.grade}>
            <Text style={styles.label}>Lớp</Text>
            <Text style={styles.body}>Lớp {GRADE}</Text>
            <Text style={styles.caption}>Thí điểm hiện chỉ dành cho học sinh lớp 8, môn Toán đại số.</Text>
          </View>
          <FormTextField
            control={form.control}
            name="shortGoal"
            label="Mục tiêu ngắn"
            hint={`${formatCount(lengthOf(goal))}/2.000 ký tự. Có thể để trống và bổ sung sau.`}
            multiline
          />
          <Text style={styles.caption}>Không cần ảnh, ngày sinh, tên trường hay địa chỉ.</Text>
        </ScrollView>
        <StickyActionBar>
          {otherErrors.map((message) => (
            <Notice key={message} tone="danger" message={message} />
          ))}
          {create.state === "unknown" ? (
            // Always offer the safe retry here, even when a field shows an error.
            <CommandStatus command={create} confirmedMessage="" />
          ) : hasFieldErrors ? (
            <Notice tone="danger" message="Vui lòng sửa các ô được đánh dấu rồi gửi lại." />
          ) : create.state === "confirmed" ? null : (
            <CommandStatus command={create} confirmedMessage="" />
          )}
          {/*
            While the outcome is unknown the first profile may already exist. A new submit would reuse
            the kept key with other input (409), then the next save would get a new key and create a
            second profile. Only "Thử lại", same key and same input, is allowed until the answer is known.
          */}
          <Button
            title="Lưu hồ sơ"
            busy={create.state === "sending" || savedId !== undefined}
            disabled={create.state === "unknown"}
            onPress={form.handleSubmit((values) => create.submit(values))}
          />
        </StickyActionBar>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

/** 2000 → "2.000", as in the approved copy. */
function formatCount(value: number) {
  return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  top: { paddingHorizontal: spacing.md, paddingTop: spacing.md },
  fill: { flex: 1 },
  form: { gap: spacing.md, padding: spacing.md },
  grade: { gap: spacing.xs },
  label: { ...typography.label, color: colors.text },
  body: { ...typography.body, color: colors.text },
  caption: { ...typography.caption, color: colors.textMuted },
});
