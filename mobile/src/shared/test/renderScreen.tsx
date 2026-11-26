import { render, screen } from "@testing-library/react-native";
import type { ReactElement } from "react";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { QueryProvider } from "@/shared/api/QueryProvider";
import { createQueryClient } from "@/shared/api/queryClient";
import { SessionProvider, useSession } from "@/shared/session/SessionProvider";
import type { Session } from "@/shared/session/storage";
import { clearStore, storeSession } from "./session";

const METRICS = {
  frame: { x: 0, y: 0, width: 400, height: 800 },
  insets: { top: 0, left: 0, right: 0, bottom: 0 },
};

function WhenSessionLoaded({ children }: { children: ReactElement }) {
  return useSession().status === "loading" ? null : children;
}

/**
 * Renders one screen with the app's providers (session, TanStack Query, safe area), signed in as
 * `session` when given. Use with `mockApi`. Navigation tests use `renderRouter` instead.
 */
export async function renderScreen(ui: ReactElement, { session }: { session?: Session } = {}) {
  clearStore();
  if (session) {
    storeSession(session);
  }
  // The app's defaults, without the 5 minute cleanup timers that would keep Jest running.
  const client = createQueryClient();
  const defaults = client.getDefaultOptions();
  client.setDefaultOptions({
    queries: { ...defaults.queries, gcTime: Infinity },
    mutations: { ...defaults.mutations, gcTime: Infinity },
  });
  const result = await render(
    <SafeAreaProvider initialMetrics={METRICS}>
      <SessionProvider>
        <QueryProvider client={client}>
          <WhenSessionLoaded>{ui}</WhenSessionLoaded>
        </QueryProvider>
      </SessionProvider>
    </SafeAreaProvider>,
  );
  // Waits until the screen has drawn its title. A screen may have more than one header (section
  // titles help screen reader users jump through the page), so do not expect exactly one.
  await screen.findAllByRole("header");
  // The client, so a test can make a mounted screen read again as the app does on focus or after a write.
  return Object.assign(result, { queryClient: client });
}
