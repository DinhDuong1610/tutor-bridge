import { deriveTutorHome, hasOpenAvailability, type TutorCalendar, type TutorProfile, type TutorVerification } from "./derive";

const NOW = new Date("2026-11-22T03:00:00Z");

const profile = (version = 1, rateVnd60 = 250000): TutorProfile => ({
  id: "4d2a9c1e-7b3f-4e8a-9c21-5f6e7d8c9b10",
  version,
  revision: 1,
  status: "DRAFT",
  description: "Dạy Toán 8 theo từng bước.",
  experienceMonths: 24,
  rateVnd60,
});

const verification = (state: TutorVerification["state"], over: Partial<TutorVerification> = {}): TutorVerification => ({
  id: "5e3b0d2f-8c4a-4f9b-8d32-6a7f8e9d0c21",
  profileVersionId: "6f4c1e3a-9d5b-4a0c-9e43-7b8a9f0e1d32",
  profileVersion: 1,
  state,
  scope: "Toán 8, đại số",
  reason: state === "NEEDS_INFO" || state === "REJECTED" ? "Ảnh bằng tốt nghiệp bị mờ." : null,
  decidedAt: state === "WAITING" ? null : "2026-11-21T08:00:00Z",
  approvedVersion: state === "VERIFIED" ? 1 : null,
  ...over,
});

const calendar = (windows: TutorCalendar["windows"]): TutorCalendar => ({
  id: "7a5d2f4b-0e6c-4b1d-8f54-8c9b0a1f2e43",
  version: 1,
  timezone: "Asia/Ho_Chi_Minh",
  windows,
});
const OPEN = calendar([{ start: "2026-11-24T11:00:00Z", end: "2026-11-24T13:00:00Z", kind: "AVAILABLE" }]);

const kinds = (home: ReturnType<typeof deriveTutorHome>) => home.tasks.map((task) => (task.kind === "step" ? `step${task.step}` : task.kind));

describe("hasOpenAvailability (TB-14 Q4)", () => {
  it("needs a window that is not BLOCKED and still ends after now", () => {
    expect(hasOpenAvailability(null, NOW)).toBe(false);
    expect(hasOpenAvailability(calendar([]), NOW)).toBe(false);
    expect(hasOpenAvailability(OPEN, NOW)).toBe(true);
    expect(hasOpenAvailability(calendar([{ start: "2026-11-24T11:00:00Z", end: "2026-11-24T13:00:00Z", kind: "PREFERRED" }]), NOW)).toBe(true);
    expect(hasOpenAvailability(calendar([{ start: "2026-11-24T11:00:00Z", end: "2026-11-24T13:00:00Z", kind: "BLOCKED" }]), NOW)).toBe(false);
    // Ended exactly now: nothing left to offer.
    expect(hasOpenAvailability(calendar([{ start: "2026-11-22T01:00:00Z", end: "2026-11-22T03:00:00Z", kind: "AVAILABLE" }]), NOW)).toBe(false);
  });
});

describe("deriveTutorHome (GS01 v2)", () => {
  it("frame Mới đăng ký: three steps and both reasons", () => {
    const home = deriveTutorHome({ profile: null, verification: null, calendar: null }, NOW);
    expect(kinds(home)).toEqual(["step1", "step2", "step3"]);
    expect(home.visibility).toEqual({
      kind: "hidden",
      message: "Hồ sơ của bạn chưa xuất hiện khi phụ huynh tìm gia sư: chưa gửi xác minh và chưa có lịch rảnh.",
    });
    expect(home.visibilityFirst).toBe(true);
  });

  it("a saved profile moves step 1 to done; a calendar drops step 3 and its reason", () => {
    expect(kinds(deriveTutorHome({ profile: profile(), verification: null, calendar: null }, NOW))).toEqual(["step2", "step3", "done"]);
    const home = deriveTutorHome({ profile: profile(), verification: null, calendar: OPEN }, NOW);
    expect(kinds(home)).toEqual(["step2", "done"]);
    expect(home.visibility?.message).toBe("Hồ sơ của bạn chưa xuất hiện khi phụ huynh tìm gia sư: chưa gửi xác minh.");
  });

  it("frame Đang chờ xác minh, first submission", () => {
    const home = deriveTutorHome({ profile: profile(), verification: verification("WAITING"), calendar: null }, NOW);
    expect(kinds(home)).toEqual(["waiting", "step3", "done"]);
    expect(home.visibility).toEqual({ kind: "hidden", message: "Hồ sơ chưa xuất hiện khi phụ huynh tìm: đang chờ xác minh." });
  });

  it.each([
    ["NEEDS_INFO", "needsInfo", "Xem và gửi lại", "Hồ sơ chưa xuất hiện khi phụ huynh tìm: cần bổ sung hồ sơ."],
    ["REJECTED", "rejected", "Sửa hồ sơ và gửi lại", "Hồ sơ chưa xuất hiện khi phụ huynh tìm: hồ sơ chưa được duyệt."],
  ] as const)("OP-23 %s shows the reason verbatim and opens GS03", (state, kind, action, message) => {
    const home = deriveTutorHome({ profile: profile(), verification: verification(state), calendar: OPEN }, NOW);
    expect(home.tasks[0]).toMatchObject({ kind, reason: "Ảnh bằng tốt nghiệp bị mờ.", action, target: "evidence" });
    expect(kinds(home)).toEqual([kind, "done"]);
    expect(home.visibility).toEqual({ kind: "hidden", message });
  });

  it.each(["WAITING", "NEEDS_INFO", "REJECTED"] as const)(
    "OP-24 %s of an edit while an approved version stays public hides the block (TB-14 Q1)",
    (state) => {
      const home = deriveTutorHome({ profile: profile(2), verification: verification(state, { profileVersion: 2, approvedVersion: 1 }), calendar: OPEN }, NOW);
      expect(home.visibility).toBeNull();
      // The task card is right in every case (GS01.md, Hành vi).
      expect(home.tasks[0].kind).not.toBe("step");
    },
  );

  it("frame Đang nhận lớp: verified with a calendar, rate of the approved version", () => {
    const home = deriveTutorHome({ profile: profile(1, 250000), verification: verification("VERIFIED"), calendar: OPEN }, NOW);
    // Offers are hidden until TB-18 (Đính, #14 câu 1), so nothing is left to do.
    expect(home.tasks).toEqual([]);
    expect(home.visibility).toEqual({
      kind: "verified",
      scope: "Toán 8, đại số",
      message: "Phụ huynh có thể thấy và gửi đề nghị cho bạn. Học phí 250.000 ₫ / 60 phút; kinh nghiệm là tự khai.",
    });
    expect(home.visibilityFirst).toBe(false);
  });

  it("verified but a newer draft is saved: no rate, it may differ from what parents see (TB-14 Q5)", () => {
    const home = deriveTutorHome({ profile: profile(2, 300000), verification: verification("VERIFIED"), calendar: OPEN }, NOW);
    expect(home.visibility?.message).toBe("Phụ huynh có thể thấy và gửi đề nghị cho bạn.");
  });

  it("verified without a calendar is not listed yet (TB-14 Q3)", () => {
    const home = deriveTutorHome({ profile: profile(), verification: verification("VERIFIED"), calendar: null }, NOW);
    expect(kinds(home)).toEqual(["step3"]);
    expect(home.visibility).toEqual({
      kind: "verified",
      scope: "Toán 8, đại số",
      message: "Hồ sơ của bạn chưa xuất hiện khi phụ huynh tìm gia sư: chưa có lịch rảnh.",
    });
  });
});
