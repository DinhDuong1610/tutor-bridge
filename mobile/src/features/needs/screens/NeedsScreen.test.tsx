import { act, fireEvent, screen } from "@testing-library/react-native";
import { SelectedStudentProvider } from "@/features/students/selection";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import type { Need, Offer } from "../summary";
import { NeedsScreen } from "./NeedsScreen";

// PH03 (UC04, FR-04). Fake children, tutors and goals only (AGENTS.md).
const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "" };
const NGOC = { id: "1c8d5a4f-3b62-4b9f-8b54-7e2a1d3c0f21", displayName: "Bảo Ngọc", grade: 8, revision: 1, shortGoal: "" };
const TUTOR = "2d9e6b50-4c73-4caf-9c65-8f3b2e4d1032";
const CLASS = "3e0f7c61-5d84-4dba-8d76-903c3f5e2143";

const need = (id: string, status: Need["status"], draft: Need["draft"], studentId = MINH.id): Need => ({
  id,
  studentId,
  revision: 1,
  versionId: id.replace(/^./, "9"),
  status,
  draft,
  studentCalendarId: null,
});

const CONFIRMED = need("4f108d72-6e95-4ecb-9e87-a14d406f3254", "CONFIRMED", {
  goal: "hằng đẳng thức và phân thức",
  budgetVndSession: 400000,
  durationMinutes: 90,
  frequencyPer7Days: 2,
  cycleStartDate: "2026-11-18",
});
const DRAFT = need("50219e83-7fa6-4fdc-8f98-b25e5170436a", "DRAFT", { goal: "ôn thi học kỳ 2", durationMinutes: 90, frequencyPer7Days: 2, cycleStartDate: "2026-11-30" });
const CLOSED = need("61320f94-80b7-4aed-90a9-c36f6281547b", "CLOSED", { goal: "nhân chia đa thức", budgetVndSession: 350000, durationMinutes: 60, frequencyPer7Days: 1, cycleStartDate: "2026-09-28" });

const offer = (id: string, needId: string, state: Offer["state"], classId: string | null = null) =>
  ({ id, needId, state, tutorId: TUTOR, classId }) as Offer;

const page = (items: unknown[], nextCursor: string | null = null) => json(200, { items, nextCursor });
const press = (name: string) => act(async () => fireEvent.press(screen.getByRole("button", { name })));
const paths = (server: ReturnType<typeof mockApi>) => server.requests.map((r) => new URL(r.url).pathname.replace(/^\/api\/v1/, ""));

describe("PH03 Nhu cầu của con", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  async function renderNeeds() {
    const handlers = { onAddStudent: jest.fn(), onManageStudents: jest.fn(), onCreate: jest.fn(), onAction: jest.fn() };
    await renderScreen(
      <SelectedStudentProvider>
        <NeedsScreen {...handlers} />
      </SelectedStudentProvider>,
      { session: fakeSession("PARENT") },
    );
    return handlers;
  }

  it("lists the child's needs with their status in words, the terms of a confirmed one and its pending offers", async () => {
    server = mockApi({
      "GET /students": () => page([MINH]),
      "GET /students/{studentId}/needs": () => page([CONFIRMED]),
      "GET /me/offers": () =>
        page([
          offer("72431fa5-91c8-4bfe-a1ba-d47073925680", CONFIRMED.id, "PENDING"),
          offer("83542fb6-a2d9-4cf0-b2cb-e58184a36791", CONFIRMED.id, "REJECTED"),
        ]),
    });
    await renderNeeds();

    expect(await screen.findByText("Toán 8: hằng đẳng thức và phân thức")).toBeOnTheScreen();
    expect(screen.getByRole("header", { name: "Tìm gia sư" })).toBeOnTheScreen();
    expect(screen.getByText("Mỗi nhu cầu là một lần tìm gia sư. Sửa nhu cầu đã xác nhận sẽ tạo bản nháp mới; lớp đã chốt không đổi.")).toBeOnTheScreen();
    expect(screen.getByText("Đã xác nhận")).toBeOnTheScreen();
    expect(
      await screen.findByText("90 phút, 2 buổi mỗi tuần, tối đa 400.000 ₫ / buổi. Bắt đầu 18/11/2026. Có 1 đề nghị đang chờ."),
    ).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Tìm gia sư" })).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Xem đề nghị" })).toBeOnTheScreen();
    // The child already has an open need (#16 Q4), so no new one can be created.
    expect(screen.queryByRole("button", { name: "Tạo nhu cầu mới" })).toBeNull();

    const needs = server.requests.find((r) => r.url.includes("/needs"));
    const url = new URL(needs!.url);
    expect(url.pathname).toBe(`/api/v1/students/${MINH.id}/needs`);
    expect(url.searchParams.get("limit")).toBe("50");
    expect(url.searchParams.has("cursor")).toBe(false);
    expect(server.unexpected).toEqual([]);
  });

  it("a draft only continues; a closed need is for reference, with its class and tutor, never search or offers", async () => {
    server = mockApi({
      "GET /students": () => page([MINH]),
      "GET /students/{studentId}/needs": () => page([DRAFT, CLOSED]),
      "GET /me/offers": () => page([offer("94653fc7-b3ea-4d01-83dc-f69295b47802", CLOSED.id, "ACCEPTED", CLASS)]),
      "GET /tutors/{tutorId}/public-profile": () =>
        json(200, { tutorId: TUTOR, profileVersionId: CLASS, displayName: "Nguyễn Thị Lan", description: "", rateVnd60: 200000, experienceMonths: 24, experienceLabel: "SELF_DECLARED", verificationScopes: [] }),
    });
    const { onAction } = await renderNeeds();

    expect(await screen.findByText("Toán 8: ôn thi học kỳ 2")).toBeOnTheScreen();
    expect(screen.getByText("Nháp")).toBeOnTheScreen();
    expect(screen.getByText("Cần thêm: ngân sách. Chưa thể tìm gia sư từ bản nháp.")).toBeOnTheScreen();
    expect(screen.getByText("Đã đóng")).toBeOnTheScreen();
    expect(await screen.findByText("Đã chốt lớp với Nguyễn Thị Lan. Bản này chỉ để đối chiếu.")).toBeOnTheScreen();
    expect(screen.queryByRole("button", { name: "Tìm gia sư" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Xem đề nghị" })).toBeNull();
    // One name, for the closed need on screen.
    expect(paths(server).filter((path) => path.startsWith("/tutors/"))).toEqual([`/tutors/${TUTOR}/public-profile`]);

    await press("Tiếp tục nhập");
    await press("Xem lớp");
    expect(onAction.mock.calls).toEqual([
      [{ kind: "edit", title: "Tiếp tục nhập", needId: DRAFT.id }],
      [{ kind: "class", title: "Xem lớp", classId: CLASS }],
    ]);
  });

  it("reads no tutor name for accepted offers of needs not on screen", async () => {
    // The first page of offers spans every child; another child's class must not cost a request here.
    server = mockApi({
      "GET /students": () => page([MINH]),
      "GET /students/{studentId}/needs": () => page([CONFIRMED]),
      "GET /me/offers": () => page([offer("b6875fe9-d50c-4f23-a5fe-18b4b7d69a24", "c7986ffa-e61d-4034-b60f-29c5c8e7ab35", "ACCEPTED", CLASS)]),
    });
    await renderNeeds();
    expect(await screen.findByText("90 phút, 2 buổi mỗi tuần, tối đa 400.000 ₫ / buổi. Bắt đầu 18/11/2026.")).toBeOnTheScreen();

    expect(paths(server).some((path) => path.startsWith("/tutors/"))).toBe(false);
    expect(server.unexpected).toEqual([]);
  });

  it("opens search and offers of a confirmed need", async () => {
    server = mockApi({
      "GET /students": () => page([MINH]),
      "GET /students/{studentId}/needs": () => page([CONFIRMED]),
      "GET /me/offers": () => page([]),
    });
    const { onAction } = await renderNeeds();
    await screen.findByText("Toán 8: hằng đẳng thức và phân thức");

    await press("Tìm gia sư");
    await press("Xem đề nghị");

    expect(onAction.mock.calls.map(([action]) => [action.kind, action.needId])).toEqual([
      ["matches", CONFIRMED.id],
      ["offers", CONFIRMED.id],
    ]);
  });

  it("offers a new need once every need is closed", async () => {
    server = mockApi({
      "GET /students": () => page([MINH]),
      "GET /students/{studentId}/needs": () => page([CLOSED]),
      "GET /me/offers": () => page([]),
    });
    const { onCreate } = await renderNeeds();
    await screen.findByText("Toán 8: nhân chia đa thức");

    await press("Tạo nhu cầu mới");

    expect(onCreate).toHaveBeenCalledTimes(1);
  });

  it("shows the empty state named after the child, with the create button", async () => {
    server = mockApi({
      "GET /students": () => page([NGOC]),
      "GET /students/{studentId}/needs": () => page([]),
      "GET /me/offers": () => page([]),
    });
    const { onCreate } = await renderNeeds();

    expect(await screen.findByText("Bảo Ngọc chưa có nhu cầu học kèm. Tạo nhu cầu để bắt đầu tìm gia sư.")).toBeOnTheScreen();
    await press("Tạo nhu cầu học kèm");
    expect(onCreate).toHaveBeenCalledTimes(1);
  });

  it("AC-UC02-01 changing the child loads that child's needs only, never mixed with the other's", async () => {
    const NGOC_NEED = need("a5764fd8-c4fb-4e12-94ed-07a3a6c58913", "DRAFT", { goal: "phân tích đa thức thành nhân tử" }, NGOC.id);
    server = mockApi({
      "GET /students": () => page([MINH, NGOC]),
      "GET /students/{studentId}/needs": (request) => page(request.url.includes(NGOC.id) ? [NGOC_NEED] : [CONFIRMED]),
      "GET /me/offers": () => page([]),
    });
    await renderNeeds();
    await screen.findByText("Toán 8: hằng đẳng thức và phân thức");

    await press("Đổi con. Đang xem Minh An, lớp 8");
    await act(async () => fireEvent.press(screen.getByRole("radio", { name: "Bảo Ngọc. Lớp 8." })));

    expect(await screen.findByText("Toán 8: phân tích đa thức thành nhân tử")).toBeOnTheScreen();
    expect(screen.queryByText("Toán 8: hằng đẳng thức và phân thức")).toBeNull();
    expect(paths(server).filter((path) => path.endsWith("/needs"))).toEqual([`/students/${MINH.id}/needs`, `/students/${NGOC.id}/needs`]);
  });

  it("loads the next page with the cursor of the previous one", async () => {
    server = mockApi({
      "GET /students": () => page([MINH]),
      "GET /students/{studentId}/needs": (request) =>
        new URL(request.url).searchParams.get("cursor") === "c1" ? page([CLOSED]) : page([DRAFT], "c1"),
      "GET /me/offers": () => page([]),
    });
    await renderNeeds();
    await screen.findByText("Toán 8: ôn thi học kỳ 2");
    expect(screen.queryByText("Toán 8: nhân chia đa thức")).toBeNull();

    await press("Xem thêm");

    expect(await screen.findByText("Toán 8: nhân chia đa thức")).toBeOnTheScreen();
    expect(screen.getByText("Toán 8: ôn thi học kỳ 2")).toBeOnTheScreen();
    expect(screen.queryByRole("button", { name: "Xem thêm" })).toBeNull();
    const second = server.requests.filter((r) => r.url.includes("/needs"))[1];
    expect(new URL(second.url).searchParams.get("cursor")).toBe("c1");
  });

  it("keeps the child switcher and offers a retry when the needs fail to load", async () => {
    // Reads retry twice on 5xx with the default backoff before the error shows.
    let attempts = 0;
    server = mockApi({
      "GET /students": () => page([MINH]),
      "GET /students/{studentId}/needs": () => {
        attempts += 1;
        return attempts <= 3 ? problem(503, "UNAVAILABLE", "Dịch vụ tạm thời không sẵn sàng.") : page([DRAFT]);
      },
      "GET /me/offers": () => page([]),
    });
    await renderNeeds();

    await screen.findByText("Không tải được dữ liệu. Kiểm tra kết nối rồi thử lại.", {}, { timeout: 10_000 });
    expect(screen.getByRole("button", { name: "Đổi con. Đang xem Minh An, lớp 8" })).toBeOnTheScreen();
    expect(screen.queryByRole("button", { name: /Tạo nhu cầu/ })).toBeNull();

    await press("Thử lại");
    expect(await screen.findByText("Toán 8: ôn thi học kỳ 2")).toBeOnTheScreen();
  }, 15_000);

  it("shows lost access when the server refuses the child's needs", async () => {
    server = mockApi({
      "GET /students": () => page([MINH]),
      "GET /students/{studentId}/needs": () => problem(403, "ACCESS_UNAVAILABLE", "Bạn không có quyền."),
      "GET /me/offers": () => page([]),
    });
    await renderNeeds();

    expect(await screen.findByText("Bạn không còn quyền xem nội dung này.")).toBeOnTheScreen();
    expect(screen.queryByRole("button", { name: /Tạo nhu cầu/ })).toBeNull();
  });

  it("still lists the needs when the offers fail, only without the count", async () => {
    server = mockApi({
      "GET /students": () => page([MINH]),
      "GET /students/{studentId}/needs": () => page([CONFIRMED]),
      "GET /me/offers": () => problem(403, "ACCESS_UNAVAILABLE", "Bạn không có quyền."),
    });
    await renderNeeds();

    expect(await screen.findByText("90 phút, 2 buổi mỗi tuần, tối đa 400.000 ₫ / buổi. Bắt đầu 18/11/2026.")).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Tìm gia sư" })).toBeOnTheScreen();
  });

  it("without a child, leads to PH02 with PH01's reminder", async () => {
    server = mockApi({ "GET /students": () => page([]) });
    const { onAddStudent } = await renderNeeds();

    expect(await screen.findByText("Tạo hồ sơ cho con trước. Mọi việc tìm gia sư, lịch và lớp đều gắn với một con.")).toBeOnTheScreen();
    await press("Thêm hồ sơ con");
    expect(onAddStudent).toHaveBeenCalledTimes(1);
    expect(paths(server)).toEqual(["/students"]);
  });
});
