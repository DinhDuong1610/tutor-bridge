// PH02 Hồ sơ các con: thêm hồ sơ con (UC02, FR-03). Thiết kế: docs/design/ui/screens/PH02.md
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { KeyboardAvoidingView, ScrollView, StyleSheet, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useServerErrors } from "@/shared/form/serverErrors";
import { Button } from "@/shared/ui/Button";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { colors, spacing } from "@/shared/ui/theme";
import { TopAppBar } from "@/shared/ui/TopAppBar";
import { useCreateStudent } from "../api";
import { SaveStatus, StudentFields } from "../components/StudentForm";
import { studentSchema, type StudentForm, type StudentFormInput } from "../schemas";

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
          <StudentFields control={form.control} />
        </ScrollView>
        <StickyActionBar>
          <SaveStatus command={create} hasFieldErrors={hasFieldErrors} otherErrors={otherErrors} />
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

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  top: { paddingHorizontal: spacing.md, paddingTop: spacing.md },
  fill: { flex: 1 },
  form: { gap: spacing.md, padding: spacing.md },
});
