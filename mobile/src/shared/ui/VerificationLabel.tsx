import { StyleSheet, Text, View } from "react-native";
import { Icon } from "./Icon";
import { colors, spacing, typography } from "./theme";

type Props = {
  /** "verified": a scope checked against evidence; "self": what the tutor declared (FR-07, BR07). */
  kind: "verified" | "self";
  text: string;
};

/**
 * Figma `VerificationLabel` (14:221). The two kinds differ by icon shape, not by color alone (`02`), and a
 * verified label names only the scope that was checked, never a promise of teaching quality.
 */
export function VerificationLabel({ kind, text }: Props) {
  const verified = kind === "verified";
  return (
    <View style={styles.row}>
      <Icon name={verified ? "verified_fill" : "person_fill"} size={18} color={verified ? colors.success : colors.textMuted} />
      <Text style={[styles.text, { color: verified ? colors.success : colors.textMuted }]}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", gap: spacing.xs, maxWidth: "100%" },
  text: { ...typography.label, flexShrink: 1 },
});
