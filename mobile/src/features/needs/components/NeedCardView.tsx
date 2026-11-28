import { Pressable, StyleSheet, Text, View } from "react-native";
import type { IconName } from "@/shared/ui/Icon";
import { StatusLabel, type StatusTone } from "@/shared/ui/StatusLabel";
import { colors, minTouchSize, radius, spacing, typography } from "@/shared/ui/theme";
import type { Need, NeedAction, NeedCard } from "../summary";

// `03` "Nhãn trạng thái theo hợp đồng": Nháp and Đã đóng neutral, Đã xác nhận info. Icons as in PH03 Figma.
const LABELS: Record<Need["status"], { label: string; tone: StatusTone; icon: IconName }> = {
  DRAFT: { label: "Nháp", tone: "neutral", icon: "edit" },
  CONFIRMED: { label: "Đã xác nhận", tone: "info", icon: "schedule" },
  CLOSED: { label: "Đã đóng", tone: "neutral", icon: "edit" },
};

/** A need's card, as PH03 lists them and PH01 shows a draft under "Việc đang chờ". */
export function NeedCardView({ card, onAction }: { card: NeedCard; onAction: (action: NeedAction) => void }) {
  return (
    <View style={[styles.card, card.status === "CLOSED" && styles.closedCard]}>
      <StatusLabel {...LABELS[card.status]} />
      <Text style={styles.title}>{card.title}</Text>
      <Text style={styles.body}>{card.body}</Text>
      {card.actions.map((action) => (
        <TextAction key={action.kind} title={action.title} onPress={() => onAction(action)} />
      ))}
    </View>
  );
}

export function TextAction({ title, onPress, disabled = false }: { title: string; onPress: () => void; disabled?: boolean }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [styles.action, pressed && styles.actionPressed]}
    >
      <Text style={styles.actionText}>{title}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: { gap: spacing.xs, padding: spacing.md, borderRadius: radius.md, borderWidth: 1, borderColor: colors.divider, backgroundColor: colors.background },
  // A closed need is for reference only; the frame sets it on the surface colour.
  closedCard: { backgroundColor: colors.surface },
  title: { ...typography.bodyStrong, color: colors.text },
  body: { ...typography.body, color: colors.textMuted },
  // A text button as in the frames; the pressable area stays at least 48 tall (minTouchSize).
  action: { alignSelf: "flex-start", minHeight: minTouchSize, justifyContent: "center", borderRadius: radius.sm, marginLeft: -spacing.xs, paddingHorizontal: spacing.xs },
  actionPressed: { backgroundColor: colors.infoSurface },
  actionText: { ...typography.label, color: colors.primary },
});
