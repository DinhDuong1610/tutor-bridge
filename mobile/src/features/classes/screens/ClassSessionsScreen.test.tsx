import { act, fireEvent, screen } from "@testing-library/react-native";
import { SelectedStudentProvider } from "@/features/students/selection";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import type { Session } from "../weeks";
import { ClassSessionsScreen } from "./ClassSessionsScreen";

// CL02 (UC07; FR-19, FR-20). Fake children, tutor and goal only (AGENTS.md).
const CLASS_ID = "3e0f7c61-5d84-4dba-8d76-903c3f5e2143";
const TUTOR = "2d9e6b50-4c73-4caf-9c65-8f3b2e4d1032";
const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "" };
const NGOC = { id: "1c8d5a4f-3b62-4b9f-8b54-7e2a1d3c0f21", displayName: "Bảo Ngọc", grade: 8, revision: 1, shortGoal: "" };
const CLASS = {
  id: CLASS_ID,
  studentId: MINH.id,
  tutorId: TUTOR,
  revision: 1,
  state: "ACTIVE",
  priceVndSession: 375000,
  durationMinutes: 90,
  effectiveGoalVersionId: "4f108d72-6e95-4ecb-9e87-a14d406f3254",
  goal: "Củng cố hằng đẳng thức và phân thức đại số trước kỳ thi học kỳ 1.",
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

describe("CL02 Lịch lớp", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  const parentServer = (sessions: Session[] = SESSIONS) =>
    mockApi({
      "GET /classes/{classId}": () => json(200, CLASS),
      "GET /classes/{classId}/sessions": () => page(sessions),
      "GET /students": () => page([MINH, NGOC]),
      "GET /tutors/{tutorId}/public-profile": () => json(200, TUTOR_PUBLIC),
    });

  async function renderParent(now = BEFORE_START) {
    const handlers = { onBack: jest.fn(), onOtherChild: jest.fn() };
    await renderScreen(
      <SelectedStudentProvider>
        <ClassSessionsScreen classId={CLASS_ID} role="PARENT" now={now} {...handlers} />
      </SelectedStudentProvider>,
      { session: fakeSession("PARENT") },
    );
    return handlers;
  }

  it("shows the class's child, the class line and week 1 with its sessions in Vietnam time", async () => {
    server = parentServer();
    await renderParent();

    expect(await screen.findByText("Toán 8 với Nguyễn Thị Lan, 8 buổi từ 18/11 đến 14/12/2026")).toBeOnTheScreen();
    expect(screen.getByRole("header", { name: "Lịch lớp" })).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Đổi con. Đang xem Minh An, lớp 8" })).toBeOnTheScreen();
    expect(screen.getByRole("header", { name: "Tuần 1: 18/11 – 24/11/2026" })).toBeOnTheScreen();
    expect(screen.getAllByText("18:00–19:30")).toHaveLength(2);
    expect(screen.getByText("Buổi 1 trên 8. 90 phút, Toán 8 với Nguyễn Thị Lan")).toBeOnTheScreen();
    expect(screen.getByText("Buổi 2 trên 8. 90 phút, Toán 8 với Nguyễn Thị Lan")).toBeOnTheScreen();
    expect(screen.getAllByText("Đã chốt")).toHaveLength(2);
    // Read only (UC08 is outside Lát 1): nothing to change, cancel or end.
    for (const action of [/Đổi lịch/, /Hủy/, /Kết thúc/]) {
      expect(screen.queryByRole("button", { name: action })).toBeNull();
    }
    const sessions = server.requests.find((r) => r.url.includes("/sessions"))!;
    expect(new URL(sessions.url).searchParams.get("limit")).toBe("50");
    expect(server.unexpected).toEqual([]);
  });

  it("moves between weeks and marks the sessions already over (#73 Q2: opens on this week)", async () => {
    server = parentServer();
    // Fri 27/11: week 2 holds Wed 25/11 (over) and Mon 30/11 (ahead).
    await renderParent(new Date("2026-11-27T03:00:00Z"));

    expect(await screen.findByRole("header", { name: "Tuần 2: 25/11 – 01/12/2026" })).toBeOnTheScreen();
    expect(screen.getByText("Đã qua")).toBeOnTheScreen();
    expect(screen.getByText("Đã chốt")).toBeOnTheScreen();

    await press("Tuần trước");
    expect(screen.getByRole("header", { name: "Tuần 1: 18/11 – 24/11/2026" })).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Tuần trước" })).toBeDisabled();

    for (let i = 0; i < 3; i += 1) await press("Tuần sau");
    expect(screen.getByRole("header", { name: "Tuần 4: 09/12 – 15/12/2026" })).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Tuần sau" })).toBeDisabled();
  });

  it("shows an empty week after the class when today is past it", async () => {
    server = parentServer();
    await renderParent(new Date("2026-12-17T03:00:00Z"));

    expect(await screen.findByRole("header", { name: "Tuần 16/12 – 22/12/2026" })).toBeOnTheScreen();
    expect(screen.getByText("Không có buổi nào trong tuần này.")).toBeOnTheScreen();
  });

  it("shows a cancelled session with words and a struck time, not colour alone", async () => {
    server = parentServer([session(1, DATES[0], "CANCELLED"), ...SESSIONS.slice(1)]);
    await renderParent();

    expect(await screen.findByText("Đã hủy")).toBeOnTheScreen();
    const struck = screen.getAllByText("18:00–19:30")[0];
    expect(struck).toHaveStyle({ textDecorationLine: "line-through" });
  });

  it("opens a session's details in a sheet and closes it", async () => {
    server = parentServer();
    await renderParent();
    await screen.findByText("Buổi 1 trên 8. 90 phút, Toán 8 với Nguyễn Thị Lan");

    await press(/Buổi 1 trên 8\. 90 phút/);

    const sheet = screen.getByRole("header", { name: "Buổi 1 trên 8" });
    expect(sheet).toBeOnTheScreen();
    expect(screen.getByText("Thứ 4, 18/11/2026, 18:00–19:30 (giờ Việt Nam)")).toBeOnTheScreen();
    expect(screen.getByText("90 phút")).toBeOnTheScreen();
    expect(screen.getByText("Toán 8 với Nguyễn Thị Lan")).toBeOnTheScreen();
    // Once on the switcher bar, once as the sheet's "Cho con".
    expect(screen.getByText("Cho con")).toBeOnTheScreen();
    expect(screen.getAllByText("Minh An, lớp 8")).toHaveLength(2);

    await press("Đóng");
    expect(screen.queryByRole("header", { name: "Buổi 1 trên 8" })).toBeNull();
  });

  it("leaves the class when the parent chooses another child (#73 Q3)", async () => {
    server = parentServer();
    const { onOtherChild } = await renderParent();
    await screen.findByText("Toán 8 với Nguyễn Thị Lan, 8 buổi từ 18/11 đến 14/12/2026");

    await press("Đổi con. Đang xem Minh An, lớp 8");
    await act(async () => fireEvent.press(screen.getByRole("radio", { name: "Bảo Ngọc. Lớp 8." })));

    expect(onOtherChild).toHaveBeenCalledTimes(1);
  });

  it("still shows the week when the tutor's name fails to load, without the name", async () => {
    server = mockApi({
      "GET /classes/{classId}": () => json(200, CLASS),
      "GET /classes/{classId}/sessions": () => page(SESSIONS),
      "GET /students": () => page([MINH]),
      "GET /tutors/{tutorId}/public-profile": () => problem(404, "NOT_FOUND", "Không tìm thấy"),
    });
    await renderParent();

    expect(await screen.findByText("Toán 8, 8 buổi từ 18/11 đến 14/12/2026")).toBeOnTheScreen();
  });

  it("shows lost access when the class is not the parent's (404 or 403 from getClass)", async () => {
    server = mockApi({
      "GET /classes/{classId}": () => problem(404, "CLASS_NOT_FOUND", "Không tìm thấy lớp."),
      "GET /classes/{classId}/sessions": () => problem(404, "CLASS_NOT_FOUND", "Không tìm thấy lớp."),
      "GET /students": () => page([MINH]),
    });
    await renderParent();

    expect(await screen.findByText("Không tìm thấy nội dung này. Có thể nó đã bị xóa hoặc bạn không có quyền xem.")).toBeOnTheScreen();
    expect(screen.queryByText(/Tuần 1/)).toBeNull();
  });

  it("shows the load error with a retry", async () => {
    let attempts = 0;
    server = mockApi({
      "GET /classes/{classId}": () => json(200, CLASS),
      "GET /classes/{classId}/sessions": () => {
        attempts += 1;
        return attempts <= 3 ? problem(503, "UNAVAILABLE", "Dịch vụ tạm thời không sẵn sàng.") : page(SESSIONS);
      },
      "GET /students": () => page([MINH]),
      "GET /tutors/{tutorId}/public-profile": () => json(200, TUTOR_PUBLIC),
    });
    await renderParent();

    await screen.findByText("Không tải được dữ liệu. Kiểm tra kết nối rồi thử lại.", {}, { timeout: 10_000 });
    await press("Thử lại");
    expect(await screen.findByRole("header", { name: "Tuần 1: 18/11 – 24/11/2026" })).toBeOnTheScreen();
  }, 15_000);

  it("reads every page of sessions with the cursor", async () => {
    server = mockApi({
      "GET /classes/{classId}": () => json(200, CLASS),
      "GET /classes/{classId}/sessions": (request) =>
        new URL(request.url).searchParams.get("cursor") === "c1" ? page(SESSIONS.slice(1)) : page(SESSIONS.slice(0, 1), "c1"),
      "GET /students": () => page([MINH]),
      "GET /tutors/{tutorId}/public-profile": () => json(200, TUTOR_PUBLIC),
    });
    await renderParent();

    expect(await screen.findByText("Toán 8 với Nguyễn Thị Lan, 8 buổi từ 18/11 đến 14/12/2026")).toBeOnTheScreen();
    expect(paths(server).filter((path) => path.endsWith("/sessions"))).toHaveLength(2);
  });

  it("the tutor sees the class without the parent's lists and without the student's name (OP-25)", async () => {
    server = mockApi({
      "GET /classes/{classId}": () => json(200, CLASS),
      "GET /classes/{classId}/sessions": () => page(SESSIONS),
    });
    await renderScreen(<ClassSessionsScreen classId={CLASS_ID} role="TUTOR" now={BEFORE_START} onBack={jest.fn()} />, {
      session: fakeSession("TUTOR"),
    });

    expect(await screen.findByText("Lớp Toán 8 đại số, 8 buổi từ 18/11 đến 14/12/2026")).toBeOnTheScreen();
    expect(screen.getByText("Buổi 1 trên 8. 90 phút, lớp Toán 8 đại số")).toBeOnTheScreen();
    expect(screen.queryByText(/Đổi con/)).toBeNull();
    expect(screen.queryByText(/Minh An/)).toBeNull();
    // Only the class reads: no listStudents, no getTutorPublic.
    expect(paths(server).sort()).toEqual([`/classes/${CLASS_ID}`, `/classes/${CLASS_ID}/sessions`]);

    await press(/Buổi 1 trên 8\. 90 phút/);
    expect(screen.getByRole("header", { name: "Buổi 1 trên 8" })).toBeOnTheScreen();
    expect(screen.queryByText("Cho con")).toBeNull();
  });
});
