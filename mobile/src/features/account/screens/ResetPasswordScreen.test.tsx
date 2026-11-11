import { act, fireEvent, screen } from "@testing-library/react-native";
import { mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { ResetPasswordScreen } from "./ResetPasswordScreen";

// A fake link token of a valid length; never a real one.
const TOKEN = "fake-reset-token-000000001";
const PASSWORD = "mot cau mat khau moi du dai";

function noContent() {
  return new Response(null, { status: 204 });
}

async function show(token: unknown = TOKEN, onRequestNewLink = jest.fn()) {
  await renderScreen(<ResetPasswordScreen token={token} onGoToSignIn={jest.fn()} onRequestNewLink={onRequestNewLink} />);
  return onRequestNewLink;
}

async function pressReset() {
  await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đặt lại mật khẩu" })));
}

describe("AUTH05 ResetPasswordScreen", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("sends the link token with the new password and says old sessions were signed out", async () => {
    server = mockApi({ "POST /auth/reset": noContent });
    await show();
    await fireEvent.changeText(screen.getByLabelText("Mật khẩu mới"), PASSWORD);

    await pressReset();

    expect(await screen.findByRole("header", { name: "Đã đặt lại mật khẩu" })).toBeOnTheScreen();
    expect(screen.getByText(/Mọi phiên đăng nhập cũ đã bị đăng xuất/)).toBeOnTheScreen();
    expect(await server.requests[0].json()).toEqual({ token: TOKEN, newPassword: PASSWORD });
    expect(server.unexpected).toEqual([]);
  });

  it("checks the password length in code points before sending", async () => {
    server = mockApi({});
    await show();
    await fireEvent.changeText(screen.getByLabelText("Mật khẩu mới"), "🙂".repeat(14));

    await pressReset();

    expect(await screen.findByText("Lỗi: Mật khẩu cần từ 15 đến 128 ký tự.")).toBeOnTheScreen();
    expect(server.requests).toHaveLength(0);
  });

  it("AC_UC01_02_used_link_is_told_apart_from_an_expired_one", async () => {
    server = mockApi({
      "POST /auth/reset": () => problem(409, "LINK_USED", "Liên kết đã được dùng."),
    });
    await show();
    await fireEvent.changeText(screen.getByLabelText("Mật khẩu mới"), PASSWORD);

    await pressReset();

    expect(await screen.findByText("Liên kết đã được dùng.")).toBeOnTheScreen();
    expect(
      screen.getByText("Liên kết này đã được dùng. Nếu bạn vừa đặt lại mật khẩu, hãy đăng nhập bằng mật khẩu mới."),
    ).toBeOnTheScreen();
  });

  it("AC_UC01_02_expired_link_is_reported_and_offers_a_new_link", async () => {
    server = mockApi({
      "POST /auth/reset": () => problem(400, "LINK_EXPIRED", "Liên kết đã hết hạn."),
    });
    const onRequestNewLink = await show();
    await fireEvent.changeText(screen.getByLabelText("Mật khẩu mới"), PASSWORD);

    await pressReset();

    expect(await screen.findByText("Liên kết đã hết hạn.")).toBeOnTheScreen();
    expect(screen.queryByText(/Liên kết này đã được dùng/)).toBeNull();
    expect(screen.queryByRole("header", { name: "Đã đặt lại mật khẩu" })).toBeNull();
    expect(screen.getByLabelText("Mật khẩu mới")).toHaveDisplayValue(PASSWORD);
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Yêu cầu liên kết mới" })));
    expect(onRequestNewLink).toHaveBeenCalledTimes(1);
  });

  it("shows a server field error next to the password", async () => {
    server = mockApi({
      "POST /auth/reset": () =>
        problem(422, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.", [{ path: "newPassword", message: "Mật khẩu quá phổ biến." }]),
    });
    await show();
    await fireEvent.changeText(screen.getByLabelText("Mật khẩu mới"), PASSWORD);

    await pressReset();

    expect(await screen.findByText("Lỗi: Mật khẩu quá phổ biến.")).toBeOnTheScreen();
  });

  it("does not show the form for a missing or malformed token", async () => {
    server = mockApi({});
    await show("short");

    expect(screen.getByText(/Liên kết đặt lại mật khẩu không hợp lệ/)).toBeOnTheScreen();
    expect(screen.queryByLabelText("Mật khẩu mới")).toBeNull();
    expect(screen.getByRole("button", { name: "Yêu cầu liên kết mới" })).toBeOnTheScreen();
    expect(server.requests).toHaveLength(0);
  });

  it("INV_24_unknown_outcome_then_used_link_says_the_password_may_have_changed", async () => {
    let attempts = 0;
    server = mockApi({
      "POST /auth/reset": () => {
        attempts += 1;
        if (attempts === 1) throw new TypeError("Network request failed");
        return problem(409, "LINK_USED", "Liên kết đã được dùng hoặc đã hết hạn.");
      },
    });
    await show();
    await fireEvent.changeText(screen.getByLabelText("Mật khẩu mới"), PASSWORD);

    await pressReset();
    expect(await screen.findByText(/Chưa rõ kết quả/)).toBeOnTheScreen();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thử lại" })));

    expect(await screen.findByText(/Lần gửi trước có thể đã đổi mật khẩu/)).toBeOnTheScreen();
    expect(attempts).toBe(2);
  });

  it("AC_UC01_04_does_not_log_the_password_or_the_link_token", async () => {
    const spies = (["log", "info", "warn", "error", "debug"] as const).map((level) => jest.spyOn(console, level));
    server = mockApi({ "POST /auth/reset": noContent });
    try {
      await show();
      await fireEvent.changeText(screen.getByLabelText("Mật khẩu mới"), PASSWORD);
      await pressReset();
      await screen.findByRole("header", { name: "Đã đặt lại mật khẩu" });

      const logged = spies.flatMap((spy) => spy.mock.calls).map((args) => args.map(String).join(" "));
      expect(logged.filter((line) => line.includes(TOKEN) || line.includes(PASSWORD))).toEqual([]);
    } finally {
      spies.forEach((spy) => spy.mockRestore());
    }
  });
});
