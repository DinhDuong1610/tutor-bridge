import type { ReactNode } from "react";
import { StyleSheet, Text, View } from "react-native";
import { Icon, type IconName } from "./Icon";
import { colors, radius, spacing, typography } from "./theme";

// The tone word is the icon's accessible name, so state is never shown by color alone (`02`).
const TONES = {
  info: { label: "Thông tin", icon: "info", color: colors.info, surface: colors.infoSurface },
  success: { label: "Đã xong", icon: "check_circle", color: colors.success, surface: colors.successSurface },
  warning: { label: "Lưu ý", icon: "warning", color: colors.warning, surface: colors.warningSurface },
  danger: { label: "Lỗi", icon: "error", color: colors.danger, surface: colors.dangerSurface },
} as const satisfies Record<string, { label: string; icon: IconName; color: string; surface: string }>;

type Props = {
  tone: keyof typeof TONES;
  message: string;
  /** Usually a Button, such as "Thử lại". It keeps its own width instead of filling the row. */
  action?: ReactNode;
};

/**
 * A message block, Figma `Notice` as in the approved PH01 frames: a tone icon beside the text on a
 * light surface. No colored side edge and no separate label line above the text
 * (`10-ra-soat-nen-08-11.md` P2, #95).
 */
export function Notice({ tone, message, action }: Props) {
  const t = TONES[tone];
  return (
    <View
      accessibilityRole={tone === "danger" || tone === "warning" ? "alert" : undefined}
      accessibilityLiveRegion="polite"
      style={[styles.container, { backgroundColor: t.surface }]}
    >
      <Icon name={t.icon} color={t.color} label={t.label} />
      <View style={styles.body}>
        <Text style={styles.message}>{message}</Text>
        {action ? <View style={styles.action}>{action}</View> : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flexDirection: "row", alignItems: "flex-start", gap: spacing.sm, borderRadius: radius.md, padding: spacing.md },
  body: { flex: 1, gap: spacing.sm },
  message: { ...typography.body, color: colors.text },
  action: { alignSelf: "flex-start" },
});
