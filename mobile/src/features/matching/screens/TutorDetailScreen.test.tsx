import { act, fireEvent, screen } from "@testing-library/react-native";
import type { Need } from "@/features/needs/api";
import { SelectedStudentProvider } from "@/features/students/selection";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import type { Candidate, MatchingRun } from "../api";
import { TutorDetailScreen } from "./TutorDetailScreen";

// Fake children, tutors and texts only (AGENTS.md).
const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "" };
const LAN = { id: "1c8d5a4f-3b62-4b9f-8b54-7e2a1d3c0f21", displayName: "Lan Chi", grade: 8, revision: 1, shortGoal: "" };
const NEED_ID = "3d9e6b5a-4c73-4caf-9c65-8f3b2e4d1a32";
const RUN_ID = "6a2b9e8d-7fa6-4fdc-8f98-1c6e5b7a4d65";
const TUTOR_ID = "7b3c0f9e-8ab7-40ed-9a09-2d7f6c8b5e01";
const PROFILE = "8c4d1a0f-9bc8-41fe-8b1a-3e8a7d9c6f76";
const DESCRIPTION =
  "Tôi dạy Toán THCS 2 năm, tập trung vào đại số lớp 8. Mỗi buổi tôi cho học sinh tự làm trước, chữa lỗi ngay và giao 3–5 bài luyện thêm vừa sức.";

const need: Need = {
  id: NEED_ID,
  studentId: MINH.id,
  revision: 2,
  versionId: "4e0f7c6b-5d84-4dba-8d76-9a4c3f5e2b43",
  status: "CONFIRMED",
  draft: { goal: "Toán 8: hằng đẳng thức và phân thức", durationMinutes: 90, frequencyPer7Days: 2, budgetVndSession: 400000, cycleStartDate: "2026-11-18" },
  studentCalendarId: null,
};

/** Thứ 2 and Thứ 4, 18:00–19:30 in Vietnam, from 18/11 to 14/12/2026 (the approved frame). */
const SCHEDULE = [
  "2026-11-18", "2026-11-23", "2026-11-25", "2026-11-30", "2026-12-02", "2026-12-07", "2026-12-09", "2026-12-14",
].map((day) => ({ start: new Date(`${day}T18:00:00+07:00`).toISOString(), end: new Date(`${day}T19:30:00+07:00`).toISOString() }));

function tutor(extra: Partial<Candidate["tutor"]> = {}): Candidate["tutor"] {
  return {
    tutorId: TUTOR_ID,
    profileVersionId: PROFILE,
    displayName: "Nguyễn Thị Lan",
    description: DESCRIPTION,
    rateVnd60: 250000,
    experienceMonths: 24,
    experienceLabel: "SELF_DECLARED",
    verificationScopes: ["MATH8_ALGEBRA"],
    ...extra,
  };
}

function run(candidateTutor = tutor()): MatchingRun {
  return {
    id: RUN_ID,
    needVersionId: need.versionId,
    mode: "B",
    status: "COMPLETED",
    expiresAt: "2026-11-28T05:00:00Z",
    candidates: [
      {
        tutor: candidateTutor,
        rank: 1,
        priceVndSession: 375000,
        commonSchedule: SCHEDULE,
        reasons: ["Kinh nghiệm 24 tháng (tự khai)."],
        tutorCalendarVersionId: "9d5e2b1a-0cd9-42af-9c2b-4f9b8e0d7a87",
      },
    ],
    failureCode: null,
    studentCalendarVersionId: "0e6f3c2b-1dea-43b0-8d3c-5a0c9f1e8b98",
  };
}

const needAnswer = () =>
  new Response(JSON.stringify(need), { status: 200, headers: { "Content-Type": "application/json", ETag: '"2"' } });
const students = () => json(200, { items: [MINH, LAN], nextCursor: null });

function server(routes: { run?: () => Response; current?: () => Response } = {}) {
  return mockApi({
    "GET /matching-runs/{runId}": routes.run ?? (() => json(200, run())),
    "GET /tutors/{tutorId}/public-profile": routes.current ?? (() => json(200, tutor())),
    "GET /needs/{needId}": needAnswer,
    "GET /students": students,
  });
}

async function renderDetail(needId = NEED_ID) {
  const handlers = { onBack: jest.fn(), onOtherChild: jest.fn(), onManageStudents: jest.fn() };
  const rendered = await renderScreen(
    <SelectedStudentProvider>
      <TutorDetailScreen tutorId={TUTOR_ID} runId={RUN_ID} needId={needId} {...handlers} />
    </SelectedStudentProvider>,
    { session: fakeSession("PARENT") },
  );
  return { ...handlers, queryClient: rendered.queryClient };
}

describe("PH07 Chi tiết gia sư công khai", () => {
  let api: ReturnType<typeof mockApi>;
  afterEach(() => api.restore());

  it("FR-12 shows the candidate as compared on PH06: labels, price for this need, teaching and common sessions", async () => {
    api = server();
    await renderDetail();

    expect(await screen.findByRole("header", { name: "Nguyễn Thị Lan" })).toBeTruthy();
    expect(screen.getByRole("header", { name: "Thông tin gia sư" })).toBeTruthy();
    expect(screen.getByLabelText("Đổi con. Đang xem Minh An, lớp 8")).toBeTruthy();
    for (const text of [
      "Đã xác minh: Toán 8, đại số",
      "Kinh nghiệm 24 tháng (tự khai)",
      "Nhãn xác minh chỉ nói phạm vi đã kiểm minh chứng, không bảo đảm chất lượng giảng dạy.",
      "Học phí cho nhu cầu của Minh An",
      "375.000 ₫ / buổi 90 phút",
      "Giá 250.000 ₫ / 60 phút",
      "Cách dạy (tự giới thiệu)",
      DESCRIPTION,
      "Giờ chung trong chu kỳ",
      "Đủ 8 buổi: Thứ 2 và Thứ 4, 18:00–19:30, từ 18/11 đến 14/12/2026. Giờ này chưa giữ chỗ.",
    ]) {
      expect(screen.getByText(text)).toBeTruthy();
    }
    // "Gửi đề nghị" comes with PH08 in #72 (Đính, #71).
    expect(screen.queryByRole("button", { name: "Gửi đề nghị" })).toBeNull();
  });

  it("worst case: a long name in full, two verified scopes and the count of the rest", async () => {
    const many = tutor({ displayName: "Lê Văn Tám – giáo viên Toán THCS, 12 năm luyện thi vào lớp 10", verificationScopes: Array(4).fill("MATH8_ALGEBRA") });
    api = server({ run: () => json(200, run(many)), current: () => json(200, many) });
    await renderDetail();

    expect(await screen.findByRole("header", { name: "Lê Văn Tám – giáo viên Toán THCS, 12 năm luyện thi vào lớp 10" })).toBeTruthy();
    expect(screen.getAllByText("Đã xác minh: Toán 8, đại số")).toHaveLength(2);
    expect(screen.getByText("và 2 phạm vi khác")).toBeTruthy();
  });

  it.each([
    ["the profile is no longer public (404)", () => problem(404, "TUTOR_NOT_FOUND", "Không tìm thấy gia sư.")],
    ["the profile changed since the run", () => json(200, tutor({ profileVersionId: "1f7a4d3c-2efb-44c1-9e4d-6b1d0a2f9c09" }))],
  ])("%s: 'Không còn khả dụng', and back to the list", async (_case, current) => {
    api = server({ current });
    const { onBack } = await renderDetail();

    expect(await screen.findByText("Không tìm thấy nội dung này. Có thể nó đã bị xóa hoặc bạn không có quyền xem.")).toBeTruthy();
    expect(screen.getByText("Hồ sơ có thể đã ẩn hoặc thay đổi. Quay lại danh sách và tìm lại để có kết quả mới.")).toBeTruthy();
    // Terms that no longer hold are not shown.
    expect(screen.queryByText("375.000 ₫ / buổi 90 phút")).toBeNull();
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Quay lại danh sách" })));
    expect(onBack).toHaveBeenCalled();
  });

  it("a run of an older version of the need is not shown with the new version's duration (as PH06, Codex #274)", async () => {
    // The need was edited and confirmed again at 120 minutes after this run priced 90-minute sessions.
    const moved = { ...need, revision: 4, versionId: "5f1a8d7c-6e95-4ecb-9e87-0b5d4a6f3c54", draft: { ...need.draft, durationMinutes: 120 } };
    api = mockApi({
      "GET /matching-runs/{runId}": () => json(200, run()),
      "GET /tutors/{tutorId}/public-profile": () => json(200, tutor()),
      "GET /needs/{needId}": () =>
        new Response(JSON.stringify(moved), { status: 200, headers: { "Content-Type": "application/json", ETag: '"4"' } }),
      "GET /students": students,
    });
    await renderDetail();

    expect(await screen.findByText("Hồ sơ có thể đã ẩn hoặc thay đổi. Quay lại danh sách và tìm lại để có kết quả mới.")).toBeTruthy();
    expect(screen.queryByText(/375.000 ₫ \/ buổi/)).toBeNull();
  });

  it("a profile hidden while the screen is open (200, then 404 on a reread) drops the terms (Codex, #275)", async () => {
    let gone = false;
    api = server({ current: () => (gone ? problem(404, "TUTOR_NOT_FOUND", "Không tìm thấy gia sư.") : json(200, tutor())) });
    const { queryClient } = await renderDetail();
    expect(await screen.findByText("375.000 ₫ / buổi 90 phút")).toBeTruthy();

    gone = true;
    await act(async () => {
      await queryClient.refetchQueries({ queryKey: ["tutors", "public", TUTOR_ID] });
    });

    expect(await screen.findByText("Hồ sơ có thể đã ẩn hoặc thay đổi. Quay lại danh sách và tìm lại để có kết quả mới.")).toBeTruthy();
    expect(screen.queryByText("375.000 ₫ / buổi 90 phút")).toBeNull();
  });

  it("a run paired with another need in the route is not shown with that need's terms (Codex, #275)", async () => {
    const other = { ...need, id: "2a7c6b5d-4e3f-4d2c-9b1a-0f9e8d7c6b5a", versionId: "3b8d7c6e-5f4a-4e3d-8c2b-1a0f9e8d7c6b" };
    api = mockApi({
      "GET /matching-runs/{runId}": () => json(200, run()),
      "GET /tutors/{tutorId}/public-profile": () => json(200, tutor()),
      "GET /needs/{needId}": () =>
        new Response(JSON.stringify(other), { status: 200, headers: { "Content-Type": "application/json", ETag: '"2"' } }),
      "GET /students": students,
    });
    await renderDetail(other.id);

    expect(await screen.findByText("Hồ sơ có thể đã ẩn hoặc thay đổi. Quay lại danh sách và tìm lại để có kết quả mới.")).toBeTruthy();
    expect(screen.queryByText("375.000 ₫ / buổi 90 phút")).toBeNull();
  });

  it("a tutor who is not a candidate of this run is not shown with the run's terms", async () => {
    api = server({ run: () => json(200, { ...run(), candidates: [] }) });
    await renderDetail();

    expect(await screen.findByText("Hồ sơ có thể đã ẩn hoặc thay đổi. Quay lại danh sách và tìm lại để có kết quả mới.")).toBeTruthy();
    expect(screen.queryByText(DESCRIPTION)).toBeNull();
  });

  it("choosing another child leaves the tutor, who was found for this child's need", async () => {
    api = server();
    const { onOtherChild } = await renderDetail();
    await screen.findByRole("header", { name: "Nguyễn Thị Lan" });

    await act(async () => fireEvent.press(screen.getByLabelText("Đổi con. Đang xem Minh An, lớp 8")));
    await act(async () => fireEvent.press(screen.getByRole("radio", { name: "Lan Chi. Lớp 8." })));
    expect(onOtherChild).toHaveBeenCalled();
  });
});
