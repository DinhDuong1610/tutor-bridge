import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { QueryProvider } from "@/shared/api/QueryProvider";
import { SessionProvider } from "@/shared/session/SessionProvider";

// Root navigator. The theme provider is added by module M4.
export default function RootLayout() {
  return (
    <SessionProvider>
      <QueryProvider>
        <Stack screenOptions={{ headerShown: false }} />
        <StatusBar style="auto" />
      </QueryProvider>
    </SessionProvider>
  );
}
