import { Pressable, StyleSheet, Text, View } from "react-native";
import { Icon } from "./Icon";
import { colors, radius, spacing, typography } from "./theme";

/** Variants of `03` "Thẻ lịch buổi học" that a class shows. `proposed` and `compact` come with PH08 and later screens. */
export type SessionCardVariant = "scheduled" | "past" | "cancelled";

type Props = {
  variant: SessionCardVariant;
  /** The date block: "T4", "18", "Th11". */
  date: { weekday: string; day: string; month: string };
  /** "18:00–19:30", Vietnam time. */
  time: string;
  /** "Buổi 1 trên 8. 90 phút, Toán 8 với Nguyễn Thị Lan". */
  detail: string;
  /** Opens the session's details. */
  onPress?: () => void;
};

// Every variant differs by more than colour (`03`): a word, and the block's fill or the struck time.
const LABELS: Record<SessionCardVariant, { text: string; color: string }> = {
  scheduled: { text: "Đã chốt", color: colors.success },
  past: { text: "Đã qua", color: colors.textMuted },
  cancelled: { text: "Đã hủy", color: colors.textMuted },
};

/**
 * Figma `SessionCard`, the one accent of the design (`01`): a solid date block for a scheduled
 * session, a plain one once it has passed or was cancelled.
 */
export function SessionCard({ variant, date, time, detail, onPress }: Props) {
  const label = LABELS[variant];
  const solid = variant === "scheduled";
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${date.weekday} ${date.day} ${date.month}, ${time}. ${detail}. ${label.text}`}
      onPress={onPress}
      disabled={!onPress}
      style={({ pressed }) => [styles.card, pressed && styles.pressed]}
    >
      <View style={[styles.block, solid ? styles.blockSolid : styles.blockPlain]}>
        <Text style={[styles.blockSmall, solid && styles.onPrimary]}>{date.weekday}</Text>
        <Text style={[styles.blockDay, solid && styles.onPrimary]}>{date.day}</Text>
        <Text style={[styles.blockSmall, solid && styles.onPrimary]}>{date.month}</Text>
      </View>
      <View style={styles.body}>
        <Text style={[styles.time, variant === "past" && styles.muted, variant === "cancelled" && styles.struck]}>{time}</Text>
        <Text style={styles.detail}>{detail}</Text>
        <View style={styles.label}>
          <Icon name="event" color={label.color} size={16} />
          <Text style={[styles.labelText, { color: label.color }]}>{label.text}</Text>
        </View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: "row",
    gap: spacing.md,
    padding: spacing.md,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.divider,
    backgroundColor: colors.background,
  },
  pressed: { backgroundColor: colors.surface },
  // No fixed height: the block grows with the text at 200% (`03`, chữ 200%).
  block: { alignItems: "center", alignSelf: "flex-start", minWidth: 56, paddingVertical: spacing.sm, paddingHorizontal: spacing.xs, borderRadius: radius.sm },
  blockSolid: { backgroundColor: colors.primary },
  blockPlain: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.divider },
  blockSmall: { ...typography.caption, color: colors.textMuted },
  blockDay: { ...typography.dateNumeral, color: colors.text },
  onPrimary: { color: colors.onPrimary },
  body: { flex: 1, gap: spacing.xs },
  time: { ...typography.bodyStrong, color: colors.text },
  muted: { color: colors.textMuted },
  struck: { color: colors.textMuted, textDecorationLine: "line-through" },
  detail: { ...typography.body, color: colors.textMuted },
  label: { flexDirection: "row", alignItems: "center", gap: spacing.xs },
  labelText: { ...typography.label },
});
