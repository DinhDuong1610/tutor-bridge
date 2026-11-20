/** Where sign-in goes when there is no usable `next`. */
export const CONSOLE_HOME = "/admin";

const ORIGIN = "https://tutorbridge.invalid";

/**
 * The `next` the proxy puts on the sign-in link (`proxy.ts`), made safe to redirect to after
 * sign-in (#86). Only a path of the admin console on this site is kept; anything else, such as
 * `//host`, `/\host`, an absolute URL or a path outside `/admin`, gives the console home, so the
 * sign-in page can never send someone to another site.
 *
 * The browser's own URL parser decides, because it also drops tabs and newlines and reads `\` as
 * `/`, which a plain prefix check misses: `/\t/evil.example` is `//evil.example` to a browser.
 */
export function safeNextPath(raw: string | null | undefined): string {
  if (!raw || !raw.startsWith("/")) {
    return CONSOLE_HOME;
  }
  let url: URL;
  try {
    url = new URL(raw, ORIGIN);
  } catch {
    return CONSOLE_HOME;
  }
  if (url.origin !== ORIGIN) {
    return CONSOLE_HOME;
  }
  if (url.pathname !== CONSOLE_HOME && !url.pathname.startsWith(`${CONSOLE_HOME}/`)) {
    return CONSOLE_HOME;
  }
  // The sign-in pages themselves would loop.
  if (url.pathname.startsWith("/admin/sign-in") || url.pathname.startsWith("/admin/session-expired")) {
    return CONSOLE_HOME;
  }
  return `${url.pathname}${url.search}${url.hash}`;
}
