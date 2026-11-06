import { render } from "@testing-library/react-native";
import { ExpoRoot } from "expo-router";
import { getMockContext } from "expo-router/testing-library";

/**
 * Renders the real route tree of src/app at a URL. Replaces renderRouter from
 * expo-router/testing-library, which expects the synchronous render of Testing Library 13;
 * this project uses 14, where render is async.
 */
export async function renderRoute(initialUrl: string) {
  // Load route files eagerly, as renderRouter does, so the first render shows the screen.
  process.env.EXPO_ROUTER_IMPORT_MODE = "sync";
  return render(<ExpoRoot context={getMockContext("./src/app")} location={initialUrl} />);
}
