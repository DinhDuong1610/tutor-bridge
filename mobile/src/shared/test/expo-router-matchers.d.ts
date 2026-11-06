// Types for the Jest matchers that expo-router/testing-library registers at runtime but does not
// declare. Keep the list in step with node_modules/expo-router/build/testing-library/expect.js.
declare global {
  namespace jest {
    interface Matchers<R> {
      toHavePathname(pathname: string): R;
      toHavePathnameWithParams(pathnameWithParams: string): R;
      toHaveSegments(segments: string[]): R;
      toHaveSearchParams(params: Record<string, string | string[]>): R;
      toHaveRouterState(state: unknown): R;
    }
  }
}

export {};
