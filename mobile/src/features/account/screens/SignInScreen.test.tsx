import { act, fireEvent, screen, waitFor } from "@testing-library/react-native";
import { Text } from "react-native";
import { useSession } from "@/shared/session/SessionProvider";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { storedSession } from "@/shared/test/session";
import { SignInScreen } from "./SignInScreen";

const PASSWORD = "mat khau rat bi mat 2026";
const CREDENTIAL = {
  sessionId: "0b6f3a52-6c1e-4c7e-9a39-1b2f0d6c9a10",
  accessToken: "opaque-token-value-for-tests-only",
  role: "PARENT",
  idleExpiresAt: "2030-11-06T10:00:00Z",
  absoluteExpiresAt: "2030-11-12T10:00:00Z",
};

function SessionStatus() {
  const session = useSession();
  return <Text>{session.status === "signedIn" ? `signed in as ${session.session.role}` : "signed out"}</Text>;
}

async function signIn() {
  await renderScreen(
    <>
      <SignInScreen onGoToRegister={jest.fn()} onForgotPassword={jest.fn()} />
      <SessionStatus />
    </>,
  );
  fireEvent.changeText(screen.getByLabelText("Email"), "phu.huynh@example.test");
  fireEvent.changeText(screen.getByLabelText("Mật khẩu"), PASSWORD);
  await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đăng nhập" })));
}

describe("AUTH03 SignInScreen", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("keeps the session in the secure store, never the password", async () => {
    server = mockApi({ "POST /auth/login": () => json(200, CREDENTIAL) });

    await signIn();

    expect(await screen.findByText("signed in as PARENT")).toBeOnTheScreen();
    expect(await server.requests[0].json()).toEqual({ email: "phu.huynh@example.test", password: PASSWORD });
    const stored = storedSession();
    expect(JSON.parse(stored ?? "null")).toEqual(CREDENTIAL);
    expect(stored).not.toContain(PASSWORD);
    expect(server.unexpected).toEqual([]);
  });

  it("checks the form before sending anything", async () => {
    server = mockApi({});
    await renderScreen(<SignInScreen onGoToRegister={jest.fn()} onForgotPassword={jest.fn()} />);

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đăng nhập" })));

    expect(await screen.findByText("Lỗi: Vui lòng nhập email.")).toBeOnTheScreen();
    expect(screen.getByText("Lỗi: Vui lòng nhập mật khẩu.")).toBeOnTheScreen();
    expect(server.requests).toHaveLength(0);
  });

  it("gives the same message for a wrong password or an unknown email", async () => {
    server = mockApi({
      "POST /auth/login": () => problem(401, "AUTHENTICATION_FAILED", "Không có tài khoản với email này"),
    });

    await signIn();

    expect(await screen.findByText("Email hoặc mật khẩu không đúng.")).toBeOnTheScreen();
    expect(screen.queryByText(/Không có tài khoản/)).toBeNull();
    expect(screen.getByText("signed out")).toBeOnTheScreen();
    expect(screen.getByLabelText("Email")).toHaveDisplayValue("phu.huynh@example.test");
    expect(storedSession()).toBeUndefined();
  });

  it("UC01_shows_the_delay_after_repeated_failures", async () => {
    server = mockApi({
      "POST /auth/login": () => problem(429, "RATE_LIMITED", "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau 15 phút."),
    });

    await signIn();

    expect(await screen.findByText("Bạn đã thử quá nhiều lần. Vui lòng thử lại sau 15 phút.")).toBeOnTheScreen();
    expect(storedSession()).toBeUndefined();
  });

  it("does not let an admin account sign in on the mobile app", async () => {
    server = mockApi({
      "POST /auth/login": () =>
        json(200, { mfaRequired: true, challengeToken: "challenge-token-for-tests", expiresAt: "2030-11-05T10:05:00Z" }),
    });

    await signIn();

    expect(await screen.findByText(/Tài khoản quản trị/)).toBeOnTheScreen();
    expect(screen.getByText("signed out")).toBeOnTheScreen();
    expect(storedSession()).toBeUndefined();
  });

  it("reports an unknown outcome when the network fails, then signs in on retry", async () => {
    let attempts = 0;
    server = mockApi({
      "POST /auth/login": () => {
        attempts += 1;
        if (attempts === 1) throw new TypeError("Network request failed");
        return json(200, CREDENTIAL);
      },
    });

    await signIn();
    expect(await screen.findByText(/Chưa rõ kết quả/)).toBeOnTheScreen();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đăng nhập" })));

    expect(await screen.findByText("signed in as PARENT")).toBeOnTheScreen();
    await waitFor(() => expect(attempts).toBe(2));
  });

  it("AC_UC01_04_password_and_token_never_reach_the_console", async () => {
    const spies = (["log", "info", "warn", "error", "debug"] as const).map((level) =>
      jest.spyOn(console, level).mockImplementation(() => undefined),
    );
    let attempts = 0;
    server = mockApi({
      "POST /auth/login": () => {
        attempts += 1;
        if (attempts === 1) throw new TypeError("Network request failed");
        return json(200, CREDENTIAL);
      },
    });

    await signIn();
    await screen.findByText(/Chưa rõ kết quả/);
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đăng nhập" })));
    await screen.findByText("signed in as PARENT");

    const printed = spies
      .flatMap((spy) => spy.mock.calls)
      .map((args) => args.map(String).join(" "))
      .join("\n");
    spies.forEach((spy) => spy.mockRestore());
    expect(printed).not.toContain(PASSWORD);
    expect(printed).not.toContain(CREDENTIAL.accessToken);
  });
});
