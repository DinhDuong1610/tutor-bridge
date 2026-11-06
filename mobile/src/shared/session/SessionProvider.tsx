import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { setTokenSource } from "@/shared/api/client";
import { clearSession, loadSession, saveSession, type Session } from "./storage";

export type SessionState =
  | { status: "loading" }
  | { status: "signedOut"; endedBy: "expired" | "signedOut" | null }
  | { status: "signedIn"; session: Session };

type SessionContextValue = SessionState & {
  /** Keep the session returned by login (TB-08, TB-10). */
  signIn: (session: Session) => Promise<void>;
  /** Forget the session on this device. Call the logout API first when the user signs out. */
  signOut: () => Promise<void>;
  /** The server answered 401: the session ended elsewhere (revoked, expired, password changed). */
  expire: () => Promise<void>;
};

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SessionState>({ status: "loading" });
  const token = useRef<string | null>(null);

  useEffect(() => {
    setTokenSource(() => token.current);
    let cancelled = false;
    loadSession()
      .catch(() => null)
      .then((session) => {
        if (cancelled) return;
        token.current = session?.accessToken ?? null;
        setState(session ? { status: "signedIn", session } : { status: "signedOut", endedBy: null });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const end = useCallback(async (endedBy: "expired" | "signedOut") => {
    token.current = null;
    setState({ status: "signedOut", endedBy });
    await clearSession();
  }, []);

  const signIn = useCallback(async (session: Session) => {
    await saveSession(session);
    token.current = session.accessToken;
    setState({ status: "signedIn", session });
  }, []);

  const signOut = useCallback(() => end("signedOut"), [end]);

  const expire = useCallback(async () => {
    // A 401 while signed out, such as a wrong password at login, is not a session ending.
    if (token.current !== null) {
      await end("expired");
    }
  }, [end]);

  const value = useMemo(() => ({ ...state, signIn, signOut, expire }), [state, signIn, signOut, expire]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const value = useContext(SessionContext);
  if (value === null) {
    throw new Error("useSession must be used inside SessionProvider");
  }
  return value;
}
