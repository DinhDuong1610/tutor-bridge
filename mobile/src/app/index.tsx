import { Redirect } from "expo-router";
import { StyleSheet, Text, View } from "react-native";
import { useSession } from "@/shared/session/SessionProvider";
import { Button } from "@/shared/ui/Button";
import { colors, spacing, typography } from "@/shared/ui/theme";

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

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: "center", justifyContent: "center", gap: spacing.md, padding: spacing.md, backgroundColor: colors.background },
  title: { ...typography.heading, color: colors.text, textAlign: "center" },
});
