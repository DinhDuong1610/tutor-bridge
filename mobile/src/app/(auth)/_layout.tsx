import { Stack } from "expo-router";

// Screens for people without a session: AUTH01–AUTH05.
export default function AuthLayout() {
  return <Stack screenOptions={{ headerShown: false }} />;
}
