import { Link } from "expo-router";
import { StyleSheet, Text, View } from "react-native";

// Shown for a link to a screen that does not exist, for example an old notification.
export default function NotFound() {
  return (
    <View style={styles.container}>
      <Text accessibilityRole="header" style={styles.title}>
        Không tìm thấy màn hình
      </Text>
      <Link href="/" accessibilityRole="link">
        Về trang đầu
      </Link>
    </View>
  );
}

// Theme tokens replace these values in M4.
const styles = StyleSheet.create({
  container: { flex: 1, alignItems: "center", justifyContent: "center", gap: 8, padding: 16 },
  title: { fontSize: 20, fontWeight: "600" },
});
