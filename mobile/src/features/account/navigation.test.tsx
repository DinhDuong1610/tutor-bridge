import { act, fireEvent, renderRouter, screen } from "expo-router/testing-library";
import { json, mockApi } from "@/shared/test/mockApi";
import { clearStore, fakeSession, storeSession } from "@/shared/test/session";

beforeEach(() => {
  clearStore();
});

// Uses the real route tree in src/app.
describe("account routes", () => {
  it("goes from sign-in to registration and back", async () => {
    renderRouter("./src/app", { initialUrl: "/sign-in" });
    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Chưa có tài khoản? Đăng ký" })));

    expect(await screen.findByRole("header", { name: "Đăng ký tài khoản" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/register");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đã có tài khoản? Đăng nhập" })));

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/sign-in");
  });

  it("opens registration from a link without a history to go back to", async () => {
    renderRouter("./src/app", { initialUrl: "/register" });
    expect(await screen.findByRole("header", { name: "Đăng ký tài khoản" })).toBeOnTheScreen();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đã có tài khoản? Đăng nhập" })));

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/sign-in");
  });

  it("goes from sign-in to password recovery and back", async () => {
    renderRouter("./src/app", { initialUrl: "/sign-in" });
    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Quên mật khẩu?" })));

    expect(await screen.findByRole("header", { name: "Khôi phục mật khẩu" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/forgot-password");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Quay lại đăng nhập" })));

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
  });

  it.each([
    ["/verify-email?token=fake-verification-token-0001", "Xác minh email", "Xác minh email"],
    ["/reset-password?token=fake-reset-token-000000001", "Đặt lại mật khẩu", "Đặt lại mật khẩu"],
  ])("opens the email link %s with its token", async (initialUrl, header, button) => {
    renderRouter("./src/app", { initialUrl });

    expect(await screen.findByRole("header", { name: header })).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: button })).toBeOnTheScreen();
    expect(screen.queryByText(/không hợp lệ/)).toBeNull();
  });

  it.each([
    ["PARENT", "/parent/security"],
    ["TUTOR", "/tutor/security"],
  ] as const)("opens session security for a %s at %s", async (role, initialUrl) => {
    const server = mockApi({ "GET /me/sessions": () => json(200, { items: [], nextCursor: null }) });
    try {
      storeSession(fakeSession(role));
      renderRouter("./src/app", { initialUrl });

      expect(await screen.findByRole("header", { name: "Bảo mật và phiên đăng nhập" })).toBeOnTheScreen();
      expect(screen).toHavePathname(initialUrl);
    } finally {
      server.restore();
    }
  });

  it("sends a signed-out user from session security to sign-in", async () => {
    renderRouter("./src/app", { initialUrl: "/parent/security" });

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/sign-in");
  });

  it("sends a signed-in user away from registration", async () => {
    storeSession(fakeSession("TUTOR"));
    renderRouter("./src/app", { initialUrl: "/register" });

    expect(await screen.findByRole("header", { name: "Trang chủ gia sư" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/tutor");
  });
});
