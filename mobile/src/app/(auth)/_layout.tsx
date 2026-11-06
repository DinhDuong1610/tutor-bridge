import { Stack } from "expo-router";
import { RequireSignedOut } from "@/shared/session/RequireRole";

// Screens for people without a session: AUTH01–AUTH05. A signed-in user goes back to the entry point.
export default function AuthLayout() {
  return (
    <RequireSignedOut>
      <Stack screenOptions={{ headerShown: false }} />
    </RequireSignedOut>
  );
}
