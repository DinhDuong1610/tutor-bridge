import { act, fireEvent, screen } from "@testing-library/react-native";
import { SelectedStudentProvider } from "@/features/students/selection";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import type { Session } from "../weeks";
import { ClassDetailScreen } from "./ClassDetailScreen";

// CL01 (UC04, UC07; FR-17–19). Fake children, tutor and goal only (AGENTS.md).
const CLASS_ID = "3e0f7c61-5d84-4dba-8d76-903c3f5e2143";
const TUTOR = "2d9e6b50-4c73-4caf-9c65-8f3b2e4d1032";
const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "" };
const NGOC = { id: "1c8d5a4f-3b62-4b9f-8b54-7e2a1d3c0f21", displayName: "Bảo Ngọc", grade: 8, revision: 1, shortGoal: "" };
const GOAL = "Củng cố hằng đẳng thức và phân thức đại số trước kỳ thi học kỳ 1.";
const CLASS = {
  id: CLASS_ID,
  studentId: MINH.id,
  studentDisplayName: "Minh An",
  tutorId: TUTOR,
  revision: 1,
  state: "WAITING",
  priceVndSession: 375000,
  durationMinutes: 90,
  effectiveGoalVersionId: "4f108d72-6e95-4ecb-9e87-a14d406f3254",
  goal: GOAL,
};
const TUTOR_PUBLIC = { tutorId: TUTOR, profileVersionId: CLASS_ID, displayName: "Nguyễn Thị Lan", description: "", rateVnd60: 250000, experienceMonths: 24, experienceLabel: "SELF_DECLARED", verificationScopes: [] };

// Wednesday and Monday 18:00–19:30 Vietnam time from Wed 18/11/2026, as in the Figma frames.
const DATES = ["2026-11-18", "2026-11-23", "2026-11-25", "2026-11-30", "2026-12-02", "2026-12-07", "2026-12-09", "2026-12-14"];
const session = (ordinal: number, date: string, state: Session["state"] = "SCHEDULED"): Session => ({
  id: `00000000-0000-4000-8000-${String(ordinal).padStart(12, "0")}`,
  classId: CLASS_ID,
  ordinal,
  revision: 1,
  state,
  during: { start: `${date}T11:00:00Z`, end: `${date}T12:30:00Z` },
});
const SESSIONS = DATES.map((date, i) => session(i + 1, date));
const page = (items: unknown[], nextCursor: string | null = null) => json(200, { items, nextCursor });

const BEFORE_START = new Date("2026-11-10T03:00:00Z");
const press = (name: string | RegExp) => act(async () => fireEvent.press(screen.getByRole("button", { name })));
const paths = (server: ReturnType<typeof mockApi>) => server.requests.map((r) => new URL(r.url).pathname.replace(/^\/api\/v1/, ""));

describe("CL01 Chi tiết lớp", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  const parentServer = (overrides: Record<string, () => Response> = {}) =>
    mockApi({
      "GET /classes/{classId}": () => json(200, CLASS),
      "GET /classes/{classId}/sessions": () => page(SESSIONS),
      "GET /students": () => page([MINH, NGOC]),
      "GET /tutors/{tutorId}/public-profile": () => json(200, TUTOR_PUBLIC),
      ...overrides,
    });

  async function renderParent(now = BEFORE_START) {
    const handlers = { onBack: jest.fn(), onSessions: jest.fn(), onOtherChild: jest.fn(), onManageStudents: jest.fn() };
    await renderScreen(
      <SelectedStudentProvider>
        <ClassDetailScreen classId={CLASS_ID} role="PARENT" now={now} {...handlers} />
      </SelectedStudentProvider>,
      { session: fakeSession("PARENT") },
    );
    return handlers;
  }

  it("shows the parent the class as TX05 fixed it, before it starts", async () => {
    server = parentServer();
    const handlers = await renderParent();

    expect(await screen.findByText("Nguyễn Thị Lan")).toBeOnTheScreen();
    expect(screen.getByRole("header", { name: "Chi tiết lớp" })).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Đổi con. Đang xem Minh An, lớp 8" })).toBeOnTheScreen();
    expect(screen.getByText("Chưa bắt đầu")).toBeOnTheScreen();
    expect(screen.getByText("Gia sư")).toBeOnTheScreen();
    expect(screen.getByText("Cho con")).toBeOnTheScreen();
    // In the switcher and in "Cho con".
    expect(screen.getAllByText("Minh An, lớp 8")).toHaveLength(2);
    expect(screen.getByRole("header", { name: "Mục tiêu đã chốt" })).toBeOnTheScreen();
    expect(screen.getByText(GOAL)).toBeOnTheScreen();
    expect(screen.getByRole("header", { name: "Học phí đã chốt" })).toBeOnTheScreen();
    expect(screen.getByText("375.000 ₫ / buổi 90 phút")).toBeOnTheScreen();
    expect(screen.getByText("Giữ nguyên theo thỏa thuận khi chốt, kể cả khi gia sư đổi học phí sau này.")).toBeOnTheScreen();
    expect(screen.getByText("8 buổi, Thứ 4 và Thứ 2, 18:00–19:30, từ 18/11 đến 14/12/2026")).toBeOnTheScreen();
    for (const week of [1, 2, 3, 4]) {
      expect(screen.getByLabelText(`Tuần ${week}, 2 buổi`)).toBeOnTheScreen();
    }
    expect(screen.getByText("Chấm đặc: buổi đã chốt.")).toBeOnTheScreen();
    expect(screen.getByText("Buổi tới: Thứ 4, 18/11/2026, 18:00–19:30")).toBeOnTheScreen();

    // Read only (UC08 is outside Lát 1): nothing to change the goal, share contacts, reschedule, cancel or end.
    for (const action of [/mục tiêu/i, /liên hệ/i, /Đổi lịch/, /Hủy/, /Kết thúc/]) {
      expect(screen.queryByRole("button", { name: action })).toBeNull();
    }

    await press("Xem lịch lớp");
    expect(handlers.onSessions).toHaveBeenCalledWith(CLASS_ID);
    expect(server.unexpected).toEqual([]);
  });

  it("while the class runs: 'Đang học', past and cancelled marks, and the next session past a cancelled one", async () => {
    const sessions = SESSIONS.map((s) => (s.ordinal === 4 ? { ...s, state: "CANCELLED" as const } : s));
    server = parentServer({
      "GET /classes/{classId}": () => json(200, { ...CLASS, state: "ACTIVE" }),
      "GET /classes/{classId}/sessions": () => page(sessions),
    });
    // Tue 24/11 08:00: sessions 1 and 2 are over, 3 (Wed 25/11) is next.
    await renderParent(new Date("2026-11-24T01:00:00Z"));

    expect(await screen.findByText("Đang học")).toBeOnTheScreen();
    expect(screen.getByLabelText("Tuần 1, 2 buổi, 2 đã qua")).toBeOnTheScreen();
    expect(screen.getByLabelText("Tuần 2, 2 buổi, 1 đã hủy")).toBeOnTheScreen();
    expect(screen.getByText("Buổi tới: Thứ 4, 25/11/2026, 18:00–19:30")).toBeOnTheScreen();
  });

  it("leaves out 'Buổi tới' once the last session is over", async () => {
    server = parentServer({ "GET /classes/{classId}": () => json(200, { ...CLASS, state: "ACTIVE" }) });
    await renderParent(new Date("2026-12-15T03:00:00Z"));

    expect(await screen.findByText("Đang học")).toBeOnTheScreen();
    expect(screen.queryByText(/^Buổi tới/)).toBeNull();
    expect(screen.getByRole("button", { name: "Xem lịch lớp" })).toBeOnTheScreen();
  });

  it("still shows the class when the tutor's name cannot be read, without the 'Gia sư' line", async () => {
    server = parentServer({ "GET /tutors/{tutorId}/public-profile": () => problem(500, "INTERNAL", "Lỗi máy chủ.") });
    await renderParent();

    expect(await screen.findByText(GOAL)).toBeOnTheScreen();
    expect(screen.queryByText("Gia sư")).toBeNull();
    expect(screen.getByText("Cho con")).toBeOnTheScreen();
  });

  it("choosing another child keeps the choice and leaves; 'Quản lý hồ sơ các con' opens PH02", async () => {
    server = parentServer();
    const handlers = await renderParent();
    await screen.findByText(GOAL);

    await press("Đổi con. Đang xem Minh An, lớp 8");
    await act(async () => fireEvent.press(screen.getByRole("radio", { name: "Bảo Ngọc. Lớp 8." })));
    expect(handlers.onOtherChild).toHaveBeenCalledTimes(1);

    await press("Đổi con. Đang xem Minh An, lớp 8");
    await press("Quản lý hồ sơ các con");
    expect(handlers.onManageStudents).toHaveBeenCalledTimes(1);
  });

  it.each([
    [403, "FORBIDDEN", "Bạn không còn quyền xem nội dung này."],
    [404, "CLASS_NOT_FOUND", "Không tìm thấy nội dung này. Có thể nó đã bị xóa hoặc bạn không có quyền xem."],
  ])("a %i from getClass shows the lost-access state, never the class", async (status, code, message) => {
    server = parentServer({ "GET /classes/{classId}": () => problem(status, code, "Không có quyền.") });
    await renderParent();

    expect(await screen.findByText(message)).toBeOnTheScreen();
    expect(screen.queryByText("Xem lịch lớp")).toBeNull();
  });

  it("a failed read offers 'Thử lại', which reads again", async () => {
    // Reads retry a server error by themselves (queryClient) before the screen shows it, as on CL02.
    let attempts = 0;
    server = parentServer({
      "GET /classes/{classId}/sessions": () => {
        attempts += 1;
        return attempts <= 3 ? problem(503, "UNAVAILABLE", "Dịch vụ tạm thời không sẵn sàng.") : page(SESSIONS);
      },
    });
    await renderParent();

    await screen.findByText("Không tải được dữ liệu. Kiểm tra kết nối rồi thử lại.", {}, { timeout: 10_000 });
    expect(screen.queryByText(GOAL)).toBeNull();
    await press("Thử lại");
    expect(await screen.findByText(GOAL)).toBeOnTheScreen();
  }, 15_000);

  it("shows the tutor the student's name (OP-25) and never reads the parent's lists", async () => {
    server = mockApi({
      "GET /classes/{classId}": () => json(200, CLASS),
      "GET /classes/{classId}/sessions": () => page(SESSIONS),
    });
    const onSessions = jest.fn();
    await renderScreen(<ClassDetailScreen classId={CLASS_ID} role="TUTOR" now={BEFORE_START} onBack={jest.fn()} onSessions={onSessions} />, {
      session: fakeSession("TUTOR"),
    });

    expect(await screen.findByText("Học sinh")).toBeOnTheScreen();
    expect(screen.getByText("Minh An")).toBeOnTheScreen();
    expect(screen.getByText("375.000 ₫ / buổi 90 phút")).toBeOnTheScreen();
    expect(screen.queryByText("Cho con")).toBeNull();
    expect(screen.queryByRole("button", { name: /Đổi con/ })).toBeNull();
    expect(paths(server).sort()).toEqual([`/classes/${CLASS_ID}`, `/classes/${CLASS_ID}/sessions`]);

    await press("Xem lịch lớp");
    expect(onSessions).toHaveBeenCalledWith(CLASS_ID);
    expect(server.unexpected).toEqual([]);
  });
});
