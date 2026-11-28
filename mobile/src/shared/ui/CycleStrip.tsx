import { StyleSheet, Text, View } from "react-native";
import { colors, radius, spacing, typography } from "./theme";

type Props = {
  /** "8 buổi trong 28 ngày, 2 buổi mỗi tuần, từ 18/11 đến 15/12/2026". */
  heading: string;
  /** Sessions needed in each of the 4 blocks of 7 days (BR04: the same number every week, at most 3). */
  perWeek: number;
  caption: string;
};

/**
 * Figma `CycleStrip` (`03-thanh-phan.md`): the 28 days of a cycle as `Tuần 1`–`Tuần 4`, one mark per session.
 * PH05 shows only how many sessions each week needs, as dashed rings; the times come with the offer (PH08), so
 * marks for past or cancelled sessions are added by the screens that have them. Each week is read out as
 * "Tuần 1, 2 buổi", not left to the rings.
 */
export function CycleStrip({ heading, perWeek, caption }: Props) {
  return (
    <View style={styles.strip}>
      <Text style={styles.heading}>{heading}</Text>
      <View style={styles.weeks}>
        {[1, 2, 3, 4].map((week) => (
          <View key={week} accessible accessibilityLabel={`Tuần ${week}, ${perWeek} buổi`} style={styles.week}>
            <Text style={styles.weekLabel}>{`Tuần ${week}`}</Text>
            <View style={styles.rings}>
              {Array.from({ length: perWeek }, (_, index) => (
                <View key={index} style={styles.ring} />
              ))}
            </View>
          </View>
        ))}
      </View>
      <Text style={styles.caption}>{caption}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  strip: {
    gap: spacing.sm,
    padding: spacing.md,
    borderWidth: 1,
    borderStyle: "dashed",
    borderColor: colors.border,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
  },
  heading: { ...typography.label, color: colors.text },
  weeks: { flexDirection: "row", flexWrap: "wrap", gap: spacing.sm },
  week: {
    flexGrow: 1,
    flexBasis: 64,
    gap: spacing.xs,
    padding: spacing.sm,
    borderRadius: radius.sm,
    backgroundColor: colors.background,
  },
  weekLabel: { ...typography.caption, color: colors.text },
  rings: { flexDirection: "row", flexWrap: "wrap", gap: spacing.xs },
  ring: { width: 12, height: 12, borderRadius: radius.full, borderWidth: 1, borderStyle: "dashed", borderColor: colors.primary },
  caption: { ...typography.caption, color: colors.textMuted },
});
