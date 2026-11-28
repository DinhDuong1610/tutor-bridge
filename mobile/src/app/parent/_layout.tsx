import { Stack } from "expo-router";
import { SelectedStudentProvider } from "@/features/students/selection";
import { RequireRole } from "@/shared/session/RequireRole";

// Parent area (PH screens), only for a PARENT session. The chosen child is shared by every PH screen.
export default function ParentLayout() {
  return (
    <RequireRole role="PARENT">
      <SelectedStudentProvider>
        <Stack screenOptions={{ headerShown: false }} />
      </SelectedStudentProvider>
    </RequireRole>
  );
}
