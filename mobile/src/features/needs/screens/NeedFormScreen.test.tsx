import { act, fireEvent, screen } from "@testing-library/react-native";
import { emptyWeek, spreadWeek, type CalendarWindow, type WeekPattern } from "@/features/calendar/weekly";
import { SelectedStudentProvider } from "@/features/students/selection";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import type { Need } from "../api";
import { EditNeedScreen, NewNeedScreen } from "./NeedFormScreen";

// 10:00 on Thứ 4 18/11/2026 in Vietnam, as in the approved frames. Fake children only (AGENTS.md).
const NOW = () => new Date("2026-11-18T03:00:00Z");
const TODAY = "2026-11-18";
const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "" };
const NEED_ID = "3d9e6b5a-4c73-4caf-9c65-8f3b2e4d1a32";
const CALENDAR_ID = "6a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";
const GOAL = "Củng cố hằng đẳng thức và phân thức đại số trước kỳ thi học kỳ 1.";
const COMPLETE = { goal: GOAL, durationMinutes: 90, frequencyPer7Days: 2, budgetVndSession: 400000, cycleStartDate: "2026-12-02" } as const;

function week(entries: Record<number, [string, string][]>): WeekPattern {
  const result = emptyWeek();
  for (const [day, slots] of Object.entries(entries)) {
    result[Number(day)] = slots.map(([start, end]) => ({ start, end }));
  }
  return result;
}

/** Monday 18:00–21:00 every week: two 90-minute sessions a week fit, three do not. */
const MONDAY = spreadWeek(week({ 0: [["18:00", "21:00"]] }), emptyWeek(), TODAY);

function need(revision: number, draft: Need["draft"], status: Need["status"] = "DRAFT"): Need {
  return { id: NEED_ID, studentId: MINH.id, revision, versionId: "4e0f7c6b-5d84-4dba-8d76-9a4c3f5e2b43", status, draft, studentCalendarId: null };
}

/** The need and calendar operations answer with the strong ETag of the revision, as the backend does. */
function withEtag(body: { revision?: number; version?: number }, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ETag: `"${body.revision ?? body.version}"` },
  });
}

const calendar = (version: number, windows: CalendarWindow[]) =>
  withEtag({ id: CALENDAR_ID, version, timezone: "Asia/Ho_Chi_Minh", windows } as { version: number });
const noCalendar = () => problem(404, "CALENDAR_NOT_FOUND", "Chưa có lịch rảnh.");
const students = () => json(200, { items: [MINH], nextCursor: null });
const press = (name: string) => act(async () => fireEvent.press(screen.getByRole("button", { name })));
const type = (label: string, text: string) => act(async () => fireEvent.changeText(screen.getByLabelText(label), text));
const choose = (label: string) => act(async () => fireEvent.press(screen.getByRole("radio", { name: label })));
const writes = (server: ReturnType<typeof mockApi>) => server.requests.filter((r) => ["POST", "PATCH", "PUT"].includes(r.method));

async function addSlot(day: string, start: string, end: string) {
  await press(`Thêm giờ, ${day}`);
  await type("Từ", start);
  await type("Đến", end);
  await press("Thêm khung giờ");
}

async function renderNew() {
  const handlers = { onBack: jest.fn(), onReview: jest.fn() };
  await renderScreen(
    <SelectedStudentProvider>
      <NewNeedScreen {...handlers} now={NOW} />
    </SelectedStudentProvider>,
    { session: fakeSession("PARENT") },
  );
  return handlers;
}

async function renderEdit() {
  const handlers = { onBack: jest.fn(), onReview: jest.fn() };
  await renderScreen(
    <SelectedStudentProvider>
      <EditNeedScreen needId={NEED_ID} {...handlers} now={NOW} />
    </SelectedStudentProvider>,
    { session: fakeSession("PARENT") },
  );
  return handlers;
}

describe("PH04 Nhập nhu cầu học kèm", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("AC-UC04-01 saves an incomplete draft for the chosen child, names what is missing, then updates it with If-Match", async () => {
    let revision = 0;
    server = mockApi({
      "GET /students": students,
      "GET /students/{studentId}/calendar": noCalendar,
      "POST /students/{studentId}/needs": async (request) => {
        revision = 1;
        return withEtag(need(1, await request.clone().json()), 201);
      },
      "PATCH /needs/{needId}": async (request) => {
        revision += 1;
        return withEtag(need(revision, await request.clone().json()));
      },
    });
    await renderNew();

    expect(await screen.findByText("Minh An, lớp 8")).toBeTruthy();
    expect(screen.getByText("Toán 8, đại số, học trực tuyến. Thí điểm hiện chỉ có phạm vi này.")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Xem lại" })).toBeDisabled();
    expect(screen.getByLabelText("Ngân sách tối đa cho một buổi (VND)")).toBeTruthy();

    await type("Mục tiêu học kèm", GOAL);
    await choose("90 phút");
    expect(screen.getByRole("radio", { name: "90 phút" })).toBeChecked();
    // The budget label follows the duration (PH04 "Hành vi").
    expect(screen.getByLabelText("Ngân sách tối đa cho một buổi 90 phút (VND)")).toBeTruthy();
    expect(screen.getByText(`${GOAL.length}/2.000 ký tự, tối thiểu 20.`)).toBeTruthy();

    await press("Lưu nháp");

    const [created] = writes(server);
    expect(new URL(created.url).pathname).toBe(`/api/v1/students/${MINH.id}/needs`);
    expect(created.headers.get("Idempotency-Key")).toBeTruthy();
    expect(await created.json()).toEqual({ goal: GOAL, durationMinutes: 90 });
    expect(
      await screen.findByText(
        "Đã lưu nháp. Cần thêm: ngân sách, số buổi mỗi tuần, ngày bắt đầu, giờ rảnh của con. Chưa thể tìm gia sư từ bản nháp.",
      ),
    ).toBeTruthy();
    expect(screen.getByRole("button", { name: "Xem lại" })).toBeDisabled();

    await type("Ngân sách tối đa cho một buổi 90 phút (VND)", "400.000");
    await press("Lưu nháp");

    const updated = writes(server)[1];
    expect(updated.method).toBe("PATCH");
    expect(updated.headers.get("If-Match")).toBe('"1"');
    // The whole draft, as updateNeedDraft replaces it (#16 Q3).
    expect(await updated.json()).toEqual({ goal: GOAL, durationMinutes: 90, budgetVndSession: 400000 });
  });

  it("checks the fields on the device with PH04's copy and sends nothing until they are fixed", async () => {
    server = mockApi({ "GET /students": students, "GET /students/{studentId}/calendar": noCalendar });
    await renderNew();
    await screen.findByText("Minh An, lớp 8");

    await type("Mục tiêu học kèm", "Học thêm Toán.");
    await type("Ngân sách tối đa cho một buổi (VND)", "0");
    await type("Ngày bắt đầu chu kỳ", "20/12/2026");
    await press("Lưu nháp");

    expect(screen.getByText("Lỗi: Mục tiêu cần ít nhất 20 ký tự (hiện 14).")).toBeTruthy();
    expect(screen.getByText("Lỗi: Ngân sách phải là số nguyên lớn hơn 0.")).toBeTruthy();
    expect(screen.getByText("Lỗi: Chọn ngày từ 18/11/2026 đến 16/12/2026.")).toBeTruthy();
    expect(screen.getByText("Vui lòng sửa các ô được đánh dấu rồi gửi lại.")).toBeTruthy();
    expect(writes(server)).toHaveLength(0);
  });

  it("opens a complete draft with the child's free time as saved, and 'Xem lại' goes on without saving again", async () => {
    server = mockApi({
      "GET /students": students,
      "GET /needs/{needId}": () => withEtag(need(2, COMPLETE)),
      "GET /students/{studentId}/calendar": () => calendar(4, MONDAY),
    });
    const { onReview } = await renderEdit();

    expect(await screen.findByDisplayValue(GOAL)).toBeTruthy();
    expect(screen.getByDisplayValue("400.000")).toBeTruthy();
    expect(screen.getByDisplayValue("02/12/2026")).toBeTruthy();
    expect(screen.getByText("Thứ 4, 02/12/2026. Từ hôm nay đến 16/12/2026. Chu kỳ gồm 4 tuần liên tiếp kể từ ngày này.")).toBeTruthy();
    expect(screen.getByRole("radio", { name: "2 buổi" })).toBeChecked();
    expect(screen.getByText("Giờ Việt Nam. Chỉ gợi ý gia sư rảnh trùng giờ này cho đủ cả chu kỳ.")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Bỏ khung Thứ 2 18:00–21:00" })).toBeTruthy();

    await press("Xem lại");

    expect(onReview).toHaveBeenCalledWith(NEED_ID);
    expect(writes(server)).toHaveLength(0);
  });

  it("'Xem lại' after a change saves first against the ETag it was read with, then goes on", async () => {
    server = mockApi({
      "GET /students": students,
      "GET /needs/{needId}": () => withEtag(need(2, COMPLETE)),
      "GET /students/{studentId}/calendar": () => calendar(4, MONDAY),
      "PATCH /needs/{needId}": async (request) => withEtag(need(3, await request.clone().json())),
    });
    const { onReview } = await renderEdit();
    await screen.findByDisplayValue(GOAL);

    await choose("1 buổi");
    await press("Xem lại");

    const [patch] = writes(server);
    expect(patch.headers.get("If-Match")).toBe('"2"');
    expect(await patch.json()).toEqual({ ...COMPLETE, frequencyPer7Days: 1 });
    // The free time did not change: no calendar save.
    expect(writes(server)).toHaveLength(1);
    expect(onReview).toHaveBeenCalledWith(NEED_ID);
  });

  it("Huy's order: the need is saved first, then the child's calendar over 56 days with If-None-Match: *", async () => {
    server = mockApi({
      "GET /students": students,
      "GET /students/{studentId}/calendar": noCalendar,
      "POST /students/{studentId}/needs": async (request) => withEtag(need(1, await request.clone().json()), 201),
      "PUT /students/{studentId}/calendar": async (request) => calendar(1, (await request.clone().json()).windows),
    });
    await renderNew();
    await screen.findByText("Minh An, lớp 8");
    await type("Mục tiêu học kèm", GOAL);
    await addSlot("Thứ 2", "18:00", "21:00");

    await press("Lưu nháp");

    const [post, put] = writes(server);
    expect(post.method).toBe("POST");
    expect(put.method).toBe("PUT");
    expect(new URL(put.url).pathname).toBe(`/api/v1/students/${MINH.id}/calendar`);
    expect(put.headers.get("If-None-Match")).toBe("*");
    expect(await put.json()).toEqual({ timezone: "Asia/Ho_Chi_Minh", windows: MONDAY });
    // The saved calendar gives free time, so it is no longer missing.
    expect(
      await screen.findByText("Đã lưu nháp. Cần thêm: ngân sách, thời lượng, số buổi mỗi tuần, ngày bắt đầu. Chưa thể tìm gia sư từ bản nháp."),
    ).toBeTruthy();
  });

  it("Huy's order: a calendar save that fails after the need says so and retries the calendar alone (INV-20)", async () => {
    let calendarAttempts = 0;
    server = mockApi({
      "GET /students": students,
      "GET /needs/{needId}": () => withEtag(need(2, COMPLETE)),
      "GET /students/{studentId}/calendar": () => calendar(4, MONDAY),
      "PUT /students/{studentId}/calendar": async (request) => {
        calendarAttempts++;
        return calendarAttempts === 1
          ? problem(503, "SERVICE_UNAVAILABLE", "Hệ thống đang bận.")
          : calendar(5, (await request.clone().json()).windows);
      },
    });
    const { onReview } = await renderEdit();
    await screen.findByDisplayValue(GOAL);
    await addSlot("Thứ 4", "18:00", "20:00");

    await press("Xem lại");

    expect(await screen.findByText("Đã lưu nhu cầu, chưa lưu giờ rảnh của con.")).toBeTruthy();
    expect(onReview).not.toHaveBeenCalled();
    await press("Thử lại");

    const puts = writes(server).filter((r) => r.method === "PUT");
    expect(puts).toHaveLength(2);
    expect(puts[1].headers.get("Idempotency-Key")).toBe(puts[0].headers.get("Idempotency-Key"));
    expect(puts[1].headers.get("If-Match")).toBe('"4"');
    // The need did not change, so it was not sent at all.
    expect(writes(server).filter((r) => r.method === "PATCH")).toHaveLength(0);
    expect(onReview).toHaveBeenCalledWith(NEED_ID);
  });

  it("AC-UC04-02 warns that the free time is short of the sessions a week and keeps 'Xem lại' disabled", async () => {
    server = mockApi({
      "GET /students": students,
      "GET /needs/{needId}": () => withEtag(need(2, COMPLETE)),
      "GET /students/{studentId}/calendar": () => calendar(4, MONDAY),
    });
    await renderEdit();
    await screen.findByDisplayValue(GOAL);

    await choose("3 buổi");

    expect(screen.getByText("Giờ rảnh chưa đủ cho 3 buổi 90 phút mỗi tuần. Thêm giờ rảnh hoặc giảm số buổi.")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Xem lại" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Lưu nháp" })).toBeEnabled();
  });

  it("#70 Q2 a calendar that is not a weekly pattern shows Đính's warning before it is replaced", async () => {
    const dayOff: CalendarWindow = { start: "2026-11-21T17:00:00Z", end: "2026-11-22T17:00:00Z", kind: "BLOCKED" };
    server = mockApi({
      "GET /students": students,
      "GET /needs/{needId}": () => withEtag(need(2, COMPLETE)),
      "GET /students/{studentId}/calendar": () => calendar(4, [...MONDAY, dayOff]),
    });
    await renderEdit();

    expect(
      await screen.findByText("Lịch rảnh của con có ngày khác mẫu tuần. Lưu ở đây sẽ thay toàn bộ lịch bằng mẫu tuần dưới đây."),
    ).toBeTruthy();
  });

  it("INV-23 a stale calendar (412) is reloaded, keeping the slots added, and saved against the new ETag", async () => {
    let version = 4;
    server = mockApi({
      "GET /students": students,
      "GET /needs/{needId}": () => withEtag(need(2, COMPLETE)),
      "GET /students/{studentId}/calendar": () => calendar(version, MONDAY),
      "PUT /students/{studentId}/calendar": async (request) =>
        request.headers.get("If-Match") === '"4"'
          ? problem(412, "PRECONDITION_FAILED", "Dữ liệu đã thay đổi.")
          : calendar(6, (await request.clone().json()).windows),
    });
    await renderEdit();
    await screen.findByDisplayValue(GOAL);
    await addSlot("Thứ 4", "18:00", "20:00");
    version = 5;

    await press("Lưu nháp");
    expect(
      await screen.findByText("Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."),
    ).toBeTruthy();

    await press("Tải lại");
    expect(screen.getByRole("button", { name: "Bỏ khung Thứ 4 18:00–20:00" })).toBeTruthy();
    await press("Lưu nháp");

    expect(writes(server).map((r) => r.headers.get("If-Match"))).toEqual(['"4"', '"5"']);
  });

  it("INV-23 a stale need (412) is reloaded, keeping what was typed, and saved against the new ETag", async () => {
    let stored = need(2, COMPLETE);
    server = mockApi({
      "GET /students": students,
      "GET /needs/{needId}": () => withEtag(stored),
      "GET /students/{studentId}/calendar": () => calendar(4, MONDAY),
      "PATCH /needs/{needId}": async (request) =>
        request.headers.get("If-Match") === '"2"'
          ? problem(412, "PRECONDITION_FAILED", "Dữ liệu đã thay đổi.")
          : withEtag(need(4, await request.clone().json())),
    });
    await renderEdit();
    await screen.findByDisplayValue(GOAL);
    await type("Ngân sách tối đa cho một buổi 90 phút (VND)", "500.000");
    stored = need(3, { ...COMPLETE, cycleStartDate: "2026-12-09" });

    await press("Lưu nháp");
    expect(
      await screen.findByText("Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."),
    ).toBeTruthy();
    expect(screen.getByRole("button", { name: "Lưu nháp" })).toBeDisabled();

    await press("Tải lại");
    // The typed budget stays; the untouched date takes the newer revision's value.
    expect(screen.getByDisplayValue("500.000")).toBeTruthy();
    expect(screen.getByDisplayValue("09/12/2026")).toBeTruthy();

    await press("Lưu nháp");
    expect(writes(server).map((r) => r.headers.get("If-Match"))).toEqual(['"2"', '"3"']);
  });

  it("INV-20 after an unknown outcome the retry resends the same key and body", async () => {
    let attempts = 0;
    server = mockApi({
      "GET /students": students,
      "GET /students/{studentId}/calendar": noCalendar,
      "POST /students/{studentId}/needs": async (request) => {
        attempts++;
        return attempts === 1
          ? problem(503, "SERVICE_UNAVAILABLE", "Hệ thống đang bận.")
          : withEtag(need(1, await request.clone().json()), 201);
      },
    });
    await renderNew();
    await screen.findByText("Minh An, lớp 8");
    await type("Mục tiêu học kèm", GOAL);
    await press("Lưu nháp");

    expect(
      await screen.findByText("Chưa rõ kết quả. Yêu cầu có thể đã được ghi nhận. Thử lại sẽ không tạo bản trùng."),
    ).toBeTruthy();
    await press("Thử lại");

    const [first, second] = writes(server);
    expect(second.headers.get("Idempotency-Key")).toBe(first.headers.get("Idempotency-Key"));
    expect(await second.json()).toEqual(await first.json());
    expect(await screen.findByText(/Đã lưu nháp\. Cần thêm/)).toBeTruthy();
  });

  it("shows the server's answer when the child already has an open need (409 NEED_ALREADY_OPEN)", async () => {
    server = mockApi({
      "GET /students": students,
      "GET /students/{studentId}/calendar": noCalendar,
      "POST /students/{studentId}/needs": () =>
        problem(409, "NEED_ALREADY_OPEN", "Con đã có một nhu cầu đang mở. Hãy sửa nhu cầu đó thay vì tạo mới."),
    });
    await renderNew();
    await screen.findByText("Minh An, lớp 8");
    await type("Mục tiêu học kèm", GOAL);
    await press("Lưu nháp");

    expect(await screen.findByText("Con đã có một nhu cầu đang mở. Hãy sửa nhu cầu đó thay vì tạo mới.")).toBeTruthy();
    // No calendar save after a refused need.
    expect(writes(server)).toHaveLength(1);
  });

  it("puts the server's field message next to its field", async () => {
    server = mockApi({
      "GET /students": students,
      "GET /students/{studentId}/calendar": noCalendar,
      "POST /students/{studentId}/needs": () =>
        problem(422, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.", [{ path: "budgetVndSession", message: "Ngân sách quá lớn." }]),
    });
    await renderNew();
    await screen.findByText("Minh An, lớp 8");
    await type("Ngân sách tối đa cho một buổi (VND)", "999.999.999");
    await press("Lưu nháp");

    expect(await screen.findByText("Lỗi: Ngân sách quá lớn.")).toBeTruthy();
  });

  it("INV-06 another family's need is lost access, as an unknown one", async () => {
    server = mockApi({
      "GET /students": students,
      "GET /needs/{needId}": () => problem(403, "ACCESS_UNAVAILABLE", "Tài khoản không thể thực hiện thao tác này."),
    });
    await renderEdit();

    expect(await screen.findByText("Bạn không còn quyền xem nội dung này.")).toBeTruthy();
    expect(screen.queryByLabelText("Mục tiêu học kèm")).toBeNull();
  });
});
