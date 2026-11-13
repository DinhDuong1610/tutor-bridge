import { Pressable, StyleSheet, Text, View } from "react-native";
import { colors, minTouchSize, spacing, typography } from "./theme";

type Props = {
  title: string;
  /** Shows a back button. Leave it out on a screen the user cannot go back from. */
  onBack?: () => void;
};

/**
 * The bar at the top of a screen: back button and screen title (Figma `TopAppBar`). The arrow is a
 * text glyph; an icon package needs Đính's approval (ADR-0009).
 */
export function TopAppBar({ title, onBack }: Props) {
  return (
    <View style={styles.bar}>
      {onBack ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Quay lại"
          hitSlop={spacing.xs}
          onPress={onBack}
          style={({ pressed }) => [styles.back, pressed && styles.pressed]}
        >
          <Text style={styles.arrow}>←</Text>
        </Pressable>
      ) : null}
      <Text accessibilityRole="header" style={styles.title}>
        {title}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { flexDirection: "row", alignItems: "center", gap: spacing.xs },
  back: { minWidth: minTouchSize, minHeight: minTouchSize, alignItems: "center", justifyContent: "center", marginLeft: -spacing.sm },
  pressed: { opacity: 0.6 },
  arrow: { ...typography.heading, color: colors.text },
  title: { ...typography.title, color: colors.text, flexShrink: 1 },
});
