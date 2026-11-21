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
 * button gets the `infoSurface` background (Đính, 21/11/2026, #188). Neither turns see-through.
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
      {busy ? <ActivityIndicator color={primary ? colors.onPrimary : colors.primary} /> : null}
      <Text style={[styles.text, { color: primary ? colors.onPrimary : colors.primary }]}>{title}</Text>
    </Pressable>
  );
}

/** The look of the button for a press state; exported so the pressed color can be tested. */
export function buttonStyle(variant: "primary" | "secondary", pressed: boolean, inactive: boolean) {
  const primary = variant === "primary";
  return [
    styles.base,
    primary ? styles.primary : styles.secondary,
    pressed && (primary ? styles.primaryPressed : styles.secondaryPressed),
    // TBD (Đính): the disabled color from Figma `Button` State=Disabled is not a token yet.
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
  inactive: { opacity: 0.6 },
  text: { ...typography.label, flexShrink: 1, textAlign: "center" },
});
