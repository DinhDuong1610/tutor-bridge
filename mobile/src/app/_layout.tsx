import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";

// Root navigator. Providers for data, session and theme are added by modules M2–M4.
export default function RootLayout() {
  return (
    <>
      <Stack screenOptions={{ headerShown: false }} />
      <StatusBar style="auto" />
    </>
  );
}
