import { ActivityIndicator, Pressable, StyleSheet, Text } from "react-native";
import { colors, minTouchSize, radius, spacing, typography } from "./theme";

type Props = {
  title: string;
  onPress: () => void;
  variant?: "primary" | "secondary";
  /** While a command is being sent: shows progress and ignores presses. */
  busy?: boolean;
  disabled?: boolean;
};

/**
 * Figma `Button` (v1). A long label or 200% text wraps inside the button instead of overflowing
 * (`10-ra-soat-nen-08-11.md`, P1). A pressed primary button turns `primaryPressed`; a pressed secondary
 * button gets the `infoSurface` background (Đính, 21/11/2026, #188). Neither turns see-through. A disabled
 * or busy button of either kind is Figma `Button` State=Disabled (6:143, 6:148): `surface` background,
 * `border` edge, `textMuted` text, also not see-through (Đính, 22/11/2026, #95).
 */
export function Button({ title, onPress, variant = "primary", busy = false, disabled = false }: Props) {
  const inactive = busy || disabled;
  const primary = variant === "primary";
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ disabled: inactive, busy }}
      disabled={inactive}
      onPress={onPress}
      style={({ pressed }) => buttonStyle(variant, pressed, inactive)}
    >
      {busy ? <ActivityIndicator color={colors.textMuted} /> : null}
      <Text style={[styles.text, { color: textColor(primary, inactive) }]}>{title}</Text>
    </Pressable>
  );
}

function textColor(primary: boolean, inactive: boolean) {
  if (inactive) return colors.textMuted;
  return primary ? colors.onPrimary : colors.primary;
}

/** The look of the button for a press state; exported so the pressed color can be tested. */
export function buttonStyle(variant: "primary" | "secondary", pressed: boolean, inactive: boolean) {
  const primary = variant === "primary";
  return [
    styles.base,
    primary ? styles.primary : styles.secondary,
    pressed && (primary ? styles.primaryPressed : styles.secondaryPressed),
    inactive && styles.inactive,
  ];
}

const styles = StyleSheet.create({
  base: {
    minHeight: minTouchSize,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radius.sm,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.sm,
  },
  primary: { backgroundColor: colors.primary },
  primaryPressed: { backgroundColor: colors.primaryPressed },
  secondary: { backgroundColor: colors.background, borderWidth: 1, borderColor: colors.primary },
  // primary on infoSurface: 5.85:1 (02-nen-thiet-ke.md).
  secondaryPressed: { backgroundColor: colors.infoSurface },
  // textMuted on surface: 7.2:1; the border edge on surface: 3.5:1 (02-nen-thiet-ke.md asks 3:1 for edges).
  inactive: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border },
  text: { ...typography.label, flexShrink: 1, textAlign: "center" },
});
