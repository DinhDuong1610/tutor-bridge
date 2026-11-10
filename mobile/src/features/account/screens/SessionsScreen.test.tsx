import { act, fireEvent, screen, waitFor, within } from "@testing-library/react-native";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession, storedSession } from "@/shared/test/session";
import { SessionsScreen } from "./SessionsScreen";

// Fake sessions only.
const CURRENT = {
  id: "00000000-0000-4000-8000-000000000001",
  deviceLabel: "Điện thoại Android",
  lastSeenAt: "2026-11-07T02:00:00Z",
  expiresAt: "2026-11-14T02:00:00Z",
  current: true,
  revoked: false,
};
const OTHER = { ...CURRENT, id: "00000000-0000-4000-8000-000000000002", deviceLabel: "Máy tính bảng", current: false };
const REVOKED = { ...OTHER, id: "00000000-0000-4000-8000-000000000003", deviceLabel: "Máy cũ", revoked: true };

function page(items: unknown[], nextCursor: string | null = null) {
  return json(200, { items, nextCursor });
}

async function show() {
  await renderScreen(<SessionsScreen onChangePassword={jest.fn()} />, { session: fakeSession("PARENT") });
}

async function press(name: string, scope = screen) {
  await act(async () => fireEvent.press(scope.getByRole("button", { name })));
}

describe("AUTH06 SessionsScreen", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("lists own sessions with this device marked, asking for one page within the contract limit", async () => {
    server = mockApi({ "GET /me/sessions": () => page([CURRENT, OTHER, REVOKED]) });
    await show();

    expect(await screen.findByText("Máy này (phiên hiện tại)")).toBeOnTheScreen();
    expect(screen.getByText("Máy tính bảng")).toBeOnTheScreen();
    expect(within(screen.getByLabelText("Máy tính bảng")).getByText("Hoạt động gần nhất: 09:00 07/11/2026")).toBeOnTheScreen();
    const revoked = screen.getByLabelText("Máy cũ");
    expect(within(revoked).getByText("Đã thu hồi")).toBeOnTheScreen();
    expect(within(revoked).queryByRole("button", { name: "Thu hồi phiên" })).toBeNull();
    expect(new URL(server.requests[0].url).searchParams.get("limit")).toBe("50");
    expect(server.unexpected).toEqual([]);
  });

  it("names a session issued before V9, which has an empty device label, as an unknown device", async () => {
    const unnamed = { ...OTHER, deviceLabel: "" };
    server = mockApi({ "GET /me/sessions": () => page([CURRENT, unnamed]) });
    await show();

    const card = await screen.findByLabelText("Thiết bị không rõ");
    expect(within(card).getByText("Thiết bị không rõ")).toBeOnTheScreen();
    await act(async () => fireEvent.press(within(card).getByRole("button", { name: "Thu hồi phiên" })));
    expect(screen.getByText('Thu hồi phiên trên "Thiết bị không rõ"? Máy đó sẽ phải đăng nhập lại.')).toBeOnTheScreen();
  });

  it("loads the next page with the server's cursor", async () => {
    server = mockApi({
      "GET /me/sessions": (request) =>
        new URL(request.url).searchParams.get("cursor") === "next-page" ? page([OTHER]) : page([CURRENT], "next-page"),
    });
    await show();
    await screen.findByText("Máy này (phiên hiện tại)");

    await press("Xem thêm phiên");

    expect(await screen.findByText("Máy tính bảng")).toBeOnTheScreen();
    expect(screen.queryByRole("button", { name: "Xem thêm phiên" })).toBeNull();
  });

  it("asks for confirmation before revoking, and sends nothing on cancel", async () => {
    server = mockApi({ "GET /me/sessions": () => page([CURRENT, OTHER]) });
    await show();
    await screen.findByText("Máy tính bảng");

    await press("Thu hồi phiên");
    expect(screen.getByText(/Máy đó sẽ phải đăng nhập lại/)).toBeOnTheScreen();
    await press("Hủy");

    expect(server.requests.filter((request) => request.method === "DELETE")).toHaveLength(0);
    expect(screen.getByRole("button", { name: "Thu hồi phiên" })).toBeOnTheScreen();
  });

  it("revokes another session after confirmation, then reloads the list", async () => {
    let revoked = false;
    server = mockApi({
      "GET /me/sessions": () => page([CURRENT, { ...OTHER, revoked }]),
      "DELETE /me/sessions/{sessionId}": () => {
        revoked = true;
        return new Response(null, { status: 204 });
      },
    });
    await show();
    await screen.findByText("Máy tính bảng");

    await press("Thu hồi phiên");
    await press("Xác nhận thu hồi");

    expect(await screen.findByText("Đã thu hồi phiên.")).toBeOnTheScreen();
    const deletes = server.requests.filter((request) => request.method === "DELETE");
    expect(deletes).toHaveLength(1);
    expect(new URL(deletes[0].url).pathname).toBe(`/api/v1/me/sessions/${OTHER.id}`);
    await waitFor(() => expect(server.requests.filter((request) => request.method === "GET")).toHaveLength(2));
    expect(await screen.findByText("Đã thu hồi")).toBeOnTheScreen();
  });

  it("shows the server's answer when a session cannot be revoked, without claiming success", async () => {
    server = mockApi({
      "GET /me/sessions": () => page([CURRENT, OTHER]),
      "DELETE /me/sessions/{sessionId}": () => problem(404, "NOT_FOUND", "Không tìm thấy phiên này."),
    });
    await show();
    await screen.findByText("Máy tính bảng");

    await press("Thu hồi phiên");
    await press("Xác nhận thu hồi");

    expect(await screen.findByText("Không tìm thấy phiên này.")).toBeOnTheScreen();
    expect(screen.queryByText("Đã thu hồi phiên.")).toBeNull();
  });

  it("AC_UC01_03_signs_out_on_this_device_only_after_the_server_confirms_logout", async () => {
    server = mockApi({
      "GET /me/sessions": () => page([CURRENT]),
      "POST /auth/logout": () => new Response(null, { status: 204 }),
    });
    await show();
    await screen.findByText("Máy này (phiên hiện tại)");
    expect(storedSession()).toBeDefined();

    await press("Đăng xuất");

    await waitFor(() => expect(storedSession()).toBeUndefined());
    expect(server.requests.filter((request) => request.method === "POST")).toHaveLength(1);
  });

  it("INV_24_keeps_the_session_when_logout_has_an_unknown_outcome_and_retries", async () => {
    let attempts = 0;
    server = mockApi({
      "GET /me/sessions": () => page([CURRENT]),
      "POST /auth/logout": () => {
        attempts += 1;
        if (attempts === 1) throw new TypeError("Network request failed");
        return new Response(null, { status: 204 });
      },
    });
    await show();
    await screen.findByText("Máy này (phiên hiện tại)");

    await press("Đăng xuất");
    expect(await screen.findByText(/Chưa rõ kết quả/)).toBeOnTheScreen();
    expect(storedSession()).toBeDefined();

    await press("Thử lại");

    await waitFor(() => expect(storedSession()).toBeUndefined());
    expect(attempts).toBe(2);
  });

  it("lets the user leave this device when logout cannot reach the server, saying only this device is signed out", async () => {
    server = mockApi({
      "GET /me/sessions": () => page([CURRENT]),
      "POST /auth/logout": () => {
        throw new TypeError("Network request failed");
      },
    });
    await show();
    await screen.findByText("Máy này (phiên hiện tại)");
    expect(screen.queryByRole("button", { name: "Đăng xuất khỏi máy này" })).toBeNull();

    await press("Đăng xuất");
    expect(await screen.findByText(/Phiên trên máy chủ sẽ tự hết hạn/)).toBeOnTheScreen();
    expect(storedSession()).toBeDefined();

    await press("Đăng xuất khỏi máy này");

    await waitFor(() => expect(storedSession()).toBeUndefined());
  });

  it("shows a system error with retry when the list cannot load", async () => {
    server = mockApi({ "GET /me/sessions": () => problem(500, "INTERNAL_ERROR", "Hệ thống đang gặp lỗi.") });
    await show();

    expect(await screen.findByRole("button", { name: "Thử lại" }, { timeout: 5000 })).toBeOnTheScreen();
    expect(screen.queryByText("Máy này (phiên hiện tại)")).toBeNull();
  });
});
