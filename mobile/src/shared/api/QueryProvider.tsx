import { QueryClientProvider } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { useSession } from "@/shared/session/SessionProvider";
import { createQueryClient, watchSessionEnd } from "./queryClient";

/**
 * TanStack Query for the app, tied to the session: a 401 ends the session, and when the session
 * ends nothing loaded for that user stays in memory for the next one.
 */
export function QueryProvider({ children }: { children: ReactNode }) {
  const { status, expire } = useSession();
  const [client] = useState(createQueryClient);

  useEffect(() => watchSessionEnd(client, () => void expire()), [client, expire]);

  useEffect(() => {
    if (status === "signedOut") {
      client.clear();
    }
  }, [client, status]);

  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
