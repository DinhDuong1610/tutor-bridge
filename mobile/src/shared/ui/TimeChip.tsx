import { Pressable, StyleSheet, Text, View } from "react-native";
import { Icon } from "./Icon";
import { colors, minTouchSize, radius, spacing, typography } from "./theme";

export type TimeChipVariant = "draft" | "effective" | "booked";

type Props = {
  /** "18:00–21:00". */
  label: string;
  variant: TimeChipVariant;
  /** "Thứ 2": named by the remove button, "Bỏ khung Thứ 2 18:00–21:00". */
  day: string;
  /**
   * Removes the slot, from an × at the end of the chip (Đính, #70 Q3, 26/11/2026); the removal takes
   * effect only on save. A booked session is never edited here (UC07), so it has no ×.
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
export function TimeChip({ label, variant, day, onRemove }: Props) {
  const v = VARIANTS[variant];
  const booked = variant === "booked";
  const removable = onRemove && !booked;
  return (
    <View style={[styles.chip, variant === "draft" && styles.draft, booked && styles.booked, removable && styles.removable]}>
      <View accessible accessibilityLabel={`${label}, ${v.spoken}`} style={styles.content}>
        <Icon name={v.icon} color={booked ? colors.onPrimary : colors.primary} size={16} />
        <Text style={[styles.text, booked && styles.bookedText]}>
          {label}
          {v.suffix ? <Text style={styles.suffix}>{v.suffix}</Text> : null}
        </Text>
      </View>
      {removable ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`Bỏ khung ${day} ${label}`}
          onPress={onRemove}
          style={({ pressed }) => [styles.remove, pressed && styles.pressed]}
        >
          <Icon name="close" color={colors.text} size={20} />
        </Pressable>
      ) : null}
    </View>
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
  content: { flexDirection: "row", alignItems: "center", gap: spacing.xs, flexShrink: 1 },
  // The × keeps its 48 point target (`minTouchSize`) inside the chip's own height.
  removable: { paddingRight: 0 },
  remove: {
    minWidth: minTouchSize,
    minHeight: minTouchSize,
    marginVertical: -spacing.xs,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radius.sm,
  },
  draft: { borderStyle: "dashed", borderColor: colors.border, backgroundColor: colors.background },
  booked: { borderColor: colors.primary, backgroundColor: colors.primary },
  pressed: { backgroundColor: colors.surface },
  text: { ...typography.label, color: colors.text, flexShrink: 1 },
  bookedText: { color: colors.onPrimary },
  suffix: { ...typography.caption },
});
