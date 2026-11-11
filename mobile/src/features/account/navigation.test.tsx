import { act, fireEvent, renderRouter, screen, waitFor } from "expo-router/testing-library";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { clearStore, fakeSession, storedSession, storeSession } from "@/shared/test/session";

beforeEach(() => {
  clearStore();
});

// Uses the real route tree in src/app.
describe("account routes", () => {
  it("goes from sign-in to registration and back", async () => {
    await renderRouter("./src/app", { initialUrl: "/sign-in" });
    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Chưa có tài khoản? Đăng ký" })));

    expect(await screen.findByRole("header", { name: "Đăng ký tài khoản" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/register");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đã có tài khoản? Đăng nhập" })));

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/sign-in");
  });

  it("opens registration from a link without a history to go back to", async () => {
    await renderRouter("./src/app", { initialUrl: "/register" });
    expect(await screen.findByRole("header", { name: "Đăng ký tài khoản" })).toBeOnTheScreen();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đã có tài khoản? Đăng nhập" })));

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/sign-in");
  });

  it("sends an unverified account from sign-in to ask for a new verification link", async () => {
    const server = mockApi({
      "POST /auth/login": () => problem(403, "ACCOUNT_UNVERIFIED", "Email của bạn chưa được xác minh."),
    });
    try {
      await renderRouter("./src/app", { initialUrl: "/sign-in" });
      expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
      await fireEvent.changeText(screen.getByLabelText("Email"), "phu.huynh@example.test");
      await fireEvent.changeText(screen.getByLabelText("Mật khẩu"), "mat khau rat bi mat 2026");
      await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đăng nhập" })));

      const resend = await screen.findByRole("button", { name: "Gửi lại liên kết xác minh" });
      await act(async () => fireEvent.press(resend));

      expect(await screen.findByRole("header", { name: "Xác minh email" })).toBeOnTheScreen();
      expect(screen).toHavePathname("/verify-email");
      expect(screen.getByRole("button", { name: "Gửi lại liên kết" })).toBeOnTheScreen();
      // No token here: a neutral lead, not the error for a broken link.
      expect(screen.getByText("Nhập email đã đăng ký để nhận liên kết xác minh mới.")).toBeOnTheScreen();
      expect(screen.queryByText(/Liên kết xác minh không hợp lệ/)).toBeNull();
    } finally {
      server.restore();
    }
  });

  it("goes from sign-in to password recovery and back", async () => {
    await renderRouter("./src/app", { initialUrl: "/sign-in" });
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
    await renderRouter("./src/app", { initialUrl });

    expect(await screen.findByRole("header", { name: header })).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: button })).toBeOnTheScreen();
    expect(screen.queryByText(/không hợp lệ/)).toBeNull();
  });

  it("opens an email link while signed in instead of losing its token (Q8)", async () => {
    storeSession(fakeSession("PARENT"));
    await renderRouter("./src/app", { initialUrl: "/verify-email?token=fake-verification-token-0001" });

    expect(await screen.findByRole("header", { name: "Xác minh email" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/verify-email");
  });

  it("forgets this device's session after a password reset opened while signed in (Q8)", async () => {
    const server = mockApi({ "POST /auth/reset": () => new Response(null, { status: 204 }) });
    try {
      storeSession(fakeSession("TUTOR"));
      await renderRouter("./src/app", { initialUrl: "/reset-password?token=fake-reset-token-000000001" });
      await screen.findByRole("header", { name: "Đặt lại mật khẩu" });
      await fireEvent.changeText(screen.getByLabelText("Mật khẩu mới"), "mot cau mat khau moi du dai");

      await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đặt lại mật khẩu" })));

      expect(await screen.findByRole("header", { name: "Đã đặt lại mật khẩu" })).toBeOnTheScreen();
      await waitFor(() => expect(storedSession()).toBeUndefined());
    } finally {
      server.restore();
    }
  });

  it.each([
    ["PARENT", "/parent/security"],
    ["TUTOR", "/tutor/security"],
  ] as const)("opens session security for a %s at %s", async (role, initialUrl) => {
    const server = mockApi({ "GET /me/sessions": () => json(200, { items: [], nextCursor: null }) });
    try {
      storeSession(fakeSession(role));
      await renderRouter("./src/app", { initialUrl });

      expect(await screen.findByRole("header", { name: "Bảo mật và phiên đăng nhập" })).toBeOnTheScreen();
      expect(screen).toHavePathname(initialUrl);
    } finally {
      server.restore();
    }
  });

  it("goes from session security to change password and back", async () => {
    const server = mockApi({ "GET /me/sessions": () => json(200, { items: [], nextCursor: null }) });
    try {
      storeSession(fakeSession("PARENT"));
      await renderRouter("./src/app", { initialUrl: "/parent/security" });
      // The header shows while the list loads; the button comes with the list.
      const changePassword = await screen.findByRole("button", { name: "Đổi mật khẩu" });
      await act(async () => fireEvent.press(changePassword));

      expect(await screen.findByRole("header", { name: "Đổi mật khẩu" })).toBeOnTheScreen();
      expect(screen).toHavePathname("/parent/change-password");

      await act(async () => fireEvent.press(screen.getByRole("button", { name: "Hủy" })));

      expect(await screen.findByRole("header", { name: "Bảo mật và phiên đăng nhập" })).toBeOnTheScreen();
    } finally {
      server.restore();
    }
  });

  it("sends a signed-out user from session security to sign-in", async () => {
    await renderRouter("./src/app", { initialUrl: "/parent/security" });

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/sign-in");
  });

  it("tells a user whose session ended elsewhere why they are back at sign-in", async () => {
    storeSession(fakeSession("PARENT"));
    const server = mockApi({
      "GET /me/sessions": () => problem(401, "AUTHENTICATION_REQUIRED", "Phiên đăng nhập không còn hiệu lực."),
    });
    try {
      await renderRouter("./src/app", { initialUrl: "/parent/security" });

      expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
      expect(screen).toHavePathname("/sign-in");
      expect(screen.getByText("Phiên đăng nhập đã kết thúc. Vui lòng đăng nhập lại.")).toBeOnTheScreen();
    } finally {
      server.restore();
    }
  });

  it("tells a user who signed out that they did", async () => {
    storeSession(fakeSession("PARENT"));
    const current = {
      id: "00000000-0000-4000-8000-000000000001",
      deviceLabel: "Điện thoại Android",
      lastSeenAt: "2026-11-07T02:00:00Z",
      expiresAt: "2026-11-14T02:00:00Z",
      current: true,
      revoked: false,
    };
    const server = mockApi({
      "GET /me/sessions": () => json(200, { items: [current], nextCursor: null }),
      "POST /auth/logout": () => new Response(null, { status: 204 }),
    });
    try {
      await renderRouter("./src/app", { initialUrl: "/parent/security" });
      const signOut = await screen.findByRole("button", { name: "Đăng xuất" });

      await act(async () => fireEvent.press(signOut));

      expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
      expect(screen.getByText("Bạn đã đăng xuất.")).toBeOnTheScreen();
      expect(screen.queryByText(/Phiên đăng nhập đã kết thúc/)).toBeNull();
    } finally {
      server.restore();
    }
  });

  it("sends a signed-in user away from registration", async () => {
    storeSession(fakeSession("TUTOR"));
    await renderRouter("./src/app", { initialUrl: "/register" });

    expect(await screen.findByRole("header", { name: "Trang chủ gia sư" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/tutor");
  });
});
