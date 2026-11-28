import { StyleSheet, Text, View } from "react-native";
import { colors, radius, spacing, typography } from "./theme";

/** How a session shows in its week, as `SessionCard` shows it (CL02). */
export type CycleMark = "scheduled" | "past" | "cancelled";

type Props = {
  /** "8 buổi trong 28 ngày, 2 buổi mỗi tuần, từ 18/11 đến 15/12/2026". */
  heading: string;
  caption: string;
} & (
  | {
      /** Sessions needed in each of the 4 blocks of 7 days (BR04: the same number every week, at most 3). */
      perWeek: number;
      marks?: never;
    }
  | {
      /** The sessions of a class in `Tuần 1`–`Tuần 4`, one mark each (CL01). */
      marks: readonly (readonly CycleMark[])[];
      perWeek?: never;
    }
);

const MARK_WORDS: Record<Exclude<CycleMark, "scheduled">, string> = { past: "đã qua", cancelled: "đã hủy" };

/** "Tuần 1, 2 buổi", with the ones past or cancelled counted: "Tuần 1, 2 buổi, 1 đã qua". */
function weekLabel(week: number, marks: readonly CycleMark[]): string {
  const extra = (["past", "cancelled"] as const)
    .map((kind) => [kind, marks.filter((mark) => mark === kind).length] as const)
    .filter(([, count]) => count > 0)
    .map(([kind, count]) => `${count} ${MARK_WORDS[kind]}`);
  return [`Tuần ${week}, ${marks.length} buổi`, ...extra].join(", ");
}

/**
 * Figma `CycleStrip` (`03-thanh-phan.md`): the 28 days of a cycle as `Tuần 1`–`Tuần 4`, one mark per session.
 * PH05 shows only how many sessions each week needs, as dashed rings; the times come with the offer (PH08), so
 * CL01 passes the class's sessions as `marks`: a solid dot for a session set, a muted dot once it is over, an
 * empty ring when cancelled (`03`: past and cancelled differ in shape). Each week is read out as
 * "Tuần 1, 2 buổi", not left to the marks.
 */
export function CycleStrip({ heading, perWeek, marks, caption }: Props) {
  return (
    <View style={styles.strip}>
      <Text style={styles.heading}>{heading}</Text>
      <View style={styles.weeks}>
        {[1, 2, 3, 4].map((week) => (
          <View
            key={week}
            accessible
            accessibilityLabel={marks ? weekLabel(week, marks[week - 1] ?? []) : `Tuần ${week}, ${perWeek} buổi`}
            style={styles.week}
          >
            <Text style={styles.weekLabel}>{`Tuần ${week}`}</Text>
            <View style={styles.rings}>
              {marks
                ? (marks[week - 1] ?? []).map((mark, index) => <View key={index} style={styles[mark]} />)
                : Array.from({ length: perWeek }, (_, index) => <View key={index} style={styles.ring} />)}
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
  scheduled: { width: 12, height: 12, borderRadius: radius.full, backgroundColor: colors.primary },
  past: { width: 12, height: 12, borderRadius: radius.full, backgroundColor: colors.textMuted },
  cancelled: { width: 12, height: 12, borderRadius: radius.full, borderWidth: 1, borderColor: colors.textMuted },
  caption: { ...typography.caption, color: colors.textMuted },
});
