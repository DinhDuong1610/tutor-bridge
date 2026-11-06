import { StyleSheet, Text, View } from "react-native";

/** Temporary body for a route whose screen is not built yet. Feature Issues replace it. */
export function ComingSoon({ title }: { title: string }) {
  return (
    <View style={styles.container}>
      <Text accessibilityRole="header" style={styles.title}>
        {title}
      </Text>
      <Text>Màn hình đang được xây dựng.</Text>
    </View>
  );
}

// Theme tokens replace these values in M4.
const styles = StyleSheet.create({
  container: { flex: 1, alignItems: "center", justifyContent: "center", gap: 8, padding: 16 },
  title: { fontSize: 24, fontWeight: "600" },
});
