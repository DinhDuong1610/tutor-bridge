import { Stack } from "expo-router";
import { RequireSignedOut } from "@/shared/session/RequireRole";

// Screens for people without a session: AUTH01, AUTH03, AUTH04. A signed-in user goes back to the
// entry point. Email links (AUTH02, AUTH05) live in (links) so they work while signed in.
export default function AuthLayout() {
  return (
    <RequireSignedOut>
      <Stack screenOptions={{ headerShown: false }} />
    </RequireSignedOut>
  );
}
