/**
 * The title of a notification, by the reader's role and the event type. Wording is verbatim from
 * docs/design/ui/screens/COM01.md. A title never names the child, the price or other private data
 * (UC15): the event type alone decides it.
 *
 * The event types are those PR #237 delivers. `eventType` is a free string in the contract, so the
 * table is a proposal (TBD in COM01.md).
 */
export type InboxRole = "PARENT" | "TUTOR";

const TITLES: Record<InboxRole, ReadonlyMap<string, string>> = {
  PARENT: new Map([
    ["OFFER_REJECTED", "Gia sư đã trả lời đề nghị của bạn"],
    ["CLASS_CREATED", "Lớp học đã được chốt"],
    ["OFFER_EXPIRED", "Một đề nghị của bạn đã hết hạn"],
  ]),
  TUTOR: new Map([
    ["OFFER_SENT", "Bạn có đề nghị nhận lớp mới"],
    ["OFFER_CANCELLED", "Phụ huynh đã hủy một đề nghị"],
    ["CLASS_CREATED", "Lớp học đã được chốt"],
    // TBD (asked Đính on #74): Figma has no tutor wording for an expired offer. Temporary, Huy 25/11/2026.
    ["OFFER_EXPIRED", "Một đề nghị đã hết hạn"],
  ]),
};

// TBD (asked Đính on #74): wording for an event type the app does not know yet. Temporary, Huy 25/11/2026.
export const UNKNOWN_EVENT_TITLE = "Có cập nhật mới";

export function eventTitle(role: InboxRole, eventType: string): string {
  return TITLES[role].get(eventType) ?? UNKNOWN_EVENT_TITLE;
}
