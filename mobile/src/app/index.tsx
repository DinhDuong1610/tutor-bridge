import { Redirect } from "expo-router";
import { Button, StyleSheet, Text, View } from "react-native";
import { useSession } from "@/shared/session/SessionProvider";

// Entry point: sends each session to the home of its role.
export default function Index() {
  const session = useSession();
  if (session.status === "loading") {
    return null;
  }
  if (session.status === "signedOut") {
    return <Redirect href="/sign-in" />;
  }
  switch (session.session.role) {
    case "PARENT":
      return <Redirect href="/parent" />;
    case "TUTOR":
      return <Redirect href="/tutor" />;
    default:
      // Students (P1) and admins (web) have no mobile screens in Lát 1.
      return (
        <View style={styles.container}>
          <Text accessibilityRole="header" style={styles.title}>
            Tài khoản này chưa dùng được trên ứng dụng
          </Text>
          <Button title="Đăng xuất" onPress={() => void session.signOut()} />
        </View>
      );
  }
}

// Theme tokens replace these values in M4.
const styles = StyleSheet.create({
  container: { flex: 1, alignItems: "center", justifyContent: "center", gap: 12, padding: 16 },
  title: { fontSize: 20, fontWeight: "600", textAlign: "center" },
});
