/**
 * The browser's address in an X-Forwarded-For list (#252). Next sets the header to the socket's address only
 * when it is missing, so the last entry is the one the closest hop wrote; a browser that sends its own list
 * only adds entries to its left. {@code hops} counts the trusted proxies in front of the one that saw the
 * browser (TB_CLIENT_IP_HOPS, 0 by default), skipped from the right.
 *
 * Next must sit behind a proxy that appends the peer address (nginx: $proxy_add_x_forwarded_for). Exposed
 * directly, the browser can choose the value: the API then still counts failures per email, not per source.
 */
export function clientIpFrom(forwardedFor: string | null, hops: number): string | null {
  const entries = (forwardedFor ?? "").split(",").map((entry) => entry.trim()).filter(Boolean);
  return entries[entries.length - 1 - hops] ?? null;
}

/**
 * TB_CLIENT_IP_HOPS as a whole number, 0 when unset. Anything else throws when the server starts: a typo
 * would otherwise send no address and put every admin back on one count.
 */
export function clientIpHops(raw: string | undefined): number {
  if (raw === undefined || raw === "") {
    return 0;
  }
  if (!/^[0-9]+$/.test(raw)) {
    throw new Error(`TB_CLIENT_IP_HOPS must be a whole number of 0 or more, not "${raw}"`);
  }
  return Number(raw);
}
