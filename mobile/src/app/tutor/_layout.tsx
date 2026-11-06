import { Stack } from "expo-router";
import { RequireRole } from "@/shared/session/RequireRole";

// Tutor area (GS screens), only for a TUTOR session.
export default function TutorLayout() {
  return (
    <RequireRole role="TUTOR">
      <Stack screenOptions={{ headerShown: false }} />
    </RequireRole>
  );
}
