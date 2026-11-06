import { QueryClientProvider, type QueryClient } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { useSession } from "@/shared/session/SessionProvider";
import { createQueryClient, watchSessionEnd } from "./queryClient";

/**
 * TanStack Query for the app, tied to the session: a 401 ends the session, and when the session
 * ends nothing loaded for that user stays in memory for the next one.
 */
export function QueryProvider({ children, client: given }: { children: ReactNode; client?: QueryClient }) {
  const { status, expire } = useSession();
  // Tests pass their own client (shared/test/renderScreen); the app creates one.
  const [client] = useState(() => given ?? createQueryClient());

  useEffect(() => watchSessionEnd(client, () => void expire()), [client, expire]);

  useEffect(() => {
    if (status === "signedOut") {
      client.clear();
    }
  }, [client, status]);

  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
