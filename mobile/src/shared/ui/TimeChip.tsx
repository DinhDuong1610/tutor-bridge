import { Pressable, StyleSheet, Text, View } from "react-native";
import { Icon } from "./Icon";
import { colors, minTouchSize, radius, spacing, typography } from "./theme";

export type TimeChipVariant = "draft" | "effective" | "booked";

type Props = {
  /** "18:00–21:00". */
  label: string;
  variant: TimeChipVariant;
  /**
   * Removes the slot. Figma does not draw how a slot is taken off (#70 Q3, pending Đính); a booked
   * session is never edited here (UC07), so it gets no handler.
   */
  onRemove?: () => void;
};

// `03-thanh-phan.md`: three variants differ by shape and words, not only by color.
const VARIANTS = {
  draft: { icon: "edit", suffix: " nháp", spoken: "nháp, chưa xác nhận" },
  effective: { icon: "check", suffix: "", spoken: "đang hiệu lực" },
  booked: { icon: "event", suffix: " đã có buổi", spoken: "đã có buổi, không sửa ở đây" },
} as const;

/** Figma `TimeChip` (PH04, GS04): one slot of a day in `WeekAvailability`. */
export function TimeChip({ label, variant, onRemove }: Props) {
  const v = VARIANTS[variant];
  const booked = variant === "booked";
  const content = (
    <>
      <Icon name={v.icon} color={booked ? colors.onPrimary : colors.primary} size={16} />
      <Text style={[styles.text, booked && styles.bookedText]}>
        {label}
        {v.suffix ? <Text style={styles.suffix}>{v.suffix}</Text> : null}
      </Text>
    </>
  );
  const look = [styles.chip, variant === "draft" && styles.draft, booked && styles.booked];
  if (!onRemove || booked) {
    return (
      <View accessible accessibilityLabel={`${label}, ${v.spoken}`} style={look}>
        {content}
      </View>
    );
  }
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${label}, ${v.spoken}`}
      accessibilityHint="Nhấn để bỏ khung giờ này."
      onPress={onRemove}
      style={({ pressed }) => [...look, pressed && styles.pressed]}
    >
      {content}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    minHeight: minTouchSize,
    flexDirection: "row",
    alignItems: "center",
    alignSelf: "flex-start",
    flexShrink: 1,
    gap: spacing.xs,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    borderRadius: radius.sm,
    borderWidth: 1,
    borderColor: colors.primary,
    backgroundColor: colors.infoSurface,
  },
  draft: { borderStyle: "dashed", borderColor: colors.border, backgroundColor: colors.background },
  booked: { borderColor: colors.primary, backgroundColor: colors.primary },
  pressed: { backgroundColor: colors.surface },
  text: { ...typography.label, color: colors.text, flexShrink: 1 },
  bookedText: { color: colors.onPrimary },
  suffix: { ...typography.caption },
});
