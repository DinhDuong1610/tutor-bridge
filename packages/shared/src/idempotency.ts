export const IDEMPOTENCY_KEY_MIN_LENGTH = 16;
export const IDEMPOTENCY_KEY_MAX_LENGTH = 128;

/** Fills the array with cryptographically secure random bytes, like `crypto.getRandomValues`. */
export type RandomBytes = (bytes: Uint8Array<ArrayBuffer>) => Uint8Array<ArrayBuffer>;

function defaultRandomBytes(bytes: Uint8Array<ArrayBuffer>): Uint8Array<ArrayBuffer> {
  const crypto = globalThis.crypto;
  if (crypto === undefined || typeof crypto.getRandomValues !== "function") {
    // Never fall back to Math.random: a guessable key lets another request replay a command.
    throw new Error("No secure random source. Pass one to newIdempotencyKey.");
  }
  return crypto.getRandomValues(bytes);
}

/**
 * A new key for one user action. Keep it while retrying the same action, so the server
 * returns the first result instead of running the command twice (INV-20).
 */
export function newIdempotencyKey(randomBytes: RandomBytes = defaultRandomBytes): string {
  const bytes = randomBytes(new Uint8Array(16));
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

export function isValidIdempotencyKey(key: string): boolean {
  return key.length >= IDEMPOTENCY_KEY_MIN_LENGTH && key.length <= IDEMPOTENCY_KEY_MAX_LENGTH;
}
