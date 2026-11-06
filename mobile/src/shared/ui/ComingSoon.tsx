import { StyleSheet, Text, View } from "react-native";
import { colors, spacing, typography } from "./theme";

/** Temporary body for a route whose screen is not built yet. Feature Issues replace it. */
export function ComingSoon({ title }: { title: string }) {
  return (
    <View style={styles.container}>
      <Text accessibilityRole="header" style={styles.title}>
        {title}
      </Text>
      <Text style={styles.body}>Màn hình đang được xây dựng.</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: "center", justifyContent: "center", gap: spacing.sm, padding: spacing.md, backgroundColor: colors.background },
  title: { ...typography.title, color: colors.text },
  body: { ...typography.body, color: colors.textMuted },
});
