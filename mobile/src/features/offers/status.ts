import { formatDateTime, formatVnd } from "@tutorbridge/shared";
import type { IconName } from "@/shared/ui/Icon";
import type { StatusTone } from "@/shared/ui/StatusLabel";
import type { Offer } from "./api";

/**
 * The label of an offer, from the table "Nhãn trạng thái theo hợp đồng" of `03`. An offer the system ended
 * because the need was closed with another tutor reads "Đã kết thúc", as the approved PH09 frame shows it.
 */
export function offerLabel(offer: Pick<Offer, "state" | "reasonCode">): { label: string; tone: StatusTone; icon: IconName } {
  switch (offer.state) {
    case "PENDING":
      return { label: "Đang chờ gia sư trả lời", tone: "info", icon: "schedule" };
    case "ACCEPTED":
      return { label: "Đã chốt lớp", tone: "success", icon: "check_circle" };
    case "REJECTED":
      return { label: "Gia sư đã từ chối", tone: "neutral", icon: "edit" };
    case "EXPIRED":
      return { label: "Đã hết hạn", tone: "warning", icon: "warning" };
    case "CANCELLED":
      return offer.reasonCode === "NEED_CLOSED"
        ? { label: "Đã kết thúc", tone: "neutral", icon: "edit" }
        : { label: "Đã hủy", tone: "neutral", icon: "edit" };
  }
}

/** The sentences of PH09.md for each reason; a code the app does not know gets none (Đính, #72). */
const REASONS: Record<string, string> = {
  NOT_AVAILABLE: "Lý do: không còn rảnh các giờ này.",
  DECLINED: "Lý do: không nhận lớp này.",
  OTHER: "Lý do: lý do khác.",
  PARENT_CANCELLED: "Bạn đã hủy đề nghị này.",
  NEED_CLOSED: "Đã kết thúc vì nhu cầu này đã chốt lớp với gia sư khác.",
};

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

/** "còn 2 ngày", "còn 5 giờ", "còn dưới 1 giờ": whole days first, rounded down (Đính, #72). */
export function remaining(expiresAt: string, now: Date): string {
  const left = new Date(expiresAt).getTime() - now.getTime();
  if (left >= DAY) return `còn ${Math.floor(left / DAY)} ngày`;
  if (left >= HOUR) return `còn ${Math.floor(left / HOUR)} giờ`;
  return "còn dưới 1 giờ";
}

/** The line under the name on a PH09 card; null when there is nothing more to say. */
export function offerLine(offer: Offer, now: Date): string | null {
  switch (offer.state) {
    case "PENDING":
      return `Hết hạn lúc ${formatDateTime(offer.expiresAt)} (${remaining(offer.expiresAt, now)}). Chưa giữ chỗ.`;
    case "ACCEPTED":
      return `Lớp và đủ ${offer.schedule.length} buổi đã được đặt.`;
    case "EXPIRED":
      return `Hết hạn lúc ${formatDateTime(offer.expiresAt)}, gia sư chưa trả lời.`;
    default:
      return offer.reasonCode ? (REASONS[offer.reasonCode] ?? null) : null;
  }
}

/** "8 buổi, 90 phút, 375.000 ₫ / buổi": the terms of a PH09 card. */
export function termsLine(offer: Offer): string {
  return `${offer.schedule.length} buổi, ${offer.durationMinutes} phút, ${formatVnd(offer.priceVndSession)} / buổi`;
}

/** Waiting offers first, then the class, then the rest; newest first within each (PH09 list). */
export function sortOffers(offers: readonly Offer[]): Offer[] {
  const rank = (offer: Offer) => (offer.state === "PENDING" ? 0 : offer.state === "ACCEPTED" ? 1 : 2);
  return [...offers].sort((a, b) => rank(a) - rank(b) || b.sentAt.localeCompare(a.sentAt));
}
