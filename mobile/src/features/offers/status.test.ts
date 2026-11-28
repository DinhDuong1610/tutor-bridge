import type { Offer } from "./api";
import { offerLabel, offerLine, remaining, sortOffers, termsLine } from "./status";

const NOW = new Date("2026-11-18T11:00:00Z"); // 18:00 18/11/2026 in Vietnam

function offer(extra: Partial<Offer> = {}): Offer {
  return {
    id: "1d2e3f4a-5b6c-4d7e-8f9a-0b1c2d3e4f5a",
    needId: "3d9e6b5a-4c73-4caf-9c65-8f3b2e4d1a32",
    needVersionId: "4e0f7c6b-5d84-4dba-8d76-9a4c3f5e2b43",
    revision: 1,
    state: "PENDING",
    tutorId: "7b3c0f9e-8ab7-40ed-9a09-2d7f6c8b5e01",
    goalSnapshot: "Toán 8",
    durationMinutes: 90,
    frequencyPer7Days: 2,
    cycleStartDate: "2026-11-18",
    rateVnd60: 250000,
    priceVndSession: 375000,
    schedule: Array.from({ length: 8 }, (_, i) => ({
      start: new Date(Date.UTC(2026, 10, 18 + i * 3, 11)).toISOString(),
      end: new Date(Date.UTC(2026, 10, 18 + i * 3, 12, 30)).toISOString(),
    })),
    sentAt: "2026-11-18T11:00:00Z",
    expiresAt: "2026-11-20T11:00:00Z",
    reasonCode: null,
    classId: null,
    ...extra,
  };
}

describe("PH09 offer text", () => {
  it("labels every state as table 03 does, and an offer ended for another tutor as 'Đã kết thúc'", () => {
    expect(offerLabel(offer())).toEqual({ label: "Đang chờ gia sư trả lời", tone: "info", icon: "schedule" });
    expect(offerLabel(offer({ state: "ACCEPTED" })).label).toBe("Đã chốt lớp");
    expect(offerLabel(offer({ state: "REJECTED" })).label).toBe("Gia sư đã từ chối");
    expect(offerLabel(offer({ state: "EXPIRED" })).tone).toBe("warning");
    expect(offerLabel(offer({ state: "CANCELLED", reasonCode: "PARENT_CANCELLED" })).label).toBe("Đã hủy");
    expect(offerLabel(offer({ state: "CANCELLED", reasonCode: "NEED_CLOSED" })).label).toBe("Đã kết thúc");
  });

  it("says each reason in a full sentence, and nothing for a code the app does not know (Đính, #72)", () => {
    expect(offerLine(offer({ state: "REJECTED", reasonCode: "NOT_AVAILABLE" }), NOW)).toBe("Lý do: không còn rảnh các giờ này.");
    expect(offerLine(offer({ state: "REJECTED", reasonCode: "DECLINED" }), NOW)).toBe("Lý do: không nhận lớp này.");
    expect(offerLine(offer({ state: "REJECTED", reasonCode: "OTHER" }), NOW)).toBe("Lý do: lý do khác.");
    expect(offerLine(offer({ state: "CANCELLED", reasonCode: "PARENT_CANCELLED" }), NOW)).toBe("Bạn đã hủy đề nghị này.");
    expect(offerLine(offer({ state: "CANCELLED", reasonCode: "NEED_CLOSED" }), NOW)).toBe(
      "Đã kết thúc vì nhu cầu này đã chốt lớp với gia sư khác.",
    );
    expect(offerLine(offer({ state: "REJECTED", reasonCode: "SOMETHING_NEW" }), NOW)).toBeNull();
    expect(offerLine(offer({ state: "EXPIRED", reasonCode: "OFFER_EXPIRED" }), NOW)).toBe(
      "Hết hạn lúc 18:00 20/11/2026, gia sư chưa trả lời.",
    );
    expect(offerLine(offer({ state: "ACCEPTED" }), NOW)).toBe("Lớp và đủ 8 buổi đã được đặt.");
  });

  it("counts the time left in whole days, then hours, then 'dưới 1 giờ' (Đính, #72)", () => {
    expect(offerLine(offer(), NOW)).toBe("Hết hạn lúc 18:00 20/11/2026 (còn 2 ngày). Chưa giữ chỗ.");
    expect(remaining("2026-11-20T11:00:00Z", new Date("2026-11-18T12:00:00Z"))).toBe("còn 1 ngày");
    expect(remaining("2026-11-20T11:00:00Z", new Date("2026-11-20T05:30:00Z"))).toBe("còn 5 giờ");
    expect(remaining("2026-11-20T11:00:00Z", new Date("2026-11-20T10:30:00Z"))).toBe("còn dưới 1 giờ");
  });

  it("writes the terms and lists waiting offers first, then the class, newest first", () => {
    expect(termsLine(offer())).toBe("8 buổi, 90 phút, 375.000 ₫ / buổi");
    const a = offer({ id: "a", state: "REJECTED", sentAt: "2026-11-18T12:00:00Z" });
    const b = offer({ id: "b", state: "PENDING", sentAt: "2026-11-17T12:00:00Z" });
    const c = offer({ id: "c", state: "PENDING", sentAt: "2026-11-18T10:00:00Z" });
    const d = offer({ id: "d", state: "ACCEPTED", sentAt: "2026-11-16T10:00:00Z" });
    expect(sortOffers([a, b, c, d]).map((o) => o.id)).toEqual(["c", "b", "d", "a"]);
  });
});
