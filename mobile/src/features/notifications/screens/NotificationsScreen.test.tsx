import { act, fireEvent, screen, waitFor } from "@testing-library/react-native";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import type { Notification } from "../api";
import { NotificationsScreen } from "./NotificationsScreen";

// COM01 (UC15, FR-41). Event types are those #237 delivers.
const note = (id: string, eventType: string, createdAt: string, read = false): Notification => ({
  id,
  eventType,
  targetType: eventType === "CLASS_CREATED" ? "CLASS" : "OFFER",
  targetId: "9f0e8d7c-6b5a-4c3d-8e2f-1a0b9c8d7e6f",
  targetVersion: "1",
  createdAt,
  read,
});

const REPLIED = note("0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", "OFFER_REJECTED", "2026-11-18T02:15:00Z");
const CLASS = note("1c8d5a4f-3b62-4b9f-8b54-7e2a1d3c0f21", "CLASS_CREATED", "2026-11-17T14:02:00Z");
const EXPIRED = note("2d9e6b50-4c73-4caf-9c65-8f3b2e4d1032", "OFFER_EXPIRED", "2026-11-15T11:00:00Z", true);

const page = (items: Notification[], nextCursor: string | null = null) => json(200, { items, nextCursor });
const markedRead = (item: Notification) => json(200, { ...item, read: true });

const press = (name: string) => act(async () => fireEvent.press(screen.getByRole("button", { name })));
const posts = (server: ReturnType<typeof mockApi>) => server.requests.filter((r) => r.method === "POST");

describe("COM01 Hộp thông báo", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  const renderInbox = (role: "PARENT" | "TUTOR" = "PARENT") =>
    renderScreen(<NotificationsScreen role={role} />, { session: fakeSession(role) });

  it("shows the parent's notifications newest first, with words for unread, not colour alone", async () => {
    server = mockApi({ "GET /me/notifications": () => page([REPLIED, CLASS, EXPIRED]) });
    await renderInbox("PARENT");

    expect(await screen.findByText("Gia sư đã trả lời đề nghị của bạn")).toBeOnTheScreen();
    // 24-hour Vietnam time, as in Figma "Chưa đọc, 09:15 18/11/2026".
    expect(screen.getByText("Chưa đọc, 09:15 18/11/2026")).toBeOnTheScreen();
    expect(screen.getByText("Chưa đọc, 21:02 17/11/2026")).toBeOnTheScreen();
    expect(screen.getByText("Đã đọc, 18:00 15/11/2026")).toBeOnTheScreen();
    expect(screen.getByText("Một đề nghị của bạn đã hết hạn")).toBeOnTheScreen();
    // The server's order is kept.
    expect(screen.getAllByText(/^(Chưa đọc|Đã đọc), /).map((node) => node.props.children)).toEqual([
      "Chưa đọc, 09:15 18/11/2026",
      "Chưa đọc, 21:02 17/11/2026",
      "Đã đọc, 18:00 15/11/2026",
    ]);
    const url = new URL(server.requests[0].url);
    expect(url.searchParams.get("limit")).toBe("50");
    expect(url.searchParams.has("cursor")).toBe(false);
    expect(server.unexpected).toEqual([]);
  });

  it("uses the tutor's wording, with temporary words where Figma has none (TBD on #74)", async () => {
    server = mockApi({
      "GET /me/notifications": () =>
        page([
          note("3e0f7c61-5d84-4dba-8d76-903c3f5e2143", "OFFER_SENT", "2026-11-18T11:00:00Z"),
          note("4f108d72-6e95-4ecb-9e87-a14d406f3254", "OFFER_CANCELLED", "2026-11-16T13:15:00Z", true),
          note("50219e83-7fa6-4fdc-8f98-b25e5170436a", "CLASS_CREATED", "2026-11-12T14:02:00Z", true),
          note("61320f94-80b7-4aed-90a9-c36f6281547b", "OFFER_EXPIRED", "2026-11-11T01:00:00Z", true),
          note("72431fa5-91c8-4bfe-a1ba-d47073925680", "SOMETHING_NEW", "2026-11-10T01:30:00Z", true),
        ]),
    });
    await renderInbox("TUTOR");

    expect(await screen.findByText("Bạn có đề nghị nhận lớp mới")).toBeOnTheScreen();
    expect(screen.getByText("Phụ huynh đã hủy một đề nghị")).toBeOnTheScreen();
    expect(screen.getByText("Lớp học đã được chốt")).toBeOnTheScreen();
    expect(screen.getByText("Một đề nghị đã hết hạn")).toBeOnTheScreen();
    expect(screen.getByText("Có cập nhật mới")).toBeOnTheScreen();
  });

  it("shows the empty state", async () => {
    server = mockApi({ "GET /me/notifications": () => page([]) });
    await renderInbox();

    expect(await screen.findByText("Chưa có thông báo. Phản hồi đề nghị và lớp đã chốt sẽ hiện ở đây.")).toBeOnTheScreen();
  });

  it("shows lost access when the inbox cannot be read", async () => {
    server = mockApi({ "GET /me/notifications": () => problem(403, "ACCESS_UNAVAILABLE", "Không có quyền") });
    await renderInbox();

    expect(await screen.findByText("Bạn không còn quyền xem nội dung này.")).toBeOnTheScreen();
  });

  it("shows the load error with a retry that reads the inbox again", async () => {
    // Reads retry twice on 5xx with the default backoff before the error shows.
    let attempts = 0;
    server = mockApi({
      "GET /me/notifications": () => {
        attempts += 1;
        return attempts <= 3 ? problem(503, "UNAVAILABLE", "Dịch vụ tạm thời không sẵn sàng.") : page([REPLIED]);
      },
    });
    await renderInbox();

    expect(await screen.findByText("Không tải được dữ liệu. Kiểm tra kết nối rồi thử lại.", {}, { timeout: 10_000 })).toBeOnTheScreen();
    await press("Thử lại");

    expect(await screen.findByText("Gia sư đã trả lời đề nghị của bạn")).toBeOnTheScreen();
  }, 15_000);

  it("loads the next page with the cursor and appends it", async () => {
    server = mockApi({
      "GET /me/notifications": (request) =>
        new URL(request.url).searchParams.get("cursor") === "page-2" ? page([EXPIRED]) : page([REPLIED, CLASS], "page-2"),
    });
    await renderInbox();
    expect(await screen.findByText("Đang hiện 2 thông báo mới nhất.")).toBeOnTheScreen();

    await press("Xem thêm");

    expect(await screen.findByText("Một đề nghị của bạn đã hết hạn")).toBeOnTheScreen();
    expect(screen.getByText("Gia sư đã trả lời đề nghị của bạn")).toBeOnTheScreen();
    expect(screen.queryByRole("button", { name: "Xem thêm" })).toBeNull();
    expect(screen.queryByText(/^Đang hiện/)).toBeNull();
    expect(new URL(server.requests[1].url).searchParams.get("cursor")).toBe("page-2");
  });

  it("marks an unread notification read only after the server confirms", async () => {
    let confirm: () => void = () => undefined;
    server = mockApi({
      "GET /me/notifications": () => page([REPLIED, CLASS]),
      "POST /notifications/{notificationId}/read": () =>
        new Promise<Response>((resolve) => (confirm = () => resolve(markedRead(REPLIED)))),
    });
    await renderInbox();
    await screen.findByText("Gia sư đã trả lời đề nghị của bạn");

    await press("Gia sư đã trả lời đề nghị của bạn, Chưa đọc, 09:15 18/11/2026");

    expect(await screen.findByText("Đang gửi…")).toBeOnTheScreen();
    expect(screen.getByText("Chưa đọc, 09:15 18/11/2026")).toBeOnTheScreen();

    await act(async () => confirm());

    expect(await screen.findByText("Đã đọc, 09:15 18/11/2026")).toBeOnTheScreen();
    expect(screen.queryByText("Đang gửi…")).toBeNull();
    // The other notification is untouched, and no success message is invented.
    expect(screen.getByText("Chưa đọc, 21:02 17/11/2026")).toBeOnTheScreen();
    const [post] = posts(server);
    expect(new URL(post.url).pathname).toBe(`/api/v1/notifications/${REPLIED.id}/read`);
    expect(post.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f]{32}$/);
    expect(server.unexpected).toEqual([]);
  });

  it("does not send anything for a notification already read", async () => {
    server = mockApi({ "GET /me/notifications": () => page([EXPIRED]) });
    await renderInbox();
    await screen.findByText("Một đề nghị của bạn đã hết hạn");

    expect(screen.queryByRole("button", { name: /Một đề nghị của bạn đã hết hạn/ })).toBeNull();
    await act(async () => fireEvent.press(screen.getByText("Một đề nghị của bạn đã hết hạn")));

    expect(posts(server)).toHaveLength(0);
  });

  it.each([
    [404, "NOTIFICATION_NOT_FOUND"],
    [403, "ACCESS_UNAVAILABLE"],
  ])("shows lost access when marking gets %i, and keeps the item unread (AC-UC15-03)", async (status, code) => {
    server = mockApi({
      "GET /me/notifications": () => page([REPLIED]),
      "POST /notifications/{notificationId}/read": () => problem(status, code, "Không tìm thấy thông báo."),
    });
    await renderInbox();
    await screen.findByText("Gia sư đã trả lời đề nghị của bạn");

    await press("Gia sư đã trả lời đề nghị của bạn, Chưa đọc, 09:15 18/11/2026");

    expect(await screen.findByText("Bạn không còn quyền xem nội dung này.")).toBeOnTheScreen();
    expect(screen.getByText("Chưa đọc, 09:15 18/11/2026")).toBeOnTheScreen();
  });

  it.each([
    ["a lost connection", () => Promise.reject(new TypeError("Network request failed"))],
    ["a 5xx", () => problem(503, "UNAVAILABLE", "Dịch vụ tạm thời không sẵn sàng.")],
  ])("treats %s as an unknown outcome and retries with the same key", async (_name, firstAnswer) => {
    let attempts = 0;
    server = mockApi({
      "GET /me/notifications": () => page([REPLIED]),
      "POST /notifications/{notificationId}/read": () => {
        attempts += 1;
        return attempts === 1 ? firstAnswer() : markedRead(REPLIED);
      },
    });
    await renderInbox();
    await screen.findByText("Gia sư đã trả lời đề nghị của bạn");

    await press("Gia sư đã trả lời đề nghị của bạn, Chưa đọc, 09:15 18/11/2026");
    expect(await screen.findByText(/Chưa rõ kết quả/)).toBeOnTheScreen();
    expect(screen.getByText("Chưa đọc, 09:15 18/11/2026")).toBeOnTheScreen();

    await press("Thử lại");

    expect(await screen.findByText("Đã đọc, 09:15 18/11/2026")).toBeOnTheScreen();
    const keys = posts(server).map((r) => r.headers.get("Idempotency-Key"));
    expect(keys).toHaveLength(2);
    expect(keys[1]).toBe(keys[0]);
  });

  it("retries with the same key when the same notification is pressed again after an unknown outcome", async () => {
    let attempts = 0;
    server = mockApi({
      "GET /me/notifications": () => page([REPLIED]),
      "POST /notifications/{notificationId}/read": () => {
        attempts += 1;
        return attempts === 1 ? Promise.reject(new TypeError("Network request failed")) : markedRead(REPLIED);
      },
    });
    await renderInbox();
    await screen.findByText("Gia sư đã trả lời đề nghị của bạn");

    await press("Gia sư đã trả lời đề nghị của bạn, Chưa đọc, 09:15 18/11/2026");
    await screen.findByText(/Chưa rõ kết quả/);
    await press("Gia sư đã trả lời đề nghị của bạn, Chưa đọc, 09:15 18/11/2026");

    expect(await screen.findByText("Đã đọc, 09:15 18/11/2026")).toBeOnTheScreen();
    const keys = posts(server).map((r) => r.headers.get("Idempotency-Key"));
    expect(keys[1]).toBe(keys[0]);
  });

  it("uses a new key for another notification after an unknown outcome, so it is not rejected as reused", async () => {
    server = mockApi({
      "GET /me/notifications": () => page([REPLIED, CLASS]),
      "POST /notifications/{notificationId}/read": (request) =>
        request.url.includes(REPLIED.id) ? Promise.reject(new TypeError("Network request failed")) : markedRead(CLASS),
    });
    await renderInbox();
    await screen.findByText("Gia sư đã trả lời đề nghị của bạn");

    await press("Gia sư đã trả lời đề nghị của bạn, Chưa đọc, 09:15 18/11/2026");
    await screen.findByText(/Chưa rõ kết quả/);
    await press("Lớp học đã được chốt, Chưa đọc, 21:02 17/11/2026");

    expect(await screen.findByText("Đã đọc, 21:02 17/11/2026")).toBeOnTheScreen();
    await waitFor(() => expect(screen.queryByText(/Chưa rõ kết quả/)).toBeNull());
    const keys = posts(server).map((r) => r.headers.get("Idempotency-Key"));
    expect(keys).toHaveLength(2);
    expect(keys[1]).not.toBe(keys[0]);
  });
});
