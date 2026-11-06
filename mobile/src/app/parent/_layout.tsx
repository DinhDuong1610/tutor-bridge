import { Stack } from "expo-router";
import { RequireRole } from "@/shared/session/RequireRole";

// Parent area (PH screens), only for a PARENT session.
export default function ParentLayout() {
  return (
    <RequireRole role="PARENT">
      <Stack screenOptions={{ headerShown: false }} />
    </RequireRole>
  );
}
