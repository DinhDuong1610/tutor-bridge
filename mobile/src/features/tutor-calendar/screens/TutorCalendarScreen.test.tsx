import { act, fireEvent, screen, waitFor } from "@testing-library/react-native";
import { emptyWeek, spreadWeek, type CalendarWindow, type WeekPattern } from "@/features/calendar/weekly";
import { mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import { TutorCalendarScreen } from "./TutorCalendarScreen";

// 10:00 on Thứ 4 18/11/2026 in Vietnam, as in the approved frames.
const NOW = new Date("2026-11-18T03:00:00Z");
const TODAY = "2026-11-18";
const ID = "5b1d2c3e-4f50-4a6b-8c7d-9e0f1a2b3c4d";

function week(entries: Record<number, [string, string][]>): WeekPattern {
  const result = emptyWeek();
  for (const [day, slots] of Object.entries(entries)) {
    result[Number(day)] = slots.map(([start, end]) => ({ start, end }));
  }
  return result;
}

/** getTutorCalendar and replaceTutorCalendar answer with the strong ETag of the version, as the backend does. */
function calendar(version: number, windows: CalendarWindow[]) {
  return new Response(JSON.stringify({ id: ID, version, timezone: "Asia/Ho_Chi_Minh", windows }), {
    status: 200,
    headers: { "Content-Type": "application/json", ETag: `"${version}"` },
  });
}

const noCalendar = () => problem(404, "CALENDAR_NOT_FOUND", "Chưa có lịch rảnh.");
const MONDAY_EVENING = spreadWeek(week({ 0: [["18:00", "21:00"]] }), emptyWeek(), TODAY);

async function renderCalendar() {
  const onBack = jest.fn();
  await renderScreen(<TutorCalendarScreen onBack={onBack} now={() => NOW} />, { session: fakeSession("TUTOR") });
  return { onBack };
}

const press = (name: string) => act(async () => fireEvent.press(screen.getByRole("button", { name })));
const puts = (server: ReturnType<typeof mockApi>) => server.requests.filter((r) => r.method === "PUT");

async function addSlot(day: string, start: string, end: string) {
  await press(`Thêm giờ, ${day}`);
  await act(async () => fireEvent.changeText(screen.getByLabelText("Từ"), start));
  await act(async () => fireEvent.changeText(screen.getByLabelText("Đến"), end));
  await press("Thêm khung giờ");
}

describe("GS04 Lịch rảnh", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("AC-UC07-01 a first calendar is spread over 56 days and created with If-None-Match: *", async () => {
    let version = 0;
    server = mockApi({
      "GET /me/tutor-calendar": () => (version === 0 ? noCalendar() : calendar(version, MONDAY_EVENING)),
      "PUT /me/tutor-calendar": async (request) => {
        version = 1;
        return calendar(1, (await request.clone().json()).windows);
      },
    });
    await renderCalendar();

    expect(
      await screen.findByText(
        "Bạn chưa có lịch rảnh. Phụ huynh chỉ thấy bạn khi có lịch rảnh đã xác nhận, đủ cho chu kỳ 28 ngày của họ.",
      ),
    ).toBeTruthy();
    expect(screen.getAllByText("Không rảnh")).toHaveLength(7);
    expect(screen.getByRole("button", { name: "Xem lại" })).toBeDisabled();

    await addSlot("Thứ 2", "18:00", "21:00");
    expect(screen.getByText("Bạn có 1 khung giờ chưa xác nhận. Phụ huynh chưa tìm thấy bạn trong các giờ này.")).toBeTruthy();

    await press("Xem lại");
    expect(screen.getByRole("header", { name: "Xem lại lịch rảnh" })).toBeTruthy();
    expect(screen.getByText("Áp dụng từ 18/11/2026 đến 12/01/2027")).toBeTruthy();
    expect(screen.getByText("Mỗi tuần: Thứ 2 18:00–21:00.")).toBeTruthy();
    expect(screen.getByText("Tổng 8 khoảng rảnh trong 8 tuần (tối đa 200).")).toBeTruthy();
    expect(screen.getByText("Buổi đã đặt giữ nguyên. Lịch rảnh mới chỉ dùng cho đề nghị mới.")).toBeTruthy();

    await press("Xác nhận lịch rảnh");

    const [put] = puts(server);
    expect(put.headers.get("If-None-Match")).toBe("*");
    expect(put.headers.get("If-Match")).toBeNull();
    expect(put.headers.get("Idempotency-Key")).toBeTruthy();
    expect(await put.json()).toEqual({ timezone: "Asia/Ho_Chi_Minh", windows: MONDAY_EVENING });
    expect(
      await screen.findByText("Đã xác nhận lịch rảnh. Phụ huynh tìm gia sư sẽ thấy các giờ này."),
    ).toBeTruthy();
    expect(screen.getByRole("header", { name: "Lịch rảnh" })).toBeTruthy();
    expect(screen.queryByText(/khung giờ chưa xác nhận/)).toBeNull();
  });

  it("INV-23 a change to a stored calendar is saved with If-Match, and 'Bỏ thay đổi' goes back to it", async () => {
    server = mockApi({
      "GET /me/tutor-calendar": () => calendar(3, MONDAY_EVENING),
      "PUT /me/tutor-calendar": async (request) => calendar(4, (await request.clone().json()).windows),
    });
    await renderCalendar();

    expect(await screen.findByText("Lịch rảnh lặp theo tuần, áp dụng cho 8 tuần tới, giờ Việt Nam. Sửa lịch rảnh không hủy buổi đã đặt.")).toBeTruthy();
    // Nothing to send while the screen shows what is stored.
    expect(screen.queryByRole("button", { name: "Xem lại" })).toBeNull();

    await addSlot("Thứ 3", "18:00", "20:00");
    await press("Bỏ thay đổi");
    expect(screen.queryByText(/khung giờ chưa xác nhận/)).toBeNull();
    expect(screen.queryByRole("button", { name: "Xem lại" })).toBeNull();

    await addSlot("Thứ 3", "18:00", "20:00");
    await press("Xem lại");
    expect(screen.getByText("Mỗi tuần: Thứ 2 18:00–21:00; Thứ 3 18:00–20:00.")).toBeTruthy();
    await press("Xác nhận lịch rảnh");

    const [put] = puts(server);
    expect(put.headers.get("If-Match")).toBe('"3"');
    expect(put.headers.get("If-None-Match")).toBeNull();
    expect((await put.json()).windows).toHaveLength(16);
  });

  it("removing a confirmed slot is a change to review, with no draft chip to count", async () => {
    server = mockApi({ "GET /me/tutor-calendar": () => calendar(3, MONDAY_EVENING) });
    await renderCalendar();
    await screen.findByText(/Lịch rảnh lặp theo tuần/);

    await press("Bỏ khung Thứ 2 18:00–21:00");

    expect(screen.queryByText(/khung giờ chưa xác nhận/)).toBeNull();
    expect(screen.getByRole("button", { name: "Xem lại" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "Bỏ thay đổi" })).toBeEnabled();
  });

  it("Đính, #263 review: removing the last slot saves a calendar with no free time against its ETag", async () => {
    let stored: { version: number; windows: CalendarWindow[] } = { version: 3, windows: MONDAY_EVENING };
    server = mockApi({
      "GET /me/tutor-calendar": () => calendar(stored.version, stored.windows),
      "PUT /me/tutor-calendar": async (request) => {
        stored = { version: 4, windows: (await request.clone().json()).windows };
        return calendar(4, stored.windows);
      },
    });
    await renderCalendar();
    await screen.findByText(/Lịch rảnh lặp theo tuần/);

    await press("Bỏ khung Thứ 2 18:00–21:00");
    await press("Xem lại");
    expect(screen.getByText("Mỗi tuần: không có giờ rảnh.")).toBeTruthy();
    expect(screen.getByText("Tổng 0 khoảng rảnh trong 8 tuần (tối đa 200).")).toBeTruthy();
    expect(screen.getByText("Sau khi xác nhận, phụ huynh sẽ không tìm thấy bạn cho tới khi bạn thêm lịch rảnh.")).toBeTruthy();
    await press("Xác nhận lịch rảnh");

    const [put] = puts(server);
    expect(put.headers.get("If-Match")).toBe('"3"');
    expect(await put.json()).toEqual({ timezone: "Asia/Ho_Chi_Minh", windows: [] });
    // The screen shows the calendar as the server now holds it: no free time, so parents do not find the tutor.
    expect(
      await screen.findByText(
        "Bạn chưa có lịch rảnh. Phụ huynh chỉ thấy bạn khi có lịch rảnh đã xác nhận, đủ cho chu kỳ 28 ngày của họ.",
      ),
    ).toBeTruthy();
    expect(screen.queryByText("Đã xác nhận lịch rảnh. Phụ huynh tìm gia sư sẽ thấy các giờ này.")).toBeNull();
    expect(screen.getAllByText("Không rảnh")).toHaveLength(7);
    expect(screen.getByRole("button", { name: "Xem lại" })).toBeDisabled();
  });

  it("INV-23 a stale ETag (412) is reloaded, keeping the slots being added, and saved against the new ETag", async () => {
    let version = 3;
    server = mockApi({
      "GET /me/tutor-calendar": () => calendar(version, MONDAY_EVENING),
      "PUT /me/tutor-calendar": async (request) =>
        request.headers.get("If-Match") === '"3"'
          ? problem(412, "PRECONDITION_FAILED", "Dữ liệu đã thay đổi.")
          : calendar(5, (await request.clone().json()).windows),
    });
    await renderCalendar();
    await screen.findByText(/Lịch rảnh lặp theo tuần/);
    await addSlot("Thứ 3", "18:00", "20:00");
    await press("Xem lại");
    version = 4;

    await press("Xác nhận lịch rảnh");
    expect(
      await screen.findByText("Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."),
    ).toBeTruthy();
    expect(screen.getByRole("button", { name: "Xác nhận lịch rảnh" })).toBeDisabled();

    await press("Tải lại");
    expect(await screen.findByRole("header", { name: "Lịch rảnh" })).toBeTruthy();
    expect(screen.getByText("Bạn có 1 khung giờ chưa xác nhận. Phụ huynh chưa tìm thấy bạn trong các giờ này.")).toBeTruthy();

    await press("Xem lại");
    await press("Xác nhận lịch rảnh");
    await screen.findByText("Đã xác nhận lịch rảnh. Phụ huynh tìm gia sư sẽ thấy các giờ này.");
    expect(puts(server).map((r) => r.headers.get("If-Match"))).toEqual(['"3"', '"4"']);
  });

  it("INV-20 after an unknown outcome the retry resends the same key, body and precondition", async () => {
    let attempts = 0;
    server = mockApi({
      "GET /me/tutor-calendar": () => calendar(3, MONDAY_EVENING),
      "PUT /me/tutor-calendar": async (request) => {
        attempts++;
        return attempts === 1
          ? problem(503, "SERVICE_UNAVAILABLE", "Hệ thống đang bận.")
          : calendar(4, (await request.clone().json()).windows);
      },
    });
    await renderCalendar();
    await screen.findByText(/Lịch rảnh lặp theo tuần/);
    await addSlot("Thứ 3", "18:00", "20:00");
    await press("Xem lại");
    await press("Xác nhận lịch rảnh");

    expect(
      await screen.findByText("Chưa rõ kết quả. Yêu cầu có thể đã được ghi nhận. Thử lại sẽ không tạo bản trùng."),
    ).toBeTruthy();
    expect(screen.getByRole("button", { name: "Sửa tiếp" })).toBeDisabled();
    await press("Thử lại");

    await screen.findByText("Đã xác nhận lịch rảnh. Phụ huynh tìm gia sư sẽ thấy các giờ này.");
    const [first, second] = puts(server);
    expect(second.headers.get("Idempotency-Key")).toBe(first.headers.get("Idempotency-Key"));
    expect(second.headers.get("If-Match")).toBe(first.headers.get("If-Match"));
    expect(await second.json()).toEqual(await first.json());
  });

  it("'Sửa tiếp' goes back to the pattern being edited, as the back button does", async () => {
    server = mockApi({ "GET /me/tutor-calendar": () => calendar(3, MONDAY_EVENING) });
    const { onBack } = await renderCalendar();
    await screen.findByText(/Lịch rảnh lặp theo tuần/);
    await addSlot("Thứ 3", "18:00", "20:00");

    await press("Xem lại");
    await press("Sửa tiếp");
    expect(screen.getByText(/Bạn có 1 khung giờ chưa xác nhận/)).toBeTruthy();

    await press("Xem lại");
    await press("Quay lại");
    expect(screen.getByRole("header", { name: "Lịch rảnh" })).toBeTruthy();
    expect(onBack).not.toHaveBeenCalled();
    await press("Quay lại");
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it("AC-UC07-01 warns near 200 windows and blocks the review past them", async () => {
    // 25 slots a week: 200 windows over 8 weeks, the most the server takes.
    const slots: [string, string][] = Array.from({ length: 4 }, (_, i) => [`0${i * 2}:00`.slice(-5), `0${i * 2 + 1}:00`.slice(-5)]);
    const full = week({ 0: slots, 1: slots, 2: slots, 3: slots, 4: slots, 5: slots, 6: [["20:00", "21:00"]] });
    server = mockApi({ "GET /me/tutor-calendar": () => calendar(3, spreadWeek(full, emptyWeek(), TODAY)) });
    await renderCalendar();

    expect(
      await screen.findByText(
        "Lịch này tạo 200 khoảng rảnh trong 8 tuần, gần mức tối đa 200. Gộp các khung sát nhau để thêm được giờ.",
      ),
    ).toBeTruthy();

    await addSlot("Chủ nhật", "21:00", "22:00");
    expect(
      screen.getByText("Lịch này tạo 208 khoảng rảnh trong 8 tuần, vượt mức tối đa 200. Gộp các khung sát nhau hoặc bỏ bớt giờ."),
    ).toBeTruthy();
    expect(screen.getByRole("button", { name: "Xem lại" })).toBeDisabled();
  });

  it("AC-UC07-02 a calendar with a day off says so, and saving keeps the day off (#69 Q2, Q3, Đính 28/11/2026)", async () => {
    const dayOff: CalendarWindow = { start: "2026-11-21T17:00:00Z", end: "2026-11-22T17:00:00Z", kind: "BLOCKED" };
    server = mockApi({
      "GET /me/tutor-calendar": () => calendar(3, [...MONDAY_EVENING, dayOff]),
      "PUT /me/tutor-calendar": async (request) => calendar(4, (await request.clone().json()).windows),
    });
    await renderCalendar();

    expect(
      await screen.findByText(
        "Lịch rảnh của bạn có ngày khác mẫu tuần. Lưu ở đây sẽ thay toàn bộ lịch bằng mẫu tuần dưới đây. Ngày nghỉ và giờ bận đã có được giữ nguyên.",
      ),
    ).toBeTruthy();
    await addSlot("Thứ 3", "18:00", "20:00");
    await press("Xem lại");
    await press("Xác nhận lịch rảnh");

    const [put] = puts(server);
    expect((await put.json()).windows).toContainEqual(dayOff);
  });

  it("FR-37 a failed first read shows the error with a retry, and lost access says so", async () => {
    let answer: () => Response = () => problem(503, "SERVICE_UNAVAILABLE", "Hệ thống đang bận.");
    server = mockApi({ "GET /me/tutor-calendar": () => answer() });
    await renderCalendar();
    expect(await screen.findByText("Không tải được dữ liệu. Kiểm tra kết nối rồi thử lại.", {}, { timeout: 10_000 })).toBeTruthy();

    answer = () => problem(403, "ACCESS_UNAVAILABLE", "Tài khoản không thể thực hiện thao tác này.");
    await press("Thử lại");
    await waitFor(() => expect(screen.getByText("Bạn không còn quyền xem nội dung này.")).toBeTruthy());
    expect(screen.queryByText("Thêm giờ")).toBeNull();
  });

  it("AC-UC07-01 a review left open past midnight sends the 56 days from the new date", async () => {
    let clock = NOW;
    server = mockApi({
      "GET /me/tutor-calendar": () => calendar(3, MONDAY_EVENING),
      "PUT /me/tutor-calendar": async (request) => calendar(4, (await request.clone().json()).windows),
    });
    await renderScreen(<TutorCalendarScreen onBack={jest.fn()} now={() => clock} />, { session: fakeSession("TUTOR") });
    await screen.findByText(/Lịch rảnh lặp theo tuần/);
    await addSlot("Thứ 3", "18:00", "20:00");
    await press("Xem lại");
    // 00:30 on 19/11 in Vietnam: the server now refuses anything before that day.
    clock = new Date("2026-11-18T17:30:00Z");

    await press("Xác nhận lịch rảnh");

    const { windows } = (await puts(server)[0].json()) as { windows: CalendarWindow[] };
    expect(Math.min(...windows.map((w) => Date.parse(w.start)))).toBeGreaterThanOrEqual(Date.parse("2026-11-18T17:00:00Z"));
    expect(Math.max(...windows.map((w) => Date.parse(w.end)))).toBeLessThanOrEqual(Date.parse("2027-01-13T17:00:00Z"));
  });

  it("only the tutor's own calendar is read, from /me (INV-03)", async () => {
    server = mockApi({ "GET /me/tutor-calendar": () => calendar(1, []) });
    await renderCalendar();
    await screen.findAllByText("Không rảnh");
    expect(server.requests.map((r) => `${r.method} ${new URL(r.url).pathname}`)).toEqual(["GET /api/v1/me/tutor-calendar"]);
  });
});
