// Asia/Ho_Chi_Minh has been UTC+7 without daylight saving since 1975. A fixed offset keeps the
// output identical on Hermes, Node and browsers, whose Intl time zone data may differ.
const VIETNAM_OFFSET_MS = 7 * 60 * 60 * 1000;

function toVietnamParts(instant: string | Date) {
  const date = typeof instant === "string" ? new Date(instant) : instant;
  const time = date.getTime();
  if (Number.isNaN(time)) throw new RangeError(`Invalid instant: ${String(instant)}`);
  const local = new Date(time + VIETNAM_OFFSET_MS);
  const two = (n: number) => String(n).padStart(2, "0");
  return {
    day: two(local.getUTCDate()),
    month: two(local.getUTCMonth() + 1),
    year: String(local.getUTCFullYear()),
    hour: two(local.getUTCHours()),
    minute: two(local.getUTCMinutes()),
  };
}

/** `dd/MM/yyyy` in Vietnam time. */
export function formatDate(instant: string | Date): string {
  const p = toVietnamParts(instant);
  return `${p.day}/${p.month}/${p.year}`;
}

/** `HH:mm`, 24-hour, in Vietnam time. */
export function formatTime(instant: string | Date): string {
  const p = toVietnamParts(instant);
  return `${p.hour}:${p.minute}`;
}

/** `HH:mm dd/MM/yyyy`, 24-hour, in Vietnam time. */
export function formatDateTime(instant: string | Date): string {
  return `${formatTime(instant)} ${formatDate(instant)}`;
}

/** Money is a whole number of VND (INV-17). Example: `150.000 ₫`. */
export function formatVnd(amount: number): string {
  if (!Number.isSafeInteger(amount)) throw new RangeError(`VND must be a whole number: ${amount}`);
  const digits = String(Math.abs(amount)).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `${amount < 0 ? "-" : ""}${digits} ₫`;
}
