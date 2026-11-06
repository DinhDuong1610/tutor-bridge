import type { ReactNode } from "react";
import { StyleSheet, Text, View } from "react-native";
import { colors, radius, spacing, typography } from "./theme";

const TONES = {
  info: { label: "Thông tin", color: colors.info, surface: colors.infoSurface },
  success: { label: "Đã xong", color: colors.success, surface: colors.successSurface },
  warning: { label: "Lưu ý", color: colors.warning, surface: colors.warningSurface },
  danger: { label: "Lỗi", color: colors.danger, surface: colors.dangerSurface },
} as const;

type Props = {
  tone: keyof typeof TONES;
  message: string;
  /** Usually a Button, such as "Thử lại". */
  action?: ReactNode;
};

/** A message block. The tone is written as a word, not only shown as a color. */
export function Notice({ tone, message, action }: Props) {
  const t = TONES[tone];
  return (
    <View
      accessibilityRole={tone === "danger" || tone === "warning" ? "alert" : undefined}
      accessibilityLiveRegion="polite"
      style={[styles.container, { backgroundColor: t.surface, borderLeftColor: t.color }]}
    >
      <Text style={[styles.tone, { color: t.color }]}>{t.label}</Text>
      <Text style={styles.message}>{message}</Text>
      {action}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { borderLeftWidth: 4, borderRadius: radius.sm, padding: spacing.md, gap: spacing.sm },
  tone: { ...typography.label },
  message: { ...typography.body, color: colors.text },
});
