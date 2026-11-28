import { formatVnd } from "@tutorbridge/shared";
import { StyleSheet, Text, View } from "react-native";
import { colors, spacing, typography } from "./theme";

type Props = {
  /** The price of one session as the server quoted it; the app never computes it (BR02). */
  priceVndSession: number;
  durationMinutes: number;
  rateVnd60: number;
};

/** Figma `PriceBlock` (7:124): always the three numbers, the session price, its length and the hourly rate. */
export function PriceBlock({ priceVndSession, durationMinutes, rateVnd60 }: Props) {
  return (
    <View style={styles.block}>
      <Text style={styles.session}>{`${formatVnd(priceVndSession)} / buổi ${durationMinutes} phút`}</Text>
      <Text style={styles.rate}>{`Giá ${formatVnd(rateVnd60)} / 60 phút`}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  block: { gap: spacing.xs },
  session: { ...typography.heading, color: colors.text },
  rate: { ...typography.caption, color: colors.textMuted },
});
