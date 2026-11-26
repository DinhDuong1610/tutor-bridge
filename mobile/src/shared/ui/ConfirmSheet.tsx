import type { ReactNode } from "react";
import { Modal, Pressable, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Button } from "./Button";
import { colors, radius, spacing, typography } from "./theme";

type Props = {
  visible: boolean;
  title: string;
  message: string;
  confirmTitle: string;
  onConfirm: () => void;
  onCancel: () => void;
  /** Under the message, such as the outcome of the command the sheet started. */
  children?: ReactNode;
  busy?: boolean;
  /** Such as while an unknown outcome waits for its own "Thử lại". */
  confirmDisabled?: boolean;
};

/**
 * Figma `ConfirmSheet`: a bottom sheet that asks before a command that cannot be undone, with the
 * action and "Quay lại". The dimmed area and the system back button close it like "Quay lại", except
 * while the command is sending.
 */
export function ConfirmSheet({ visible, title, message, confirmTitle, onConfirm, onCancel, children, busy = false, confirmDisabled = false }: Props) {
  const insets = useSafeAreaInsets();
  const cancel = () => {
    if (!busy) onCancel();
  };
  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={cancel}>
      <View style={styles.backdrop}>
        <Pressable style={styles.fill} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" onPress={cancel} />
        <View accessibilityViewIsModal style={[styles.sheet, { paddingBottom: Math.max(insets.bottom, spacing.md) }]}>
          <View style={styles.handle} />
          <Text accessibilityRole="header" style={styles.title}>
            {title}
          </Text>
          <Text style={styles.message}>{message}</Text>
          {children}
          <Button title={confirmTitle} busy={busy} disabled={confirmDisabled} onPress={onConfirm} />
          <Button variant="secondary" title="Quay lại" disabled={busy} onPress={cancel} />
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  // The frame dims the screen behind the sheet: `text` at 40% (hex alpha 66).
  backdrop: { flex: 1, justifyContent: "flex-end", backgroundColor: `${colors.text}66` },
  fill: { flex: 1 },
  sheet: {
    gap: spacing.md,
    paddingHorizontal: spacing.md,
    paddingTop: spacing.sm,
    backgroundColor: colors.background,
    borderTopLeftRadius: radius.md,
    borderTopRightRadius: radius.md,
  },
  handle: { alignSelf: "center", width: 32, height: 4, borderRadius: radius.full, backgroundColor: colors.border },
  title: { ...typography.heading, color: colors.text },
  message: { ...typography.body, color: colors.text },
});
