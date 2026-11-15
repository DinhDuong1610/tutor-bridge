// PH02 form parts shared by "Thêm hồ sơ con" and "Sửa hồ sơ". Design: docs/design/ui/screens/PH02.md
import { useWatch, type Control } from "react-hook-form";
import { StyleSheet, Text, View } from "react-native";
import type { CommandState } from "@/shared/api/command";
import type { ApiError } from "@/shared/api/errors";
import { FormTextField } from "@/shared/form/FormTextField";
import { lengthOf } from "@/shared/form/length";
import { CommandStatus } from "@/shared/ui/CommandStatus";
import { Notice } from "@/shared/ui/Notice";
import { colors, spacing, typography } from "@/shared/ui/theme";
import { GRADE, type StudentFormInput } from "../schemas";

/** The three parts of a child profile: name, grade 8 as text (never an input), short goal. */
export function StudentFields({ control }: { control: Control<StudentFormInput> }) {
  const goal = useWatch({ control, name: "shortGoal" }) ?? "";
  return (
    <>
      <FormTextField
        control={control}
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
        control={control}
        name="shortGoal"
        label="Mục tiêu ngắn"
        hint={`${formatCount(lengthOf(goal))}/2.000 ký tự. Có thể để trống và bổ sung sau.`}
        multiline
      />
      <Text style={styles.caption}>Không cần ảnh, ngày sinh, tên trường hay địa chỉ.</Text>
    </>
  );
}

type Command = { state: CommandState; error: ApiError | null; retry: () => unknown };

/**
 * The outcome of a save, above the save button. An unknown outcome always offers the safe retry,
 * even while a field shows an error; the screen disables its save button in that state, because a
 * new submit would reuse the kept key with other input (409) and the next save could then create
 * or change the profile twice.
 */
export function SaveStatus({
  command,
  hasFieldErrors,
  otherErrors,
}: {
  command: Command;
  hasFieldErrors: boolean;
  otherErrors: string[];
}) {
  return (
    <>
      {otherErrors.map((message) => (
        <Notice key={message} tone="danger" message={message} />
      ))}
      {command.state === "unknown" ? (
        <CommandStatus command={command} confirmedMessage="" />
      ) : hasFieldErrors ? (
        <Notice tone="danger" message="Vui lòng sửa các ô được đánh dấu rồi gửi lại." />
      ) : command.state === "confirmed" ? null : (
        <CommandStatus command={command} confirmedMessage="" />
      )}
    </>
  );
}

/** 2000 → "2.000", as in the approved copy. */
function formatCount(value: number) {
  return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
}

const styles = StyleSheet.create({
  grade: { gap: spacing.xs },
  label: { ...typography.label, color: colors.text },
  body: { ...typography.body, color: colors.text },
  caption: { ...typography.caption, color: colors.textMuted },
});
