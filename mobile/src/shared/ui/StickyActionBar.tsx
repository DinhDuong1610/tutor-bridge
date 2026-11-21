import type { ReactNode } from "react";
import { StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { colors, spacing } from "./theme";

/**
 * The main action of a screen, kept at the bottom below the scrolling content (Figma
 * `StickyActionBar`). On a screen with inputs, put it after the ScrollView and wrap both in
 * `<KeyboardAvoidingView behavior="padding">`, as `AddStudentScreen` does, so the keyboard does not
 * cover it. The top edge is the `divider` line, the only raised surface of the design (`02`).
 */
export function StickyActionBar({ children }: { children: ReactNode }) {
  const insets = useSafeAreaInsets();
  return <View style={[styles.bar, { paddingBottom: Math.max(insets.bottom, spacing.md) }]}>{children}</View>;
}

const styles = StyleSheet.create({
  bar: {
    gap: spacing.sm,
    paddingTop: spacing.md,
    paddingHorizontal: spacing.md,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.divider,
    backgroundColor: colors.background,
  },
});
