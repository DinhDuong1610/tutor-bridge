import { act, fireEvent, screen, waitFor } from "@testing-library/react-native";
import { mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession, storedSession } from "@/shared/test/session";
import { ChangePasswordScreen } from "./ChangePasswordScreen";

// Fake values only.
const CURRENT = "mat khau hien tai cua toi";
const NEXT = "mot cau mat khau moi du dai";

async function show() {
  await renderScreen(<ChangePasswordScreen onCancel={jest.fn()} />, { session: fakeSession("TUTOR") });
}

async function fill(current = CURRENT, next = NEXT) {
  await fireEvent.changeText(screen.getByLabelText("Mật khẩu hiện tại"), current);
  await fireEvent.changeText(screen.getByLabelText("Mật khẩu mới"), next);
}

async function pressChange() {
  await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đổi mật khẩu" })));
}

describe("AUTH06 ChangePasswordScreen", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("sends the current and new password, then asks to sign in again because every session ended", async () => {
    server = mockApi({ "POST /me/password": () => new Response(null, { status: 204 }) });
    await show();
    await fill();

    await pressChange();

    expect(await screen.findByRole("header", { name: "Đã đổi mật khẩu" })).toBeOnTheScreen();
    expect(await server.requests[0].json()).toEqual({ currentPassword: CURRENT, newPassword: NEXT });
    expect(server.requests[0].headers.get("Authorization")).toMatch(/^Bearer /);

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đăng nhập lại" })));
    await waitFor(() => expect(storedSession()).toBeUndefined());
  });

  it("does not send without the current password or with a short new password", async () => {
    server = mockApi({});
    await show();
    await fill("", "ngan");

    await pressChange();

    expect(await screen.findByText("Lỗi: Vui lòng nhập mật khẩu hiện tại.")).toBeOnTheScreen();
    expect(screen.getByText("Lỗi: Mật khẩu cần từ 15 đến 128 ký tự.")).toBeOnTheScreen();
    expect(server.requests).toHaveLength(0);
  });

  it("shows a wrong current password next to the field and keeps the session", async () => {
    server = mockApi({
      "POST /me/password": () =>
        problem(422, "CURRENT_PASSWORD_INVALID", "Dữ liệu chưa hợp lệ.", [
          { path: "currentPassword", message: "Mật khẩu hiện tại không đúng." },
        ]),
    });
    await show();
    await fill();

    await pressChange();

    expect(await screen.findByText("Lỗi: Mật khẩu hiện tại không đúng.")).toBeOnTheScreen();
    expect(screen.getByLabelText("Mật khẩu mới")).toHaveDisplayValue(NEXT);
    expect(storedSession()).toBeDefined();
  });

  it("INV_24_network_failure_reports_unknown_outcome_without_claiming_the_change", async () => {
    server = mockApi({
      "POST /me/password": () => {
        throw new TypeError("Network request failed");
      },
    });
    await show();
    await fill();

    await pressChange();

    expect(await screen.findByText(/Chưa rõ kết quả/)).toBeOnTheScreen();
    expect(screen.queryByRole("header", { name: "Đã đổi mật khẩu" })).toBeNull();
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeOnTheScreen();
  });

  it("AC_UC01_04_does_not_log_either_password", async () => {
    const spies = (["log", "info", "warn", "error", "debug"] as const).map((level) => jest.spyOn(console, level));
    server = mockApi({ "POST /me/password": () => new Response(null, { status: 204 }) });
    try {
      await show();
      await fill();
      await pressChange();
      await screen.findByRole("header", { name: "Đã đổi mật khẩu" });

      const logged = spies.flatMap((spy) => spy.mock.calls).map((args) => args.map(String).join(" "));
      expect(logged.filter((line) => line.includes(CURRENT) || line.includes(NEXT))).toEqual([]);
    } finally {
      spies.forEach((spy) => spy.mockRestore());
    }
  });
});
