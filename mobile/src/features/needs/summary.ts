import type { components } from "@tutorbridge/api-client";
import { formatDate, formatVnd } from "@tutorbridge/shared";

export type Need = components["schemas"]["Need"];
export type Offer = components["schemas"]["Offer"];

/** Where a card's action leads. Each one is a route of #70 Q1 (Đính, 26/11/2026). */
export type NeedAction =
  | { kind: "edit"; title: "Tiếp tục nhập"; needId: string }
  | { kind: "matches"; title: "Tìm gia sư"; needId: string }
  | { kind: "offers"; title: "Xem đề nghị"; needId: string }
  | { kind: "class"; title: "Xem lớp"; classId: string };

export type NeedCard = {
  id: string;
  status: Need["status"];
  title: string;
  body: string;
  actions: NeedAction[];
};

/** What the server already knows about the need's offers, from the pages of listOffers loaded so far. */
export type OfferFacts = {
  /** Offers still PENDING for this need. */
  pending: number;
  /** The accepted offer, once the class is set (TX05 closes the need). */
  accepted?: { classId: string; tutorName?: string };
};

// Labels of PH04's fields, in its order (PH04.md "Ô"). The child's free time is left out: knowing it
// needs the child's calendar and the slot count of #241, so PR 3 adds it (Huy, #70 PR 2 Q1, 27/11/2026).
// TBD: add "giờ rảnh của con" once #241 is merged.
const DRAFT_FIELDS = [
  ["goal", "mục tiêu"],
  ["budgetVndSession", "ngân sách"],
  ["durationMinutes", "thời lượng"],
  ["frequencyPer7Days", "số buổi mỗi tuần"],
  ["cycleStartDate", "ngày bắt đầu"],
] as const;

/** The draft fields still empty, by PH04 label. The server still decides on confirm (confirmNeed). */
export function missingFields(draft: Need["draft"]): string[] {
  return DRAFT_FIELDS.filter(([field]) => {
    const value = draft[field];
    return value === undefined || value === null || (typeof value === "string" && value.trim() === "");
  }).map(([, label]) => label);
}

/** The scope is fixed in R0 (PH04: "Toán 8, đại số, học trực tuyến"), so the title starts with it. */
function titleOf(draft: Need["draft"]): string {
  const goal = draft.goal?.trim();
  return goal ? `Toán 8: ${goal}` : "Toán 8";
}

/** The terms of a confirmed need, as in PH03 Figma "90 phút, 2 buổi mỗi tuần, tối đa 400.000 ₫ / buổi. Bắt đầu 18/11/2026." */
function termsOf(draft: Need["draft"]): string {
  const parts: string[] = [];
  if (draft.durationMinutes) parts.push(`${draft.durationMinutes} phút`);
  if (draft.frequencyPer7Days) parts.push(`${draft.frequencyPer7Days} buổi mỗi tuần`);
  if (draft.budgetVndSession) parts.push(`tối đa ${formatVnd(draft.budgetVndSession)} / buổi`);
  const sentences = parts.length > 0 ? [`${parts.join(", ")}.`] : [];
  if (draft.cycleStartDate) sentences.push(`Bắt đầu ${formatDate(draft.cycleStartDate)}.`);
  return sentences.join(" ");
}

/** One PH03 card. A closed need is kept for reference only: no search, no offers (PH03 "Hành vi"). */
export function needCard(need: Need, offers: OfferFacts): NeedCard {
  const base = { id: need.id, status: need.status, title: titleOf(need.draft) };
  switch (need.status) {
    case "DRAFT": {
      const missing = missingFields(need.draft);
      const lead = missing.length > 0 ? `Cần thêm: ${missing.join(", ")}. ` : "";
      return {
        ...base,
        body: `${lead}Chưa thể tìm gia sư từ bản nháp.`,
        actions: [{ kind: "edit", title: "Tiếp tục nhập", needId: need.id }],
      };
    }
    case "CONFIRMED": {
      // Figma PH03 "Có 1 đề nghị đang chờ." Counted on the first page of listOffers (Huy, #70 PR 2 Q2).
      const pending = offers.pending > 0 ? `Có ${offers.pending} đề nghị đang chờ.` : "";
      return {
        ...base,
        body: [termsOf(need.draft), pending].filter(Boolean).join(" "),
        actions: [
          { kind: "matches", title: "Tìm gia sư", needId: need.id },
          { kind: "offers", title: "Xem đề nghị", needId: need.id },
        ],
      };
    }
    case "CLOSED": {
      // TBD (#70, asked Đính 27/11/2026): Figma adds "ngày {ngày chốt}", which no API returns yet.
      const tutor = offers.accepted?.tutorName;
      return {
        ...base,
        body: tutor ? `Đã chốt lớp với ${tutor}. Bản này chỉ để đối chiếu.` : "Bản này chỉ để đối chiếu.",
        actions: offers.accepted ? [{ kind: "class", title: "Xem lớp", classId: offers.accepted.classId }] : [],
      };
    }
  }
}

/** Offer facts per need, from the offers loaded so far (listOffers does not filter by need, #70 Q1). */
export function offerFactsByNeed(offers: readonly Offer[], tutorNames: ReadonlyMap<string, string>): Map<string, OfferFacts> {
  const facts = new Map<string, OfferFacts>();
  for (const offer of offers) {
    const entry = facts.get(offer.needId) ?? { pending: 0 };
    if (offer.state === "PENDING") entry.pending += 1;
    if (offer.state === "ACCEPTED" && offer.classId) {
      entry.accepted = { classId: offer.classId, tutorName: tutorNames.get(offer.tutorId) };
    }
    facts.set(offer.needId, entry);
  }
  return facts;
}

/** A child has at most one need that is not CLOSED (409 NEED_ALREADY_OPEN, #16 Q4). */
export function hasOpenNeed(needs: readonly Need[]): boolean {
  return needs.some((need) => need.status !== "CLOSED");
}
