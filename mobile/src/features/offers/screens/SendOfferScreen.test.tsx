import { act, fireEvent, screen } from "@testing-library/react-native";
import type { Candidate, MatchingRun } from "@/features/matching/api";
import type { Need } from "@/features/needs/api";
import { SelectedStudentProvider } from "@/features/students/selection";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import type { Offer } from "../api";
import { SendOfferScreen } from "./SendOfferScreen";

// Fake children, tutors and texts only (AGENTS.md).
const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "" };
const NEED_ID = "3d9e6b5a-4c73-4caf-9c65-8f3b2e4d1a32";
const VERSION = "4e0f7c6b-5d84-4dba-8d76-9a4c3f5e2b43";
const RUN_ID = "6a2b9e8d-7fa6-4fdc-8f98-1c6e5b7a4d65";
const TUTOR_ID = "7b3c0f9e-8ab7-40ed-9a09-2d7f6c8b5e01";
const GOAL = "Củng cố hằng đẳng thức và phân thức đại số trước kỳ thi học kỳ 1.";

const need: Need = {
  id: NEED_ID,
  studentId: MINH.id,
  revision: 2,
  versionId: VERSION,
  status: "CONFIRMED",
  draft: { goal: GOAL, durationMinutes: 90, frequencyPer7Days: 2, budgetVndSession: 400000, cycleStartDate: "2026-11-18" },
  studentCalendarId: null,
};

/** Thứ 4 and Thứ 2, 18:00–19:30 in Vietnam, from 18/11 to 14/12/2026 (the approved frame). */
const DAYS = ["2026-11-18", "2026-11-23", "2026-11-25", "2026-11-30", "2026-12-02", "2026-12-07", "2026-12-09", "2026-12-14"];
const SCHEDULE = DAYS.map((day) => ({
  start: new Date(`${day}T18:00:00+07:00`).toISOString(),
  end: new Date(`${day}T19:30:00+07:00`).toISOString(),
}));

const candidate: Candidate = {
  tutor: {
    tutorId: TUTOR_ID,
    profileVersionId: "8c4d1a0f-9bc8-41fe-8b1a-3e8a7d9c6f76",
    displayName: "Nguyễn Thị Lan",
    description: "Dạy đại số lớp 8 theo từng bước.",
    rateVnd60: 250000,
    experienceMonths: 24,
    experienceLabel: "SELF_DECLARED",
    verificationScopes: ["MATH8_ALGEBRA"],
  },
  rank: 1,
  priceVndSession: 375000,
  commonSchedule: SCHEDULE,
  reasons: [],
  tutorCalendarVersionId: "9d5e2b1a-0cd9-42af-9c2b-4f9b8e0d7a87",
};

function run(needVersionId = VERSION): MatchingRun {
  return {
    id: RUN_ID,
    needVersionId,
    mode: "B",
    status: "COMPLETED",
    expiresAt: "2026-11-28T05:00:00Z",
    candidates: [candidate],
    failureCode: null,
    studentCalendarVersionId: "0e6f3c2b-1dea-43b0-8d3c-5a0c9f1e8b98",
  };
}

const offer: Offer = {
  id: "1d2e3f4a-5b6c-4d7e-8f9a-0b1c2d3e4f5a",
  needId: NEED_ID,
  needVersionId: VERSION,
  revision: 1,
  state: "PENDING",
  tutorId: TUTOR_ID,
  goalSnapshot: GOAL,
  durationMinutes: 90,
  frequencyPer7Days: 2,
  cycleStartDate: "2026-11-18",
  rateVnd60: 250000,
  priceVndSession: 375000,
  schedule: SCHEDULE,
  sentAt: "2026-11-18T11:00:00Z",
  expiresAt: "2026-11-20T11:00:00Z",
  reasonCode: null,
  classId: null,
};

/** The need edited and confirmed again after the search: 120 minutes, a higher budget, a later cycle. */
const MOVED: Need = {
  ...need,
  revision: 4,
  versionId: "5f1a8d7c-6e95-4ecb-9e87-0b5d4a6f3c54",
  draft: { ...need.draft, durationMinutes: 120, budgetVndSession: 500000, cycleStartDate: "2026-12-02" },
};
let storedNeed: Need = need;
const needAnswer = () =>
  new Response(JSON.stringify(storedNeed), {
    status: 200,
    headers: { "Content-Type": "application/json", ETag: `"${storedNeed.revision}"` },
  });

function server(sendAnswer?: () => Response, runAnswer = () => json(200, run())) {
  storedNeed = need;
  return mockApi({
    "GET /matching-runs/{runId}": runAnswer,
    "GET /needs/{needId}": needAnswer,
    "GET /students": () => json(200, { items: [MINH], nextCursor: null }),
    ...(sendAnswer ? { "POST /needs/{needId}/offers": sendAnswer } : {}),
  });
}

const press = (name: string) => act(async () => fireEvent.press(screen.getByRole("button", { name })));
/** The sheet's "Gửi đề nghị", which sends; the bar's opens the sheet. */
const pressInSheet = (name: string) =>
  act(async () => {
    const buttons = screen.getAllByRole("button", { name });
    fireEvent.press(buttons[buttons.length - 1]);
  });
const sends = (api: ReturnType<typeof mockApi>) => api.requests.filter((r) => r.method === "POST");

async function renderSend() {
  const handlers = {
    onBack: jest.fn(),
    onFindAgain: jest.fn(),
    onEdit: jest.fn(),
    onViewOffers: jest.fn(),
    onOtherChild: jest.fn(),
    onManageStudents: jest.fn(),
  };
  const rendered = await renderScreen(
    <SelectedStudentProvider>
      <SendOfferScreen needId={NEED_ID} runId={RUN_ID} tutorId={TUTOR_ID} {...handlers} />
    </SelectedStudentProvider>,
    { session: fakeSession("PARENT") },
  );
  return { ...handlers, queryClient: rendered.queryClient };
}

async function sendOnce() {
  await screen.findByText(GOAL);
  await press("Gửi đề nghị");
  await pressInSheet("Gửi đề nghị");
}

describe("PH08 Xem lại và gửi đề nghị", () => {
  let api: ReturnType<typeof mockApi>;
  afterEach(() => api.restore());

  it("UC06 shows the terms to be sent: tutor, goal, price, the cycle and every proposed session", async () => {
    api = server();
    await renderSend();

    expect(await screen.findByText(GOAL)).toBeTruthy();
    expect(screen.getByRole("header", { name: "Xem lại đề nghị" })).toBeTruthy();
    for (const text of [
      "Gia sư",
      "Nguyễn Thị Lan",
      "Mục tiêu",
      "Học phí",
      "375.000 ₫ / buổi 90 phút",
      "Giá 250.000 ₫ / 60 phút",
      "8 buổi trong 28 ngày, 2 buổi mỗi tuần, từ 18/11 đến 15/12/2026",
      "Vòng nét đứt: buổi trong đề nghị, chưa chốt, chưa giữ chỗ.",
      "Đủ 8 buổi trong đề nghị",
      "Gia sư có 48 giờ để trả lời, hoặc ít hơn nếu buổi đầu bắt đầu sớm hơn. Đề nghị chưa giữ chỗ cho tới khi gia sư chấp nhận.",
    ]) {
      expect(screen.getByText(text)).toBeTruthy();
    }
    // Proposed, never looking booked (03): every session says so.
    expect(screen.getByLabelText("T4 18, 18:00–19:30. Trong đề nghị, chưa chốt")).toBeTruthy();
    expect(screen.getByLabelText("T2 14, 18:00–19:30. Trong đề nghị, chưa chốt")).toBeTruthy();
    expect(screen.getAllByText("Trong đề nghị, chưa chốt")).toHaveLength(8);
    expect(sends(api)).toHaveLength(0);
  });

  it("asks first, then sends the candidate unchanged against the need's ETag, and says so only after 201", async () => {
    api = server(() => json(201, offer));
    const { onViewOffers, onFindAgain } = await renderSend();
    await screen.findByText(GOAL);

    await press("Gửi đề nghị");
    expect(screen.getByRole("header", { name: "Gửi đề nghị cho Nguyễn Thị Lan?" })).toBeTruthy();
    expect(
      screen.getByText("Gửi đề nghị là bạn đồng ý các điều kiện trên. Gia sư chỉ có thể chấp nhận nguyên bản hoặc từ chối."),
    ).toBeTruthy();
    expect(sends(api)).toHaveLength(0);
    await pressInSheet("Gửi đề nghị");

    const [post] = sends(api);
    expect(new URL(post.url).pathname).toBe(`/api/v1/needs/${NEED_ID}/offers`);
    expect(post.headers.get("If-Match")).toBe('"2"');
    expect(post.headers.get("Idempotency-Key")).toBeTruthy();
    expect(await post.json()).toEqual({
      needVersionId: VERSION,
      matchingRunId: RUN_ID,
      tutorId: TUTOR_ID,
      profileVersionId: candidate.tutor.profileVersionId,
      tutorCalendarVersionId: candidate.tutorCalendarVersionId,
      studentCalendarVersionId: run().studentCalendarVersionId,
      schedule: SCHEDULE,
      acceptUnchangedTerms: true,
    });

    expect(await screen.findByText("Đã gửi đề nghị cho Nguyễn Thị Lan.")).toBeTruthy();
    expect(screen.getByRole("header", { name: "Đề nghị đã gửi" })).toBeTruthy();
    expect(screen.getByText("Hết hạn lúc 18:00 20/11/2026 nếu gia sư chưa trả lời.")).toBeTruthy();
    expect(
      screen.getByText(
        "Bạn sẽ nhận thông báo khi gia sư trả lời. Trong lúc chờ, bạn vẫn có thể gửi đề nghị cho gia sư khác từ cùng nhu cầu; lớp chỉ được chốt với một gia sư.",
      ),
    ).toBeTruthy();
    await press("Xem đề nghị");
    expect(onViewOffers).toHaveBeenCalled();
    await press("Về danh sách gia sư");
    expect(onFindAgain).toHaveBeenCalled();
  });

  it.each(["OFFER_TERMS_CHANGED", "SCHEDULE_CONFLICT", "MATCHING_RUN_EXPIRED"])(
    "%s: the tutor's price or calendar changed, so search again or edit the need, never send these terms",
    async (code) => {
      api = server(() => problem(409, code, "Điều kiện đã thay đổi."));
      const { onFindAgain, onEdit } = await renderSend();
      await sendOnce();

      expect(await screen.findByText("Giá hoặc lịch của gia sư đã thay đổi. Hãy xem lại trước khi gửi.")).toBeTruthy();
      expect(screen.queryByRole("button", { name: "Gửi đề nghị" })).toBeNull();
      await press("Tìm lại gia sư");
      expect(onFindAgain).toHaveBeenCalled();
      await press("Sửa nhu cầu");
      expect(onEdit).toHaveBeenCalled();
    },
  );

  it.each([
    ["412", () => problem(412, "VERSION_CONFLICT", "Dữ liệu đã thay đổi.")],
    ["NEED_VERSION_CHANGED", () => problem(409, "NEED_VERSION_CHANGED", "Nhu cầu đã có bản mới.")],
  ])("%s: the need moved on, so the offer cannot be sent from this search", async (_case, answer) => {
    api = server(answer);
    const { onFindAgain } = await renderSend();
    await sendOnce();

    expect(
      await screen.findByText(
        "Nhu cầu đã có bản mới từ lúc bạn tìm. Đề nghị này dựa trên bản cũ nên không gửi được. Tìm lại từ bản đang hiệu lực.",
      ),
    ).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Gửi đề nghị" })).toBeNull();
    await press("Tìm lại gia sư");
    expect(onFindAgain).toHaveBeenCalled();
  });

  it("a run of an older version of the need cannot be sent at all", async () => {
    api = server(undefined, () => json(200, run("5f1a8d7c-6e95-4ecb-9e87-0b5d4a6f3c54")));
    await renderSend();

    expect(
      await screen.findByText(
        "Nhu cầu đã có bản mới từ lúc bạn tìm. Đề nghị này dựa trên bản cũ nên không gửi được. Tìm lại từ bản đang hiệu lực.",
      ),
    ).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Gửi đề nghị" })).toBeNull();
  });

  it("an unknown outcome blocks a second send; 'Thử lại' resends the same key and gets the first offer (INV-20)", async () => {
    let calls = 0;
    api = server(() => (++calls === 1 ? problem(503, "UNAVAILABLE", "Dịch vụ tạm gián đoạn.") : json(201, offer)));
    await renderSend();
    await sendOnce();

    expect(
      await screen.findByText("Chưa rõ kết quả. Yêu cầu có thể đã được ghi nhận. Thử lại sẽ không tạo bản trùng."),
    ).toBeTruthy();
    expect(screen.getByRole("button", { name: "Gửi đề nghị" })).toBeDisabled();
    await press("Thử lại");

    expect(await screen.findByText("Đã gửi đề nghị cho Nguyễn Thị Lan.")).toBeTruthy();
    const [first, second] = sends(api);
    expect(second.headers.get("Idempotency-Key")).toBe(first.headers.get("Idempotency-Key"));
  });

  it("an unknown outcome keeps 'Thử lại' with the first key and terms even after the need moves on (Codex, #276)", async () => {
    let calls = 0;
    api = server(() => (++calls === 1 ? problem(503, "UNAVAILABLE", "Dịch vụ tạm gián đoạn.") : json(201, offer)));
    const { queryClient } = await renderSend();
    await sendOnce();
    expect(await screen.findByRole("button", { name: "Thử lại" })).toBeTruthy();

    // The need is read again (as on focus) and has moved on while the first send's outcome is unknown.
    storedNeed = MOVED;
    await act(async () => {
      await queryClient.refetchQueries({ queryKey: ["needs", "one", NEED_ID] });
    });
    expect(api.requests.filter((r) => r.method === "GET" && new URL(r.url).pathname.endsWith(`/needs/${NEED_ID}`)).length).toBeGreaterThan(1);
    expect(queryClient.getQueryData<{ data: Need }>(["needs", "one", NEED_ID])?.data.versionId).toBe(MOVED.versionId);
    // TanStack Query tells observers on the next tick: let the screen draw the new version.
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(screen.getByText("Chưa rõ kết quả. Yêu cầu có thể đã được ghi nhận. Thử lại sẽ không tạo bản trùng.")).toBeTruthy();
    expect(screen.queryByText(/Nhu cầu đã có bản mới/)).toBeNull();
    // The terms on screen stay those of the send in doubt, not the new version's.
    expect(screen.getByText("375.000 ₫ / buổi 90 phút")).toBeTruthy();
    expect(screen.queryByText(/buổi 120 phút/)).toBeNull();
    await press("Thử lại");

    expect(await screen.findByText("Đã gửi đề nghị cho Nguyễn Thị Lan.")).toBeTruthy();
    const [first, second] = sends(api);
    expect(second.headers.get("Idempotency-Key")).toBe(first.headers.get("Idempotency-Key"));
    expect(second.headers.get("If-Match")).toBe('"2"');
    expect(await second.json()).toMatchObject({ needVersionId: VERSION });
  });

  it("a run of an older version shows none of its terms beside the new version's (Codex, #276)", async () => {
    api = server(undefined, () => json(200, run()));
    storedNeed = MOVED;
    await renderSend();

    expect(
      await screen.findByText(
        "Nhu cầu đã có bản mới từ lúc bạn tìm. Đề nghị này dựa trên bản cũ nên không gửi được. Tìm lại từ bản đang hiệu lực.",
      ),
    ).toBeTruthy();
    expect(screen.queryByText(/375.000 ₫ \/ buổi/)).toBeNull();
    expect(screen.queryByText(/buổi 120 phút/)).toBeNull();
    expect(screen.queryByText(/buổi trong 28 ngày/)).toBeNull();
    expect(screen.queryAllByText("Trong đề nghị, chưa chốt")).toHaveLength(0);
  });

  it("another refusal shows the server's sentence and keeps the terms", async () => {
    api = server(() => problem(409, "OFFER_ALREADY_PENDING", "Gia sư này đang có một đề nghị chờ trả lời cho nhu cầu này."));
    await renderSend();
    await sendOnce();

    expect(await screen.findByText("Gia sư này đang có một đề nghị chờ trả lời cho nhu cầu này.")).toBeTruthy();
    expect(screen.getByText("375.000 ₫ / buổi 90 phút")).toBeTruthy();
  });
});
