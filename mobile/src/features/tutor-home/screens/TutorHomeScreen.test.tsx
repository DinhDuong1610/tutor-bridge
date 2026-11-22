import { act, fireEvent, screen } from "@testing-library/react-native";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import { TutorHomeScreen } from "./TutorHomeScreen";

// Fake data only (AGENTS.md).
const NOW = new Date("2026-11-22T03:00:00Z");
const PROFILE = {
  id: "4d2a9c1e-7b3f-4e8a-9c21-5f6e7d8c9b10",
  version: 1,
  revision: 1,
  status: "PENDING",
  description: "Dạy Toán 8 theo từng bước.",
  experienceMonths: 24,
  rateVnd60: 250000,
};
const verification = (state: string, over: Record<string, unknown> = {}) => ({
  id: "5e3b0d2f-8c4a-4f9b-8d32-6a7f8e9d0c21",
  profileVersionId: "6f4c1e3a-9d5b-4a0c-9e43-7b8a9f0e1d32",
  profileVersion: 1,
  state,
  scope: "Toán 8, đại số",
  reason: state === "NEEDS_INFO" || state === "REJECTED" ? "Ảnh bằng tốt nghiệp bị mờ, chưa đọc được tên trường." : null,
  decidedAt: state === "WAITING" ? null : "2026-11-21T08:00:00Z",
  approvedVersion: state === "VERIFIED" ? 1 : null,
  ...over,
});
const CALENDAR = {
  id: "7a5d2f4b-0e6c-4b1d-8f54-8c9b0a1f2e43",
  version: 1,
  timezone: "Asia/Ho_Chi_Minh",
  windows: [{ start: "2026-11-24T11:00:00Z", end: "2026-11-24T13:00:00Z", kind: "AVAILABLE" }],
};
const none = () => problem(404, "NOT_FOUND", "Không tìm thấy");

function serve({ profile, verification: record, calendar }: { profile?: unknown; verification?: unknown; calendar?: unknown }) {
  return mockApi({
    "GET /me/tutor-profile": () => (profile ? json(200, profile) : none()),
    "GET /me/tutor-profile/verification": () => (record ? json(200, record) : none()),
    "GET /me/tutor-calendar": () => (calendar ? json(200, calendar) : none()),
  });
}

async function renderHome() {
  const handlers = { onOpen: jest.fn(), onSecurity: jest.fn() };
  await renderScreen(<TutorHomeScreen {...handlers} now={() => NOW} />, { session: fakeSession("TUTOR") });
  return handlers;
}

const press = (name: string) => act(async () => fireEvent.press(screen.getByRole("button", { name })));

describe("GS01 Trang chủ gia sư", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("frame Mới đăng ký: three steps, each opening its screen, and why parents cannot find the tutor", async () => {
    server = serve({});
    const { onOpen } = await renderHome();

    expect(await screen.findByRole("header", { name: "Tổng quan" })).toBeOnTheScreen();
    expect(screen.getByText("Chào bạn")).toBeOnTheScreen();
    expect(screen.getByRole("header", { name: "Hồ sơ khi phụ huynh tìm" })).toBeOnTheScreen();
    expect(screen.getByText("Hồ sơ của bạn chưa xuất hiện khi phụ huynh tìm gia sư: chưa gửi xác minh và chưa có lịch rảnh.")).toBeOnTheScreen();
    expect(screen.getByRole("header", { name: "Việc cần làm" })).toBeOnTheScreen();
    for (const text of [
      "Bước 1",
      "Hoàn thiện hồ sơ chuyên môn và học phí",
      "Mô tả cách dạy, kinh nghiệm, học phí cho 60 phút.",
      "Bước 2",
      "Tải minh chứng và gửi xác minh",
      "PDF, JPEG hoặc PNG, tối đa 10 tệp.",
      "Bước 3",
      "Xác nhận lịch rảnh",
      "Phụ huynh chỉ thấy bạn khi có khung giờ rảnh đã xác nhận đủ cho chu kỳ 28 ngày.",
    ]) {
      expect(screen.getByText(text)).toBeOnTheScreen();
    }

    await press("Mở hồ sơ");
    await press("Tải minh chứng");
    await press("Nhập lịch rảnh");
    expect(onOpen.mock.calls).toEqual([["profile"], ["evidence"], ["calendar"]]);
  });

  it("INV-02 asks only for the session's own data and nothing for the hidden blocks (Đính, #14 câu 1)", async () => {
    server = serve({});
    await renderHome();
    await screen.findByText("Bước 1");

    const urls = server.requests.map((request) => new URL(request.url));
    expect(urls.map((url) => url.pathname).sort()).toEqual(["/api/v1/me/tutor-calendar", "/api/v1/me/tutor-profile", "/api/v1/me/tutor-profile/verification"]);
    expect(urls.every((url) => url.search === "")).toBe(true);
    expect(server.unexpected).toEqual([]);
    for (const block of ["Buổi sắp tới", "Đề nghị", "Thông báo"]) {
      expect(screen.queryByText(block, { exact: false })).toBeNull();
    }
  });

  it("frame Đang chờ xác minh", async () => {
    server = serve({ profile: PROFILE, verification: verification("WAITING") });
    await renderHome();

    expect(await screen.findByText("Hồ sơ chưa xuất hiện khi phụ huynh tìm: đang chờ xác minh.")).toBeOnTheScreen();
    expect(screen.getByText("Đang chờ xác minh")).toBeOnTheScreen();
    expect(screen.getByText("Hồ sơ đang được xác minh")).toBeOnTheScreen();
    expect(screen.getByText("Bạn sẽ nhận thông báo khi có kết quả.")).toBeOnTheScreen();
    expect(screen.getByText("Bước 3")).toBeOnTheScreen();
    expect(screen.getByText("Xong")).toBeOnTheScreen();
    expect(screen.queryByText("Bước 1")).toBeNull();
    // OP-23: the verifier is never shown.
    expect(screen.queryByText(/người xác minh/i)).toBeNull();
  });

  it("frame Cần bổ sung: the reason verbatim and a way to GS03", async () => {
    server = serve({ profile: PROFILE, verification: verification("NEEDS_INFO"), calendar: CALENDAR });
    const { onOpen } = await renderHome();

    expect(await screen.findByText("Cần bổ sung")).toBeOnTheScreen();
    expect(screen.getByText("Người xác minh cần thêm thông tin")).toBeOnTheScreen();
    expect(screen.getByText("Lý do: Ảnh bằng tốt nghiệp bị mờ, chưa đọc được tên trường.")).toBeOnTheScreen();
    expect(screen.getByText("Hồ sơ chưa xuất hiện khi phụ huynh tìm: cần bổ sung hồ sơ.")).toBeOnTheScreen();
    // The label is a word beside its icon, not color alone; the icon itself is hidden from screen readers.
    expect(screen.getByTestId("icon-warning", { includeHiddenElements: true })).toBeOnTheScreen();
    expect(screen.queryByTestId("icon-warning")).toBeNull();
    await press("Xem và gửi lại");
    expect(onOpen).toHaveBeenCalledWith("evidence");
  });

  it("frame Chưa được duyệt: the reason verbatim and a way to GS03", async () => {
    server = serve({ profile: PROFILE, verification: verification("REJECTED"), calendar: CALENDAR });
    const { onOpen } = await renderHome();

    expect(await screen.findByText("Chưa được duyệt")).toBeOnTheScreen();
    expect(screen.getByText("Hồ sơ chưa được duyệt")).toBeOnTheScreen();
    expect(screen.getByText("Lý do: Ảnh bằng tốt nghiệp bị mờ, chưa đọc được tên trường.")).toBeOnTheScreen();
    expect(screen.getByText("Hồ sơ chưa xuất hiện khi phụ huynh tìm: hồ sơ chưa được duyệt.")).toBeOnTheScreen();
    await press("Sửa hồ sơ và gửi lại");
    expect(onOpen).toHaveBeenCalledWith("evidence");
  });

  it("OP-24 never says \"chưa xuất hiện\" while an approved version stays public (TB-14 Q1)", async () => {
    server = serve({
      profile: { ...PROFILE, version: 2 },
      verification: verification("REJECTED", { profileVersion: 2, approvedVersion: 1 }),
      calendar: CALENDAR,
    });
    await renderHome();

    expect(await screen.findByText("Hồ sơ chưa được duyệt")).toBeOnTheScreen();
    expect(screen.queryByText("Hồ sơ khi phụ huynh tìm")).toBeNull();
    expect(screen.queryByText(/chưa xuất hiện/)).toBeNull();
  });

  it("frame Đang nhận lớp: verified, listed, with the approved rate and self-declared experience", async () => {
    server = serve({ profile: PROFILE, verification: verification("VERIFIED"), calendar: CALENDAR });
    await renderHome();

    expect(await screen.findByText("Đã xác minh: Toán 8, đại số")).toBeOnTheScreen();
    expect(screen.getByText("Phụ huynh có thể thấy và gửi đề nghị cho bạn. Học phí 250.000 ₫ / 60 phút; kinh nghiệm là tự khai.")).toBeOnTheScreen();
    // Offers are hidden until TB-18, so "Việc cần làm" has nothing to show.
    expect(screen.queryByText("Việc cần làm")).toBeNull();
  });

  it("frame Lỗi tải: the shared error with a retry, and AUTH06 still reachable", async () => {
    // Reads retry twice on 5xx with the default backoff before the error shows (as PH01's test).
    let attempts = 0;
    server = mockApi({
      "GET /me/tutor-profile": () => {
        attempts += 1;
        return attempts <= 3 ? problem(503, "UNAVAILABLE", "Dịch vụ tạm thời không sẵn sàng.") : json(200, PROFILE);
      },
      "GET /me/tutor-profile/verification": none,
      "GET /me/tutor-calendar": none,
    });
    const { onSecurity } = await renderHome();

    expect(await screen.findByText("Không tải được dữ liệu. Kiểm tra kết nối rồi thử lại.", {}, { timeout: 10_000 })).toBeOnTheScreen();
    await press("Bảo mật");
    expect(onSecurity).toHaveBeenCalledTimes(1);
    await press("Thử lại");

    expect(await screen.findByText("Bước 2")).toBeOnTheScreen();
  }, 15_000);

  it("shows lost access when the server refuses the tutor area", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => problem(403, "ACCESS_UNAVAILABLE", "Không có quyền"),
      "GET /me/tutor-profile/verification": none,
      "GET /me/tutor-calendar": none,
    });
    await renderHome();

    expect(await screen.findByText("Bạn không còn quyền xem nội dung này.")).toBeOnTheScreen();
    expect(screen.queryByText("Bước 1")).toBeNull();
  });

  it("lets the longest reason wrap instead of cutting it (worst data, 200% text)", async () => {
    const reason = "Minh chứng không khớp môn và cấp học đã đăng ký. ".repeat(30).trim().slice(0, 2000);
    server = serve({ profile: PROFILE, verification: verification("NEEDS_INFO", { reason }) });
    await renderHome();

    const text = await screen.findByText(`Lý do: ${reason}`);
    expect(text).not.toHaveProp("numberOfLines");
    expect(text).not.toHaveProp("maxFontSizeMultiplier");
    expect(screen.getByText("Hồ sơ chưa xuất hiện khi phụ huynh tìm: cần bổ sung hồ sơ.")).not.toHaveProp("numberOfLines");
  });
});
