import { Stack } from "expo-router";

// Parent area (PH screens). M3 adds the guard that only lets a PARENT session in.
export default function ParentLayout() {
  return <Stack screenOptions={{ headerShown: false }} />;
}
