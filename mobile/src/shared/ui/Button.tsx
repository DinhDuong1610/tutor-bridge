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
      style={({ pressed }) => [
        styles.base,
        primary ? styles.primary : styles.secondary,
        inactive && styles.inactive,
        pressed && styles.pressed,
      ]}
    >
      {busy ? <ActivityIndicator color={primary ? colors.onPrimary : colors.primary} /> : null}
      <Text style={[styles.text, { color: primary ? colors.onPrimary : colors.primary }]}>{title}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    minHeight: minTouchSize,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radius.md,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.sm,
  },
  primary: { backgroundColor: colors.primary },
  secondary: { backgroundColor: colors.background, borderWidth: 1, borderColor: colors.primary },
  inactive: { opacity: 0.6 },
  pressed: { opacity: 0.85 },
  text: { ...typography.label, fontSize: 16 },
});
