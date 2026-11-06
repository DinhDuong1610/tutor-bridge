import { Stack } from "expo-router";

// Tutor area (GS screens). M3 adds the guard that only lets a TUTOR session in.
export default function TutorLayout() {
  return <Stack screenOptions={{ headerShown: false }} />;
}
