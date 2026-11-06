import { Redirect } from "expo-router";

// Entry point. M3 sends a signed-in user to the home of their role; until then everyone signs in.
export default function Index() {
  return <Redirect href="/sign-in" />;
}
