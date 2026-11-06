import type { components } from "@tutorbridge/api-client";
import * as SecureStore from "expo-secure-store";

/** The login answer for mobile (contract `SessionCredential`), which always carries a token. */
export type Session = components["schemas"]["SessionCredential"] & { accessToken: string };
export type Role = Session["role"];

const KEY = "tb.session";
const ROLES: readonly Role[] = ["PARENT", "TUTOR", "STUDENT", "ADMIN"];

// Kept in the OS secure store (ADR-0004), never in AsyncStorage, and never backed up to another device.
const OPTIONS: SecureStore.SecureStoreOptions = {
  keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
};

function isSession(value: unknown): value is Session {
  if (typeof value !== "object" || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v.sessionId === "string" &&
    typeof v.accessToken === "string" &&
    v.accessToken.length >= 20 &&
    ROLES.includes(v.role as Role) &&
    typeof v.idleExpiresAt === "string" &&
    typeof v.absoluteExpiresAt === "string"
  );
}

/**
 * The saved session, or null. A damaged entry or one past its absolute expiry is removed, so the
 * app never starts with a session the server will refuse anyway.
 */
export async function loadSession(now: Date = new Date()): Promise<Session | null> {
  const raw = await SecureStore.getItemAsync(KEY, OPTIONS);
  if (raw === null) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    parsed = null;
  }
  if (!isSession(parsed) || Date.parse(parsed.absoluteExpiresAt) <= now.getTime()) {
    await clearSession();
    return null;
  }
  return parsed;
}

export async function saveSession(session: Session): Promise<void> {
  await SecureStore.setItemAsync(KEY, JSON.stringify(session), OPTIONS);
}

export async function clearSession(): Promise<void> {
  await SecureStore.deleteItemAsync(KEY, OPTIONS);
}
