import { getMockContext } from "expo-router/testing-library";

/**
 * #123: renderRouter loads the whole src/app tree, and with a cold Jest cache Babel compiled it inside the
 * first test of the file, past the 5 s test timeout. Call this in beforeAll, with its own timeout, so the
 * route files and the screens they import are compiled before any test starts. It reads the tree the way
 * renderRouter("./src/app") does, and renderRouter then finds every module in the file's registry.
 */
export function preloadRoutes() {
  const context = getMockContext("./src/app");
  for (const key of context.keys()) {
    context(key);
  }
}
