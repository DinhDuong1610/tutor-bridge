"use server";

import { isProblem } from "@tutorbridge/shared";
import { redirect } from "next/navigation";
import { adminApi } from "@/shared/api/server";
import {
  clearChallengeCookie,
  readChallengeToken,
  setChallengeCookie,
  setSessionCookie,
} from "@/shared/session/cookies";
import { safeNextPath } from "@/shared/session/next-path";
import { mfaForm, signInForm, type MfaForm, type SignInForm } from "./schemas";

/**
 * What a sign-in step tells the form. Plain data, and never a token: the challenge stays in an HttpOnly
 * cookie and the session token goes straight into tb_session (AD01, ADR-0010).
 *
 * Not runCommand: here 401 means a wrong password or code, not an ended session, and the two operations
 * take no Idempotency-Key in the contract.
 */
export type SignInStep =
  | { state: "mfa" }
  | { state: "wrong-credentials" }
  | { state: "wrong-code" }
  /** The challenge is used, expired or gone: back to the password step (AdminSignIn.challengeEnded). */
  | { state: "challenge-ended" }
  /** 429: wait {@code retryAfterSeconds}, from Retry-After, then the form opens again (Codex on #253). */
  | { state: "delayed"; retryAfterSeconds: number }
  | { state: "locked" }
  | { state: "invalid"; fields: Record<string, string> }
  | { state: "failed" };

type Answer = { error?: unknown; response: Response };

/** The 15 minutes the delay message names, when the API gives no usable Retry-After. */
const DEFAULT_DELAY_SECONDS = 15 * 60;

/** Retry-After in whole seconds, as the API sends it (TooManyRequests), else the default. */
function retryAfterSeconds(response: Response): number {
  const raw = response.headers.get("Retry-After") ?? "";
  return /^[0-9]+$/.test(raw) && Number(raw) > 0 ? Number(raw) : DEFAULT_DELAY_SECONDS;
}

/** Answers both steps share: the 429 delay, the locked account, a lost connection or a 5xx. */
function common(response: Response, error: unknown): SignInStep {
  if (response.status === 429) {
    return { state: "delayed", retryAfterSeconds: retryAfterSeconds(response) };
  }
  if (response.status === 403 && isProblem(error) && error.code === "ACCOUNT_LOCKED") {
    return { state: "locked" };
  }
  return { state: "failed" };
}

/** Step 1, loginAdmin. On success the challenge goes into its cookie and the form shows step 2. */
export async function signIn(input: SignInForm): Promise<SignInStep> {
  const parsed = signInForm.safeParse(input);
  if (!parsed.success) {
    return { state: "invalid", fields: {} };
  }
  let answer: Answer & { data?: { challengeToken: string; expiresAt: string } };
  try {
    answer = await (await adminApi({ token: null })).POST("/auth/admin-login", { body: parsed.data });
  } catch {
    return { state: "failed" };
  }
  const { data, error, response } = answer;
  if (response.ok && data) {
    await setChallengeCookie(data.challengeToken, new Date(data.expiresAt));
    return { state: "mfa" };
  }
  if (response.status === 401) {
    return { state: "wrong-credentials" };
  }
  if ((response.status === 400 || response.status === 422) && isProblem(error)) {
    return { state: "invalid", fields: Object.fromEntries((error.fields ?? []).map((f) => [f.path, f.message])) };
  }
  return common(response, error);
}

/**
 * Step 2, completeAdminMfa. On success the session cookie is set and the admin goes where the proxy sent
 * them from, through safeNextPath only (#86).
 */
export async function verifyCode(input: MfaForm, next: string | null): Promise<SignInStep> {
  const parsed = mfaForm.safeParse(input);
  if (!parsed.success) {
    return { state: "wrong-code" };
  }
  const challengeToken = await readChallengeToken();
  if (!challengeToken) {
    return { state: "challenge-ended" };
  }
  let answer: Answer & { data?: { accessToken?: string; absoluteExpiresAt: string } };
  try {
    answer = await (await adminApi({ token: null })).POST("/auth/admin-mfa", {
      body: { challengeToken, code: parsed.data.code },
    });
  } catch {
    return { state: "failed" };
  }
  const { data, error, response } = answer;
  if (response.ok && data?.accessToken) {
    await setSessionCookie(data.accessToken, new Date(data.absoluteExpiresAt));
    await clearChallengeCookie();
    redirect(safeNextPath(next));
  }
  if (response.status === 401) {
    if (isProblem(error) && error.code === "MFA_CODE_INVALID") {
      return { state: "wrong-code" };
    }
    await clearChallengeCookie();
    return { state: "challenge-ended" };
  }
  return common(response, error);
}
