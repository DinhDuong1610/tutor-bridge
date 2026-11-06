import { Link } from "expo-router";
import { StyleSheet, Text, View } from "react-native";
import { colors, spacing, typography } from "@/shared/ui/theme";

// Shown for a link to a screen that does not exist, for example an old notification.
export default function NotFound() {
  return (
    <View style={styles.container}>
      <Text accessibilityRole="header" style={styles.title}>
        Không tìm thấy màn hình
      </Text>
      <Link href="/" accessibilityRole="link" style={styles.link}>
        Về trang đầu
      </Link>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: "center", justifyContent: "center", gap: spacing.sm, padding: spacing.md, backgroundColor: colors.background },
  title: { ...typography.heading, color: colors.text },
  link: { ...typography.body, color: colors.primary, textDecorationLine: "underline" },
});
