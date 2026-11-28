import { act, fireEvent, screen, waitFor } from "@testing-library/react-native";
import type { Need } from "@/features/needs/api";
import { SelectedStudentProvider } from "@/features/students/selection";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import type { Candidate, MatchingRun } from "../api";
import { MatchesScreen } from "./MatchesScreen";

// Fake children, tutors and texts only (AGENTS.md).
const NOW = new Date("2026-11-28T03:00:00Z");
const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "" };
const LAN = { id: "1c8d5a4f-3b62-4b9f-8b54-7e2a1d3c0f21", displayName: "Lan Chi", grade: 8, revision: 1, shortGoal: "" };
const NEED_ID = "3d9e6b5a-4c73-4caf-9c65-8f3b2e4d1a32";
const VERSION = "4e0f7c6b-5d84-4dba-8d76-9a4c3f5e2b43";
const NEW_VERSION = "5f1a8d7c-6e95-4ecb-9e87-0b5d4a6f3c54";
const RUN_ID = "6a2b9e8d-7fa6-4fdc-8f98-1c6e5b7a4d65";
const GOAL = "Toán 8: hằng đẳng thức và phân thức";
const DRAFT = { goal: GOAL, durationMinutes: 90, frequencyPer7Days: 2, budgetVndSession: 400000, cycleStartDate: "2026-11-30" } as const;

function need(status: Need["status"] = "CONFIRMED", versionId = VERSION, revision = 2, draft: Need["draft"] = DRAFT): Need {
  return { id: NEED_ID, studentId: MINH.id, revision, versionId, status, draft, studentCalendarId: null };
}

/** The need edited and confirmed again after the search: 120 minutes and a higher budget. */
const LONGER = { ...DRAFT, durationMinutes: 120, budgetVndSession: 500000 } as const;

function withEtag(body: Need) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json", ETag: `"${body.revision}"` },
  });
}

/** Thứ 2 and Thứ 4, 18:00–19:30 in Vietnam, for four weeks from 30/11/2026. */
const SCHEDULE = [0, 7, 14, 21].flatMap((offset) =>
  [30, 32].map((day) => ({
    start: new Date(Date.UTC(2026, 10, day + offset, 11, 0)).toISOString(),
    end: new Date(Date.UTC(2026, 10, day + offset, 12, 30)).toISOString(),
  })),
);

function candidate(rank: number, name: string, reasons: string[]): Candidate {
  return {
    tutor: {
      tutorId: `7b3c0f9e-8ab7-40ed-9a09-2d7f6c8b5e${String(rank).padStart(2, "0")}`,
      profileVersionId: "8c4d1a0f-9bc8-41fe-8b1a-3e8a7d9c6f76",
      displayName: name,
      description: "Dạy đại số lớp 8 theo từng bước, có bài tập về nhà mỗi buổi học.",
      rateVnd60: 250000,
      experienceMonths: 24,
      experienceLabel: "SELF_DECLARED",
      verificationScopes: ["MATH8_ALGEBRA"],
    },
    rank,
    priceVndSession: 375000,
    commonSchedule: SCHEDULE,
    reasons,
    tutorCalendarVersionId: "9d5e2b1a-0cd9-42af-9c2b-4f9b8e0d7a87",
  };
}

const REASONS = [
  "Giá 375.000 ₫ một buổi 90 phút (250.000 ₫/60 phút), trong ngân sách 400.000 ₫/buổi.",
  "Kinh nghiệm 24 tháng (tự khai).",
  "Đã xác minh: Toán 8 đại số.",
  "Khớp 2/2 khung giờ ưu tiên.",
];

function run(candidates: Candidate[], extra: Partial<MatchingRun> = {}): MatchingRun {
  return {
    id: RUN_ID,
    needVersionId: VERSION,
    mode: "B",
    status: "COMPLETED",
    expiresAt: "2026-11-28T05:00:00Z",
    candidates,
    failureCode: null,
    studentCalendarVersionId: "0e6f3c2b-1dea-43b0-8d3c-5a0c9f1e8b98",
    ...extra,
  };
}

const students = () => json(200, { items: [MINH, LAN], nextCursor: null });
const press = (name: string) => act(async () => fireEvent.press(screen.getByRole("button", { name })));
const runs = (server: ReturnType<typeof mockApi>) => server.requests.filter((r) => r.method === "POST");

async function renderMatches(now: () => Date = () => NOW) {
  const handlers = { onBack: jest.fn(), onEdit: jest.fn(), onOpenTutor: jest.fn(), onOtherChild: jest.fn(), onManageStudents: jest.fn() };
  const rendered = await renderScreen(
    <SelectedStudentProvider>
      <MatchesScreen needId={NEED_ID} {...handlers} now={now} />
    </SelectedStudentProvider>,
    { session: fakeSession("PARENT") },
  );
  return { ...handlers, queryClient: rendered.queryClient };
}

describe("PH06 Kết quả và so sánh gia sư", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("says how B filters before searching, and searches only when the parent chooses it (BR03)", async () => {
    server = mockApi({ "GET /needs/{needId}": () => withEtag(need()), "GET /students": students });
    await renderMatches();

    expect(await screen.findByText(`Tìm gia sư cho nhu cầu "${GOAL}"`)).toBeTruthy();
    expect(screen.getByRole("header", { name: "Gia sư phù hợp" })).toBeTruthy();
    expect(screen.getByLabelText("Đổi con. Đang xem Minh An, lớp 8")).toBeTruthy();
    expect(
      screen.getByText(
        "Hiện chưa dùng xếp hạng AI. Bạn sẽ thấy tối đa 5 gia sư đáp ứng đủ điều kiện: đúng phạm vi, giá một buổi trong ngân sách, rảnh đủ cả chu kỳ 28 ngày và hồ sơ đã được xác minh.",
      ),
    ).toBeTruthy();
    expect(
      screen.getByText("Danh sách xếp theo kinh nghiệm tự khai và mức khớp giờ ưu tiên. Thứ tự không phải là đánh giá chất lượng giảng dạy."),
    ).toBeTruthy();
    expect(runs(server)).toHaveLength(0);
  });

  it("FR-09 runs B for the version on screen with its ETag and shows each tutor as the server ranked them", async () => {
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(need()),
      "GET /students": students,
      "POST /needs/{needId}/matching-runs": () =>
        json(202, run([candidate(1, "Nguyễn Thị Lan", REASONS), candidate(2, "Trần Văn Minh", REASONS.slice(0, 2))])),
    });
    const { onOpenTutor } = await renderMatches();
    await screen.findByText(`Tìm gia sư cho nhu cầu "${GOAL}"`);

    await press("Xem danh sách lọc theo điều kiện");

    const [post] = runs(server);
    expect(new URL(post.url).pathname).toBe(`/api/v1/needs/${NEED_ID}/matching-runs`);
    expect(post.headers.get("If-Match")).toBe('"2"');
    expect(post.headers.get("Idempotency-Key")).toBeTruthy();
    expect(await post.json()).toEqual({ mode: "B", needVersionId: VERSION, fallbackExplicitlyChosen: true });

    expect(
      await screen.findByText(
        "Danh sách lọc theo điều kiện của bạn, không dùng xếp hạng AI. Xếp theo kinh nghiệm tự khai và mức khớp giờ ưu tiên.",
      ),
    ).toBeTruthy();
    expect(screen.getByText("Gợi ý chưa giữ chỗ. Lớp chỉ được chốt khi gia sư chấp nhận đề nghị.")).toBeTruthy();
    expect(screen.getByText("Nguyễn Thị Lan")).toBeTruthy();
    expect(screen.getByText("Trần Văn Minh")).toBeTruthy();
    expect(screen.getAllByText("Đã xác minh: Toán 8, đại số")).toHaveLength(2);
    expect(screen.getAllByText("Kinh nghiệm 24 tháng (tự khai)")).toHaveLength(2);
    expect(screen.getAllByText("375.000 ₫ / buổi 90 phút")).toHaveLength(2);
    expect(screen.getAllByText("Giá 250.000 ₫ / 60 phút")).toHaveLength(2);
    expect(screen.getAllByText("Giờ chung: đủ 8 buổi, Thứ 2 và Thứ 4, 18:00–19:30")).toHaveLength(2);
    // Three reasons, then the count of the rest (PH06.md, Hành vi); never a score or a percentage.
    expect(screen.getByText(`• ${REASONS[2]}`)).toBeTruthy();
    expect(screen.queryByText(`• ${REASONS[3]}`)).toBeNull();
    expect(screen.getByText("và 1 lý do khác")).toBeTruthy();
    expect(screen.queryByText(/%/)).toBeNull();
    // A result holds nothing, so there is no action on the whole list (BR01).
    expect(screen.queryByRole("button", { name: "Tìm lại" })).toBeNull();

    await press("Xem chi tiết Nguyễn Thị Lan");
    expect(onOpenTutor).toHaveBeenCalledWith(candidate(1, "", []).tutor.tutorId, RUN_ID);
  });

  it("no tutor fits: the four checked conditions from the need, and a way to change it", async () => {
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(need()),
      "GET /students": students,
      "POST /needs/{needId}/matching-runs": () => json(202, run([])),
    });
    const { onEdit, onBack } = await renderMatches();
    await screen.findByText(`Tìm gia sư cho nhu cầu "${GOAL}"`);
    await press("Xem danh sách lọc theo điều kiện");

    expect(await screen.findByText("Chưa có gia sư đáp ứng đủ điều kiện.")).toBeTruthy();
    for (const text of [
      "Đã kiểm các điều kiện sau với mọi gia sư đang nhận lớp:",
      "• Phạm vi Toán 8, đại số, học trực tuyến",
      "• Giá một buổi 90 phút không vượt 400.000 ₫",
      "• Rảnh đủ 2 buổi mỗi tuần trong cả 28 ngày, trùng giờ rảnh của con",
      "• Hồ sơ đã được xác minh",
      "Bạn có thể nới ngân sách, thêm giờ rảnh hoặc giảm số buổi. Sửa sẽ tạo bản nhu cầu mới.",
    ]) {
      expect(screen.getByText(text)).toBeTruthy();
    }
    await press("Sửa nhu cầu");
    expect(onEdit).toHaveBeenCalledWith(NEED_ID);
    await press("Tìm lại sau");
    expect(onBack).toHaveBeenCalled();
  });

  it.each([
    ["a run the server could not rank", () => json(202, run([], { status: "FAILED", failureCode: "AI_RANKING_UNAVAILABLE" }))],
    ["a refusal", () => problem(404, "NEED_NOT_FOUND", "Không tìm thấy nhu cầu học.")],
  ])("%s: one sentence that keeps the need, and a new search on 'Thử lại' (#71, Đính)", async (_case, answer) => {
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(need()),
      "GET /students": students,
      "POST /needs/{needId}/matching-runs": answer,
    });
    await renderMatches();
    await screen.findByText(`Tìm gia sư cho nhu cầu "${GOAL}"`);
    await press("Xem danh sách lọc theo điều kiện");

    expect(await screen.findByText("Không tìm được lúc này. Nhu cầu của bạn vẫn được giữ.")).toBeTruthy();
    await press("Thử lại");
    const [first, second] = runs(server);
    expect(second).toBeTruthy();
    // The first answer was known, so the new search is a new command (INV-20).
    expect(second.headers.get("Idempotency-Key")).not.toBe(first.headers.get("Idempotency-Key"));
  });

  it("an unknown outcome retries with the same key, so the server answers the first run", async () => {
    let calls = 0;
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(need()),
      "GET /students": students,
      "POST /needs/{needId}/matching-runs": () => (++calls === 1 ? problem(503, "UNAVAILABLE", "Dịch vụ tạm gián đoạn.") : json(202, run([candidate(1, "Nguyễn Thị Lan", REASONS)]))),
    });
    await renderMatches();
    await screen.findByText(`Tìm gia sư cho nhu cầu "${GOAL}"`);
    await press("Xem danh sách lọc theo điều kiện");

    expect(await screen.findByRole("button", { name: "Thử lại" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Xem danh sách lọc theo điều kiện" })).toBeDisabled();
    await press("Thử lại");

    expect(await screen.findByText("Nguyễn Thị Lan")).toBeTruthy();
    const [first, second] = runs(server);
    expect(second.headers.get("Idempotency-Key")).toBe(first.headers.get("Idempotency-Key"));
  });

  it("a need changed since it loaded (412) is reloaded, never searched as it was (INV-23)", async () => {
    let stored = need();
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(stored),
      "GET /students": students,
      "POST /needs/{needId}/matching-runs": () => problem(412, "VERSION_CONFLICT", "Dữ liệu đã thay đổi."),
    });
    await renderMatches();
    await screen.findByText(`Tìm gia sư cho nhu cầu "${GOAL}"`);
    await press("Xem danh sách lọc theo điều kiện");

    expect(
      await screen.findByText("Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."),
    ).toBeTruthy();
    expect(screen.getByRole("button", { name: "Xem danh sách lọc theo điều kiện" })).toBeDisabled();

    stored = need("CONFIRMED", NEW_VERSION, 4);
    await press("Tải lại");
    await waitFor(() => expect(screen.queryByText(/Dữ liệu đã thay đổi/)).toBeNull());
    await press("Xem danh sách lọc theo điều kiện");
    const second = runs(server)[1];
    expect(second.headers.get("If-Match")).toBe('"4"');
    expect(await second.json()).toMatchObject({ needVersionId: NEW_VERSION });
  });

  it("a result past expiresAt turns stale on time and searches again", async () => {
    let clock = NOW;
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(need()),
      "GET /students": students,
      "POST /needs/{needId}/matching-runs": () => json(202, run([candidate(1, "Nguyễn Thị Lan", REASONS)], { expiresAt: "2026-11-28T03:00:01Z" })),
    });
    jest.useFakeTimers({ now: NOW, doNotFake: ["nextTick", "setImmediate", "queueMicrotask"] });
    try {
      await renderMatches(() => clock);
      await screen.findByText(`Tìm gia sư cho nhu cầu "${GOAL}"`);
      await press("Xem danh sách lọc theo điều kiện");
      expect(await screen.findByText("Nguyễn Thị Lan")).toBeTruthy();
      expect(screen.queryByText(/Kết quả này đã cũ/)).toBeNull();

      clock = new Date("2026-11-28T03:00:02Z");
      await act(async () => {
        jest.advanceTimersByTime(1500);
      });
      expect(screen.getByText("Kết quả này đã cũ. Tìm lại để có danh sách theo giá và lịch hiện tại.")).toBeTruthy();
      expect(screen.getByText("Nguyễn Thị Lan")).toBeTruthy();
      await press("Tìm lại");
      expect(runs(server)).toHaveLength(2);
    } finally {
      jest.useRealTimers();
    }
  });

  it("results of a version the need has moved past are kept for reference, with a search from the current one", async () => {
    // The run answers for VERSION while the need on screen is NEW_VERSION: what the screen shows once the need
    // was edited and confirmed again after the search.
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(need("CONFIRMED", NEW_VERSION, 4)),
      "GET /students": students,
      "POST /needs/{needId}/matching-runs": () => json(202, run([candidate(1, "Nguyễn Thị Lan", REASONS)])),
    });
    await renderMatches();
    await screen.findByText(`Tìm gia sư cho nhu cầu "${GOAL}"`);
    await press("Xem danh sách lọc theo điều kiện");

    expect(
      await screen.findByText(
        "Đây là kết quả của bản nhu cầu cũ, chỉ để đối chiếu. Tìm lại từ bản đang hiệu lực để gửi đề nghị.",
      ),
    ).toBeTruthy();
    expect(screen.getByText("Nguyễn Thị Lan")).toBeTruthy();
    await press("Tìm từ bản mới");
    const second = runs(server)[1];
    expect(second.headers.get("If-Match")).toBe('"4"');
    expect(await second.json()).toMatchObject({ needVersionId: NEW_VERSION });
  });

  it("results kept after the need moves on show the price with the duration they were searched for (Codex, #274)", async () => {
    let stored = need();
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(stored),
      "GET /students": students,
      "POST /needs/{needId}/matching-runs": () => json(202, run([candidate(1, "Nguyễn Thị Lan", REASONS)])),
    });
    const { queryClient } = await renderMatches();
    await screen.findByText(`Tìm gia sư cho nhu cầu "${GOAL}"`);
    await press("Xem danh sách lọc theo điều kiện");
    await screen.findByText("Nguyễn Thị Lan");

    // The need is read again, as on focus, while the results stay on screen.
    stored = need("CONFIRMED", NEW_VERSION, 4, LONGER);
    await act(async () => {
      await queryClient.refetchQueries();
    });

    expect(
      await screen.findByText("Đây là kết quả của bản nhu cầu cũ, chỉ để đối chiếu. Tìm lại từ bản đang hiệu lực để gửi đề nghị."),
    ).toBeTruthy();
    expect(screen.getByText("375.000 ₫ / buổi 90 phút")).toBeTruthy();
    expect(screen.queryByText(/buổi 120 phút/)).toBeNull();
  });

  it("an empty result past its expiry is stale too, with 'Tìm lại' (Codex, #274)", async () => {
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(need()),
      "GET /students": students,
      "POST /needs/{needId}/matching-runs": () => json(202, run([], { expiresAt: "2026-11-28T02:00:00Z" })),
    });
    await renderMatches();
    await screen.findByText(`Tìm gia sư cho nhu cầu "${GOAL}"`);
    await press("Xem danh sách lọc theo điều kiện");

    expect(await screen.findByText("Kết quả này đã cũ. Tìm lại để có danh sách theo giá và lịch hiện tại.")).toBeTruthy();
    expect(screen.queryByText("Chưa có gia sư đáp ứng đủ điều kiện.")).toBeNull();
    await press("Tìm lại");
    expect(runs(server)).toHaveLength(2);
  });

  it("an empty result of an older version names the conditions of that version, not the new one (Codex, #274)", async () => {
    let stored = need();
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(stored),
      "GET /students": students,
      "POST /needs/{needId}/matching-runs": () => json(202, run([])),
    });
    const { queryClient } = await renderMatches();
    await screen.findByText(`Tìm gia sư cho nhu cầu "${GOAL}"`);
    await press("Xem danh sách lọc theo điều kiện");
    await screen.findByText("Chưa có gia sư đáp ứng đủ điều kiện.");

    stored = need("CONFIRMED", NEW_VERSION, 4, LONGER);
    await act(async () => {
      await queryClient.refetchQueries();
    });

    expect(
      await screen.findByText("Đây là kết quả của bản nhu cầu cũ, chỉ để đối chiếu. Tìm lại từ bản đang hiệu lực để gửi đề nghị."),
    ).toBeTruthy();
    expect(screen.getByText("• Giá một buổi 90 phút không vượt 400.000 ₫")).toBeTruthy();
    await press("Tìm từ bản mới");
    const second = runs(server)[1];
    expect(await second.json()).toMatchObject({ needVersionId: NEW_VERSION });
  });

  it("a draft need cannot be searched", async () => {
    server = mockApi({ "GET /needs/{needId}": () => withEtag(need("DRAFT", VERSION, 1)), "GET /students": students });
    await renderMatches();

    expect(await screen.findByText("Chưa thể tìm gia sư từ bản nháp.")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Xem danh sách lọc theo điều kiện" })).toBeNull();
  });

  it("choosing another child leaves the need, which is this child's", async () => {
    server = mockApi({ "GET /needs/{needId}": () => withEtag(need()), "GET /students": students });
    const { onOtherChild } = await renderMatches();
    await screen.findByText(`Tìm gia sư cho nhu cầu "${GOAL}"`);

    await press("Đổi con. Đang xem Minh An, lớp 8");
    await act(async () => fireEvent.press(screen.getByRole("radio", { name: "Lan Chi. Lớp 8." })));
    expect(onOtherChild).toHaveBeenCalled();
  });
});
