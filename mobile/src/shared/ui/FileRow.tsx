import { StyleSheet, Text, View } from "react-native";
import { Icon } from "./Icon";
import { StatusLabel } from "./StatusLabel";
import { colors, spacing, typography } from "./theme";

export type FileState = "uploading" | "checking" | "ready" | "error";

// `03`: the state of `TutorEvidenceRecord` as GS03 says it.
const STATES = {
  uploading: { label: "Đang tải lên", tone: "info", icon: "progress_activity" },
  checking: { label: "Đang kiểm tra", tone: "info", icon: "progress_activity" },
  ready: { label: "Sẵn sàng", tone: "success", icon: "check_circle" },
  error: { label: "Lỗi", tone: "danger", icon: "error" },
} as const;

type Props = {
  name: string;
  /** Kind and size, such as "PDF, 2,4 MB". */
  detail: string;
  state: FileState;
  /** Shown under the label when the state is "error". */
  errorMessage?: string;
};

/** Figma `FileRow`: a file icon, the name on at most two lines, kind and size, its state. No delete (Lát 1). */
export function FileRow({ name, detail, state, errorMessage }: Props) {
  const s = STATES[state];
  return (
    <View style={styles.row}>
      <Icon name="description_fill" color={colors.textMuted} />
      <View style={styles.body}>
        <Text style={styles.name} numberOfLines={2}>
          {name}
        </Text>
        <Text style={styles.detail}>{detail}</Text>
        <StatusLabel label={s.label} tone={s.tone} icon={s.icon} />
        {state === "error" && errorMessage ? <Text style={styles.error}>{errorMessage}</Text> : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    gap: spacing.md,
    paddingVertical: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.divider,
  },
  body: { flex: 1, gap: spacing.xs },
  name: { ...typography.bodyStrong, color: colors.text },
  detail: { ...typography.caption, color: colors.textMuted },
  error: { ...typography.caption, color: colors.danger },
});
