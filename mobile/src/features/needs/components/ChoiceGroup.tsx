import { Controller, type Control, type FieldValues, type Path } from "react-hook-form";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { Icon } from "@/shared/ui/Icon";
import { colors, minTouchSize, radius, spacing, typography } from "@/shared/ui/theme";

type Option = { value: string; label: string };

/**
 * Figma `ChoiceGroup` (`03-thanh-phan.md`, PH04): one of a few fixed values side by side, such as 60, 90 or
 * 120 minutes. The chosen one has a check mark and a tinted surface, and is announced as checked, so the
 * choice is never shown by color alone (`02`). Each choice is at least 48 points high; at 200% text they wrap.
 */
export function ChoiceGroup<T extends FieldValues>({
  control,
  name,
  label,
  hint,
  options,
}: {
  control: Control<T, unknown, FieldValues>;
  name: Path<T>;
  label: string;
  hint?: string;
  options: readonly Option[];
}) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        <View style={styles.group}>
          <Text style={styles.label}>{label}</Text>
          <View accessibilityRole="radiogroup" accessibilityLabel={label} style={styles.row}>
            {options.map((option) => {
              const selected = field.value === option.value;
              return (
                <Pressable
                  key={option.value}
                  accessibilityRole="radio"
                  accessibilityLabel={option.label}
                  accessibilityState={{ checked: selected }}
                  onPress={() => field.onChange(option.value)}
                  style={[styles.choice, selected && styles.selected]}
                >
                  {selected ? <Icon name="check" color={colors.primary} size={16} /> : null}
                  <Text style={[styles.text, selected && styles.selectedText]}>{option.label}</Text>
                </Pressable>
              );
            })}
          </View>
          {hint ? <Text style={styles.hint}>{hint}</Text> : null}
        </View>
      )}
    />
  );
}

const styles = StyleSheet.create({
  group: { gap: spacing.xs },
  label: { ...typography.label, color: colors.text },
  row: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  choice: {
    flexGrow: 1,
    flexBasis: 88,
    minHeight: minTouchSize,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.xs,
    paddingHorizontal: spacing.sm,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.sm,
    backgroundColor: colors.background,
  },
  selected: { borderWidth: 2, borderColor: colors.primary, backgroundColor: colors.infoSurface },
  text: { ...typography.body, color: colors.text },
  selectedText: { ...typography.bodyStrong, color: colors.primary },
  hint: { ...typography.caption, color: colors.textMuted },
});
