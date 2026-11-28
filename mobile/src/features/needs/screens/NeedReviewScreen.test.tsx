import { act, fireEvent, screen } from "@testing-library/react-native";
import { emptyWeek, spreadWeek, type WeekPattern } from "@/features/calendar/weekly";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import type { Need } from "../api";
import { NeedReviewScreen } from "./NeedReviewScreen";

// 10:00 on Thứ 4 18/11/2026 in Vietnam. Fake children only (AGENTS.md).
const NOW = () => new Date("2026-11-18T03:00:00Z");
const TODAY = "2026-11-18";
const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "" };
const NEED_ID = "3d9e6b5a-4c73-4caf-9c65-8f3b2e4d1a32";
const GOAL = "Củng cố hằng đẳng thức và phân thức đại số trước kỳ thi học kỳ 1.";
const COMPLETE = { goal: GOAL, durationMinutes: 90, frequencyPer7Days: 2, budgetVndSession: 400000, cycleStartDate: "2026-12-02" } as const;

function week(entries: Record<number, [string, string][]>): WeekPattern {
  const result = emptyWeek();
  for (const [day, slots] of Object.entries(entries)) {
    result[Number(day)] = slots.map(([start, end]) => ({ start, end }));
  }
  return result;
}

const evenings: [string, string][] = [["18:00", "21:00"]];
/** The approved frame's free time: Thứ 2, 4, 6 evenings and Thứ 7 morning. */
const FREE = spreadWeek(week({ 0: evenings, 2: evenings, 4: evenings, 5: [["08:00", "11:00"]] }), emptyWeek(), TODAY);

function need(revision: number, status: Need["status"] = "DRAFT", draft: Need["draft"] = COMPLETE): Need {
  return { id: NEED_ID, studentId: MINH.id, revision, versionId: "4e0f7c6b-5d84-4dba-8d76-9a4c3f5e2b43", status, draft, studentCalendarId: null };
}

function withEtag(body: { revision?: number; version?: number }, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ETag: `"${body.revision ?? body.version}"` },
  });
}

const calendar = () => withEtag({ id: "6a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d", version: 4, timezone: "Asia/Ho_Chi_Minh", windows: FREE } as { version: number });
const students = () => json(200, { items: [MINH], nextCursor: null });
const press = (name: string) => act(async () => fireEvent.press(screen.getByRole("button", { name })));
/** The sheet's "Xác nhận nhu cầu", which sends; the bar's opens the sheet. */
const pressInSheet = (name: string) =>
  act(async () => {
    const buttons = screen.getAllByRole("button", { name });
    fireEvent.press(buttons[buttons.length - 1]);
  });
const confirms = (server: ReturnType<typeof mockApi>) => server.requests.filter((r) => r.method === "POST");

async function renderReview() {
  const handlers = { onBack: jest.fn(), onEdit: jest.fn(), onFindTutors: jest.fn() };
  await renderScreen(<NeedReviewScreen needId={NEED_ID} {...handlers} now={NOW} />, { session: fakeSession("PARENT") });
  return handlers;
}

describe("PH05 Xem lại và xác nhận nhu cầu", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("AC-UC04-01 shows the need as the server holds it, line by line, with the cycle's sessions", async () => {
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(need(2)),
      "GET /students": students,
      "GET /students/{studentId}/calendar": calendar,
    });
    await renderReview();

    expect(await screen.findByText("Kiểm tra lại trước khi tìm gia sư. Đây là bản đang lưu trên hệ thống.")).toBeTruthy();
    expect(screen.getByRole("header", { name: "Xem lại nhu cầu" })).toBeTruthy();
    for (const text of [
      "Minh An, lớp 8",
      "Toán 8, đại số, học trực tuyến",
      GOAL,
      "90 phút, 2 buổi mỗi tuần",
      "400.000 ₫ / buổi 90 phút",
      "Thứ 2, 4, 6: 18:00–21:00; Thứ 7: 08:00–11:00",
      "Không chọn",
      "8 buổi trong 28 ngày, 2 buổi mỗi tuần, từ 02/12 đến 29/12/2026",
      "Vòng nét đứt: số buổi cần mỗi tuần. Giờ cụ thể có khi gửi đề nghị cho gia sư.",
    ]) {
      expect(screen.getByText(text)).toBeTruthy();
    }
    expect(screen.getByLabelText("Tuần 1, 2 buổi")).toBeTruthy();
    expect(screen.getByLabelText("Tuần 4, 2 buổi")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Xác nhận nhu cầu" })).toBeEnabled();
  });

  it("asks first, then confirms exactly the version shown (If-Match) and offers to find tutors", async () => {
    let stored = need(2);
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(stored),
      "GET /students": students,
      "GET /students/{studentId}/calendar": calendar,
      "POST /needs/{needId}/confirm": () => {
        stored = need(3, "CONFIRMED");
        return withEtag(stored);
      },
    });
    const { onFindTutors } = await renderReview();
    await screen.findByText(GOAL);

    await press("Xác nhận nhu cầu");
    expect(screen.getByRole("header", { name: "Xác nhận nhu cầu này?" })).toBeTruthy();
    expect(
      screen.getByText(
        "Gợi ý gia sư và đề nghị sẽ dùng đúng bản này. Sau khi xác nhận, muốn sửa sẽ tạo bản nháp mới; kết quả cũ được giữ để đối chiếu.",
      ),
    ).toBeTruthy();
    expect(confirms(server)).toHaveLength(0);
    await pressInSheet("Xác nhận nhu cầu");

    const [post] = confirms(server);
    expect(new URL(post.url).pathname).toBe(`/api/v1/needs/${NEED_ID}/confirm`);
    expect(post.headers.get("If-Match")).toBe('"2"');
    expect(post.headers.get("Idempotency-Key")).toBeTruthy();
    expect(await post.json()).toEqual({ confirmed: true });
    expect(await screen.findByText("Đã xác nhận nhu cầu. Bạn có thể tìm gia sư từ bản này.")).toBeTruthy();
    expect(screen.getByRole("header", { name: "Nhu cầu đã xác nhận" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Sửa" })).toBeNull();

    await press("Tìm gia sư");
    expect(onFindTutors).toHaveBeenCalledWith(NEED_ID);
  });

  it("'Quay lại' closes the sheet without sending anything", async () => {
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(need(2)),
      "GET /students": students,
      "GET /students/{studentId}/calendar": calendar,
    });
    await renderReview();
    await screen.findByText(GOAL);

    await press("Xác nhận nhu cầu");
    // The top bar's back button has the same name; the sheet's comes last.
    await pressInSheet("Quay lại");

    expect(screen.queryByText("Xác nhận nhu cầu này?")).toBeNull();
    expect(confirms(server)).toHaveLength(0);
  });

  it("INV-23 a stale ETag (412) is reloaded and the newer version confirmed against its own ETag", async () => {
    let stored = need(2);
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(stored),
      "GET /students": students,
      "GET /students/{studentId}/calendar": calendar,
      "POST /needs/{needId}/confirm": (request) =>
        request.headers.get("If-Match") === '"2"'
          ? problem(412, "PRECONDITION_FAILED", "Dữ liệu đã thay đổi.")
          : withEtag(need(4, "CONFIRMED", stored.draft)),
    });
    await renderReview();
    await screen.findByText(GOAL);
    stored = need(3, "DRAFT", { ...COMPLETE, budgetVndSession: 500000 });

    await press("Xác nhận nhu cầu");
    await pressInSheet("Xác nhận nhu cầu");
    expect(
      await screen.findByText("Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."),
    ).toBeTruthy();
    expect(screen.getByRole("button", { name: "Xác nhận nhu cầu" })).toBeDisabled();

    await press("Tải lại");
    // The newer version is what the parent now sees, and confirms.
    expect(await screen.findByText("500.000 ₫ / buổi 90 phút")).toBeTruthy();
    await press("Xác nhận nhu cầu");
    await pressInSheet("Xác nhận nhu cầu");

    expect(confirms(server).map((r) => r.headers.get("If-Match"))).toEqual(['"2"', '"3"']);
  });

  it("AC-UC04-01 a version read again while the sheet is open is not what gets confirmed (Đính, #266)", async () => {
    // The sheet hides the review, so the parent never saw the newer budget: confirm what was asked about.
    let stored = need(2);
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(stored),
      "GET /students": students,
      "GET /students/{studentId}/calendar": calendar,
      "POST /needs/{needId}/confirm": (request) =>
        request.headers.get("If-Match") === '"3"'
          ? withEtag(need(4, "CONFIRMED", stored.draft))
          : problem(412, "PRECONDITION_FAILED", "Dữ liệu đã thay đổi."),
    });
    const handlers = { onBack: jest.fn(), onEdit: jest.fn(), onFindTutors: jest.fn() };
    const { queryClient } = await renderScreen(<NeedReviewScreen needId={NEED_ID} {...handlers} now={NOW} />, {
      session: fakeSession("PARENT"),
    });
    await screen.findByText("400.000 ₫ / buổi 90 phút");

    await press("Xác nhận nhu cầu");
    stored = need(3, "DRAFT", { ...COMPLETE, budgetVndSession: 500000 });
    await act(async () => {
      await queryClient.refetchQueries({ queryKey: ["needs"] });
    });
    expect(await screen.findByText("500.000 ₫ / buổi 90 phút")).toBeTruthy();
    await pressInSheet("Xác nhận nhu cầu");

    expect(confirms(server).map((r) => r.headers.get("If-Match"))).toEqual(['"2"']);
    expect(
      await screen.findByText("Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."),
    ).toBeTruthy();
    expect(screen.queryByText("Đã xác nhận nhu cầu. Bạn có thể tìm gia sư từ bản này.")).toBeNull();
  });

  it("INV-20 after an unknown outcome the retry resends the same key and precondition", async () => {
    let attempts = 0;
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(need(2)),
      "GET /students": students,
      "GET /students/{studentId}/calendar": calendar,
      "POST /needs/{needId}/confirm": () => {
        attempts++;
        return attempts === 1 ? problem(503, "SERVICE_UNAVAILABLE", "Hệ thống đang bận.") : withEtag(need(3, "CONFIRMED"));
      },
    });
    await renderReview();
    await screen.findByText(GOAL);
    await press("Xác nhận nhu cầu");
    await pressInSheet("Xác nhận nhu cầu");

    expect(
      await screen.findByText("Chưa rõ kết quả. Yêu cầu có thể đã được ghi nhận. Thử lại sẽ không tạo bản trùng."),
    ).toBeTruthy();
    expect(screen.getByRole("button", { name: "Xác nhận nhu cầu" })).toBeDisabled();
    await press("Thử lại");

    const [first, second] = confirms(server);
    expect(second.headers.get("Idempotency-Key")).toBe(first.headers.get("Idempotency-Key"));
    expect(second.headers.get("If-Match")).toBe(first.headers.get("If-Match"));
    expect(await screen.findByText("Đã xác nhận nhu cầu. Bạn có thể tìm gia sư từ bản này.")).toBeTruthy();
  });

  it("shows the server's reason when it refuses the confirmation (422, free time short)", async () => {
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(need(2)),
      "GET /students": students,
      "GET /students/{studentId}/calendar": calendar,
      "POST /needs/{needId}/confirm": () => problem(422, "SCHEDULE_TOO_SHORT", "Giờ rảnh chưa đủ cho tuần 3."),
    });
    await renderReview();
    await screen.findByText(GOAL);
    await press("Xác nhận nhu cầu");
    await pressInSheet("Xác nhận nhu cầu");

    expect(await screen.findByText("Giờ rảnh chưa đủ cho tuần 3.")).toBeTruthy();
  });

  it("an incomplete draft names what is missing and cannot be confirmed; 'Sửa' goes back to PH04", async () => {
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(need(2, "DRAFT", { goal: GOAL, durationMinutes: 90 })),
      "GET /students": students,
      "GET /students/{studentId}/calendar": () => problem(404, "CALENDAR_NOT_FOUND", "Chưa có lịch rảnh."),
    });
    const { onEdit } = await renderReview();

    expect(
      await screen.findByText(
        "Đã lưu nháp. Cần thêm: ngân sách, số buổi mỗi tuần, ngày bắt đầu, giờ rảnh của con. Chưa thể tìm gia sư từ bản nháp.",
      ),
    ).toBeTruthy();
    expect(screen.getByRole("button", { name: "Xác nhận nhu cầu" })).toBeDisabled();
    await press("Sửa");
    expect(onEdit).toHaveBeenCalledWith(NEED_ID);
  });

  it("a need already confirmed opens as confirmed, with 'Tìm gia sư' and no edit", async () => {
    server = mockApi({
      "GET /needs/{needId}": () => withEtag(need(3, "CONFIRMED")),
      "GET /students": students,
      "GET /students/{studentId}/calendar": calendar,
    });
    await renderReview();

    expect(await screen.findByRole("header", { name: "Nhu cầu đã xác nhận" })).toBeTruthy();
    // The title comes with the need; the actions wait for the child's calendar.
    expect(await screen.findByRole("button", { name: "Tìm gia sư" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Xác nhận nhu cầu" })).toBeNull();
    expect(screen.queryByText("Đã xác nhận nhu cầu. Bạn có thể tìm gia sư từ bản này.")).toBeNull();
  });

  it("INV-06 another family's need is lost access", async () => {
    server = mockApi({
      "GET /needs/{needId}": () => problem(403, "ACCESS_UNAVAILABLE", "Tài khoản không thể thực hiện thao tác này."),
      "GET /students": students,
    });
    await renderReview();

    expect(await screen.findByText("Bạn không còn quyền xem nội dung này.")).toBeTruthy();
    expect(screen.queryByText("Cho con")).toBeNull();
  });
});
