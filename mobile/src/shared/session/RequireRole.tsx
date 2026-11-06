import { Redirect } from "expo-router";
import type { ReactNode } from "react";
import type { Role } from "./storage";
import { useSession } from "./SessionProvider";

/**
 * Lets only a session of this role reach the area. Without a session it goes to sign-in; with
 * another role it goes to the entry point, which sends each role to its own home. The server still
 * checks every request; this only keeps the app from showing a screen it cannot fill.
 */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const session = useSession();
  if (session.status === "loading") {
    return null;
  }
  if (session.status === "signedOut") {
    return <Redirect href="/sign-in" />;
  }
  if (session.session.role !== role) {
    return <Redirect href="/" />;
  }
  return children;
}

/** The opposite guard for sign-in screens: a signed-in user goes back to the entry point. */
export function RequireSignedOut({ children }: { children: ReactNode }) {
  const session = useSession();
  if (session.status === "loading") {
    return null;
  }
  if (session.status === "signedIn") {
    return <Redirect href="/" />;
  }
  return children;
}
