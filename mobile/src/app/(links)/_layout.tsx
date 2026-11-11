import { Stack } from "expo-router";

// Screens opened from email links (AUTH02, AUTH05). Not guarded like (auth): a link opened while
// signed in must still reach its screen, or the single-use token in it is lost (handoff TB-10, Q8).
export default function LinkLayout() {
  return <Stack screenOptions={{ headerShown: false }} />;
}
