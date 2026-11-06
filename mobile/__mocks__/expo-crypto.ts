// jest-expo's default stand-in returns zero bytes, so every Idempotency-Key would be the same and
// tests could not catch a reused key. Use the Web Crypto source of Node instead.
export function getRandomValues<T extends ArrayBufferView>(typedArray: T): T {
  return globalThis.crypto.getRandomValues(typedArray as unknown as Uint8Array<ArrayBuffer>) as unknown as T;
}
