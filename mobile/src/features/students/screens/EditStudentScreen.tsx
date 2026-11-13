// PH02 Hồ sơ các con: sửa hồ sơ (UC02, FR-03). Thiết kế: docs/design/ui/screens/PH02.md
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { KeyboardAvoidingView, ScrollView, StyleSheet } from "react-native";
import { useServerErrors } from "@/shared/form/serverErrors";
import { Button } from "@/shared/ui/Button";
import { Notice } from "@/shared/ui/Notice";
import { QueryScreen } from "@/shared/ui/QueryScreen";
import { StickyActionBar } from "@/shared/ui/StickyActionBar";
import { spacing } from "@/shared/ui/theme";
import { changedFields, useStudentProfile, useUpdateStudent, type Student } from "../api";
import { SaveStatus, StudentFields } from "../components/StudentForm";
import { studentSchema, type StudentForm, type StudentFormInput } from "../schemas";

type Props = {
  studentId: string;
  onBack: () => void;
  /** Called once the server has confirmed the change (INV-72). */
  onSaved: (studentId: string) => void;
};

/**
 * Loading, not found and lost access come from QueryScreen; they say nothing about the profile
 * (INV-06). A stale ETag (412) is never merged on the device (INV-23): the user reloads, keeps what
 * they typed, and saves again against the new revision.
 */
export function EditStudentScreen({ studentId, onBack, onSaved }: Props) {
  const profile = useStudentProfile(studentId);
  return (
    <QueryScreen title="Sửa hồ sơ" onBack={onBack} query={profile}>
      {(loaded) => <EditForm initial={loaded} onReload={() => profile.refetch()} onSaved={onSaved} />}
    </QueryScreen>
  );
}

type Revision = { data: Student; etag: string };

function EditForm({
  initial,
  onReload,
  onSaved,
}: {
  initial: Revision;
  onReload: () => Promise<{ isSuccess: boolean; data?: Revision }>;
  onSaved: (studentId: string) => void;
}) {
  // The revision the form was filled from. The changed fields and If-Match always come from this
  // same revision. A background refetch must not replace it: the form would keep the old values
  // while the new ETag let them overwrite a newer save (Codex review of #138). Only "Tải lại"
  // after a 412 moves it to the server's latest revision.
  const [base, setBase] = useState(initial);
  const loaded = base.data;
  const form = useForm<StudentFormInput, unknown, StudentForm>({
    resolver: zodResolver(studentSchema),
    defaultValues: { displayName: loaded.displayName, shortGoal: loaded.shortGoal ?? "" },
  });
  const update = useUpdateStudent();
  const otherErrors = useServerErrors(update.error, form.setError, ["displayName", "shortGoal"]);
  const hasFieldErrors = Object.keys(form.formState.errors).length > 0;
  const [displayName, shortGoal] = useWatch({ control: form.control, name: ["displayName", "shortGoal"] });
  // The contract needs at least one changed field, so there is nothing to save until one differs.
  const hasChanges =
    (displayName ?? "").trim() !== loaded.displayName || (shortGoal ?? "").trim() !== (loaded.shortGoal ?? "");
  const stale = update.state === "failed" && update.error?.kind === "stale";
  const savedId = update.state === "confirmed" ? update.result?.data.id : undefined;

  useEffect(() => {
    if (savedId) {
      onSaved(savedId);
    }
  }, [savedId, onSaved]);

  // Loads the new revision and its ETag. The form keeps what the user typed; they decide again.
  const reload = async () => {
    const result = await onReload();
    if (result.isSuccess && result.data) {
      setBase(result.data);
      update.reset();
    }
  };

  return (
    <KeyboardAvoidingView style={styles.fill} behavior="padding">
      <ScrollView contentContainerStyle={styles.form} keyboardShouldPersistTaps="handled">
        <StudentFields control={form.control} />
      </ScrollView>
      <StickyActionBar>
        {stale ? (
          <Notice
            tone="warning"
            message="Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."
            action={<Button variant="secondary" title="Tải lại" onPress={() => void reload()} />}
          />
        ) : (
          <SaveStatus command={update} hasFieldErrors={hasFieldErrors} otherErrors={otherErrors} />
        )}
        <Button
          title="Lưu thay đổi"
          busy={update.state === "sending" || savedId !== undefined}
          disabled={update.state === "unknown" || stale || !hasChanges}
          onPress={form.handleSubmit((values) =>
            update.submit({ studentId: loaded.id, etag: base.etag, changes: changedFields(loaded, values) }),
          )}
        />
      </StickyActionBar>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  // Lets the form and the action bar reach the screen edges inside QueryScreen's padding.
  fill: { flex: 1, marginHorizontal: -spacing.md, marginBottom: -spacing.md },
  form: { gap: spacing.md, paddingHorizontal: spacing.md, paddingBottom: spacing.md },
});
