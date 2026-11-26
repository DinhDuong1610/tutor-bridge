import { StyleSheet, Text, View } from "react-native";
import { Icon, type IconName } from "./Icon";
import { colors, radius, spacing, typography } from "./theme";

// Tones of `03-thanh-phan.md` "Nhãn trạng thái theo hợp đồng": a light surface and a colored icon.
const TONES = {
  neutral: { color: colors.text, surface: colors.surface },
  info: { color: colors.info, surface: colors.infoSurface },
  success: { color: colors.success, surface: colors.successSurface },
  warning: { color: colors.warning, surface: colors.warningSurface },
  danger: { color: colors.danger, surface: colors.dangerSurface },
} as const;

export type StatusTone = keyof typeof TONES;

type Props = { label: string; tone: StatusTone; icon: IconName };

/**
 * Figma `StatusLabel`: the label of a contract status, always as text beside an icon, never a colored
 * dot alone (`03`). Callers take the label and tone from the table in `03`; the icon is decoration,
 * since the text already says the state.
 */
export function StatusLabel({ label, tone, icon }: Props) {
  const t = TONES[tone];
  return (
    <View style={[styles.label, { backgroundColor: t.surface }]}>
      <Icon name={icon} color={t.color} size={16} />
      <Text style={[styles.text, { color: t.color }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  label: {
    flexDirection: "row",
    alignItems: "center",
    alignSelf: "flex-start",
    flexShrink: 1,
    gap: spacing.xs,
    paddingHorizontal: spacing.sm,
    paddingVertical: spacing.xs,
    borderRadius: radius.sm,
  },
  text: { ...typography.label, flexShrink: 1 },
});
