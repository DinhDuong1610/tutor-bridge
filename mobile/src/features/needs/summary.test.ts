import { hasOpenNeed, missingFields, needCard, offerFactsByNeed, type Need, type Offer } from "./summary";

// Fake data only (AGENTS.md).
const NEED_ID = "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10";
const OTHER_NEED = "1c8d5a4f-3b62-4b9f-8b54-7e2a1d3c0f21";
const TUTOR = "2d9e6b50-4c73-4caf-9c65-8f3b2e4d1032";
const CLASS = "3e0f7c61-5d84-4dba-8d76-903c3f5e2143";

const FULL: Need["draft"] = {
  goal: "hằng đẳng thức và phân thức",
  budgetVndSession: 400000,
  durationMinutes: 90,
  frequencyPer7Days: 2,
  cycleStartDate: "2026-11-18",
};

const need = (status: Need["status"], draft: Need["draft"] = FULL): Need => ({
  id: NEED_ID,
  studentId: "4f108d72-6e95-4ecb-9e87-a14d406f3254",
  revision: 2,
  versionId: "50219e83-7fa6-4fdc-8f98-b25e5170436a",
  status,
  draft,
  studentCalendarId: status === "DRAFT" ? null : "61320f94-80b7-4aed-90a9-c36f6281547b",
});

const offer = (state: Offer["state"], needId = NEED_ID, classId: string | null = null): Offer =>
  ({ id: `offer-${state}-${needId}`, needId, state, tutorId: TUTOR, classId }) as Offer;

describe("PH03 card wording (summary)", () => {
  it("lists the draft fields still empty by PH04 label, in PH04's order", () => {
    expect(missingFields({ goal: "ôn thi học kỳ 2", durationMinutes: 90 })).toEqual([
      "ngân sách",
      "số buổi mỗi tuần",
      "ngày bắt đầu",
    ]);
    expect(missingFields({ goal: "   " })).toContain("mục tiêu");
    expect(missingFields(FULL)).toEqual([]);
  });

  it("a draft says what is missing and only offers to continue (UC04: no search from a draft)", () => {
    const card = needCard(need("DRAFT", { goal: "ôn thi học kỳ 2", durationMinutes: 90, frequencyPer7Days: 2, cycleStartDate: "2026-11-30" }), { pending: 0 });
    expect(card.title).toBe("Toán 8: ôn thi học kỳ 2");
    expect(card.body).toBe("Cần thêm: ngân sách. Chưa thể tìm gia sư từ bản nháp.");
    expect(card.actions).toEqual([{ kind: "edit", title: "Tiếp tục nhập", needId: NEED_ID }]);
  });

  it("a complete draft still says it cannot be searched yet", () => {
    expect(needCard(need("DRAFT"), { pending: 0 }).body).toBe("Chưa thể tìm gia sư từ bản nháp.");
  });

  it("a draft without a goal is titled by the fixed scope", () => {
    expect(needCard(need("DRAFT", {}), { pending: 0 }).title).toBe("Toán 8");
  });

  it("a confirmed need shows its terms as in Figma, with the pending offers when there are some", () => {
    expect(needCard(need("CONFIRMED"), { pending: 1 }).body).toBe(
      "90 phút, 2 buổi mỗi tuần, tối đa 400.000 ₫ / buổi. Bắt đầu 18/11/2026. Có 1 đề nghị đang chờ.",
    );
    const card = needCard(need("CONFIRMED"), { pending: 0 });
    expect(card.body).toBe("90 phút, 2 buổi mỗi tuần, tối đa 400.000 ₫ / buổi. Bắt đầu 18/11/2026.");
    expect(card.actions.map((a) => a.title)).toEqual(["Tìm gia sư", "Xem đề nghị"]);
  });

  it("a closed need is for reference only: no search and no offers, only its class (PH03 Hành vi)", () => {
    const card = needCard(need("CLOSED"), { pending: 0, accepted: { classId: CLASS, tutorName: "Nguyễn Thị Lan" } });
    expect(card.body).toBe("Đã chốt lớp với Nguyễn Thị Lan. Bản này chỉ để đối chiếu.");
    expect(card.actions).toEqual([{ kind: "class", title: "Xem lớp", classId: CLASS }]);
  });

  it("a closed need whose offer or tutor name did not load keeps the reference sentence", () => {
    expect(needCard(need("CLOSED"), { pending: 0, accepted: { classId: CLASS } }).body).toBe("Bản này chỉ để đối chiếu.");
    const bare = needCard(need("CLOSED"), { pending: 0 });
    expect(bare.body).toBe("Bản này chỉ để đối chiếu.");
    expect(bare.actions).toEqual([]);
  });
});

describe("offer facts per need", () => {
  it("counts pending offers per need and takes the accepted offer's class and tutor", () => {
    const facts = offerFactsByNeed(
      [offer("PENDING"), offer("PENDING"), offer("REJECTED"), offer("ACCEPTED", OTHER_NEED, CLASS)],
      new Map([[TUTOR, "Nguyễn Thị Lan"]]),
    );
    expect(facts.get(NEED_ID)).toEqual({ pending: 2 });
    expect(facts.get(OTHER_NEED)).toEqual({ pending: 0, accepted: { classId: CLASS, tutorName: "Nguyễn Thị Lan" } });
  });

  it("knows a child has an open need when any is not closed", () => {
    expect(hasOpenNeed([need("CLOSED"), need("DRAFT")])).toBe(true);
    expect(hasOpenNeed([need("CLOSED")])).toBe(false);
    expect(hasOpenNeed([])).toBe(false);
  });
});
