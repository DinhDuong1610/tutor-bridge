import { Pressable, StyleSheet, Text, View } from "react-native";
import { Icon, type IconName } from "@/shared/ui/Icon";
import { colors, minTouchSize, radius, spacing, typography } from "@/shared/ui/theme";
import type { Task, TaskTarget } from "../derive";

type Chip = { icon: IconName; label: string; color: string; surface: string };

// Every label has a word and an icon, never color alone (`03`, StatusLabel). Text on its surface:
// textMuted on surface 7.2:1, primary on infoSurface 5.85:1, the tone colors on their surfaces as in Notice.
function chipOf(task: Task): Chip {
  switch (task.kind) {
    case "step":
      return { icon: "edit", label: `Bước ${task.step}`, color: colors.textMuted, surface: colors.surface };
    case "waiting":
      return { icon: "schedule", label: "Đang chờ xác minh", color: colors.info, surface: colors.infoSurface };
    case "needsInfo":
      return { icon: "warning", label: "Cần bổ sung", color: colors.warning, surface: colors.warningSurface };
    case "rejected":
      return { icon: "error", label: "Chưa được duyệt", color: colors.danger, surface: colors.dangerSurface };
    case "done":
      return { icon: "check_circle", label: "Xong", color: colors.success, surface: colors.successSurface };
  }
}

/** A card of "Việc cần làm" (GS01 v2): label, title, detail and a text action. */
export function TaskCard({ task, onOpen }: { task: Task; onOpen: (target: TaskTarget) => void }) {
  const chip = chipOf(task);
  const done = task.kind === "done";
  return (
    <View style={[styles.card, done && styles.doneCard]}>
      <View style={[styles.chip, { backgroundColor: chip.surface }]}>
        <Icon name={chip.icon} color={chip.color} size={16} />
        <Text style={[styles.chipText, { color: chip.color }]}>{chip.label}</Text>
      </View>
      <Text style={styles.title}>{task.title}</Text>
      {task.kind === "step" || task.kind === "waiting" ? <Text style={styles.body}>{task.body}</Text> : null}
      {task.kind === "needsInfo" || task.kind === "rejected" ? (
        // The verifier's reason, verbatim (OP-23).
        <Text style={styles.body}>{`Lý do: ${task.reason}`}</Text>
      ) : null}
      {"action" in task ? <TextAction title={task.action} onPress={() => onOpen(task.target)} /> : null}
    </View>
  );
}

function TextAction({ title, onPress }: { title: string; onPress: () => void }) {
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.action, pressed && styles.actionPressed]}
    >
      <Text style={styles.actionText}>{title}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing.xs, padding: spacing.md, borderRadius: radius.md, borderWidth: 1, borderColor: colors.divider, backgroundColor: colors.background },
  doneCard: { backgroundColor: colors.surface },
  chip: { flexDirection: "row", alignItems: "center", gap: spacing.xs, alignSelf: "flex-start", paddingHorizontal: spacing.sm, paddingVertical: spacing.xs, borderRadius: radius.full },
  chipText: { ...typography.caption, flexShrink: 1 },
  title: { ...typography.bodyStrong, color: colors.text },
  body: { ...typography.body, color: colors.textMuted },
  // A text button as in the frames; the pressable area stays at least 48 tall (minTouchSize).
  action: { alignSelf: "flex-start", minHeight: minTouchSize, justifyContent: "center", borderRadius: radius.sm, marginLeft: -spacing.xs, paddingHorizontal: spacing.xs },
  actionPressed: { backgroundColor: colors.infoSurface },
  actionText: { ...typography.label, color: colors.primary },
});
