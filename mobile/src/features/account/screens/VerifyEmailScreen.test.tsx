import { act, fireEvent, screen } from "@testing-library/react-native";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { VerifyEmailScreen } from "./VerifyEmailScreen";

// A fake link token of a valid length; never a real one.
const TOKEN = "fake-verification-token-0001";
const ACK = { message: "Nếu email cần xác minh, bạn sẽ nhận được liên kết mới.", traceId: "0123456789abcdef" };

function noContent() {
  return new Response(null, { status: 204 });
}

async function show(token: unknown) {
  await renderScreen(<VerifyEmailScreen token={token} onGoToSignIn={jest.fn()} />);
}

async function press(name: string) {
  await act(async () => fireEvent.press(screen.getByRole("button", { name })));
}

describe("AUTH02 VerifyEmailScreen", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("does not spend the single-use link just by opening it", async () => {
    server = mockApi({ "POST /auth/verify": noContent });
    await show(TOKEN);

    expect(screen.getByRole("header", { name: "Xác minh email" })).toBeOnTheScreen();
    expect(server.requests).toHaveLength(0);
  });

  it("sends the link token and shows success only after the server confirms", async () => {
    server = mockApi({ "POST /auth/verify": noContent });
    await show(TOKEN);

    await press("Xác minh email");

    expect(await screen.findByRole("header", { name: "Email đã được xác minh" })).toBeOnTheScreen();
    expect(server.requests).toHaveLength(1);
    expect(await server.requests[0].json()).toEqual({ token: TOKEN });
    expect(server.unexpected).toEqual([]);
  });

  it("AC_UC01_02_expired_link_shows_the_server_message_and_offers_a_new_link", async () => {
    server = mockApi({
      "POST /auth/verify": () => problem(400, "LINK_EXPIRED", "Liên kết đã hết hạn."),
    });
    await show(TOKEN);

    await press("Xác minh email");

    expect(await screen.findByText("Liên kết đã hết hạn.")).toBeOnTheScreen();
    expect(screen.queryByRole("header", { name: "Email đã được xác minh" })).toBeNull();
    expect(screen.getByText("Gửi lại liên kết xác minh")).toBeOnTheScreen();
    expect(screen.queryByText(/đã được dùng/)).toBeNull();
    // Only after a retry of an unknown outcome may the first attempt have worked.
    expect(screen.queryByText(/Lần gửi trước có thể đã xác minh/)).toBeNull();
  });

  it("AC_UC01_02_used_link_is_told_apart_from_an_expired_one", async () => {
    server = mockApi({
      "POST /auth/verify": () => problem(409, "LINK_USED", "Liên kết đã được dùng."),
    });
    await show(TOKEN);

    await press("Xác minh email");

    expect(await screen.findByText("Liên kết đã được dùng.")).toBeOnTheScreen();
    expect(screen.getByText("Liên kết này đã được dùng. Nếu email của bạn đã được xác minh, hãy đăng nhập.")).toBeOnTheScreen();
    expect(screen.getByText("Gửi lại liên kết xác minh")).toBeOnTheScreen();
  });

  it.each([[undefined], ["too-short"], [["two", "values"]]])("does not send a missing or malformed token: %p", async (token) => {
    server = mockApi({});
    await show(token);

    expect(screen.getByText(/Liên kết xác minh không hợp lệ/)).toBeOnTheScreen();
    expect(screen.queryByRole("button", { name: "Xác minh email" })).toBeNull();
    expect(screen.getByRole("button", { name: "Gửi lại liên kết" })).toBeOnTheScreen();
    expect(server.requests).toHaveLength(0);
  });

  it("INV_24_network_failure_reports_unknown_outcome_and_a_retry_that_finds_the_link_used_says_it_may_have_worked", async () => {
    let attempts = 0;
    server = mockApi({
      "POST /auth/verify": () => {
        attempts += 1;
        if (attempts === 1) throw new TypeError("Network request failed");
        return problem(409, "LINK_USED", "Liên kết đã được dùng hoặc đã hết hạn.");
      },
    });
    await show(TOKEN);

    await press("Xác minh email");
    expect(await screen.findByText(/Chưa rõ kết quả/)).toBeOnTheScreen();
    expect(screen.queryByRole("header", { name: "Email đã được xác minh" })).toBeNull();

    await press("Thử lại");

    expect(await screen.findByText(/Lần gửi trước có thể đã xác minh email của bạn/)).toBeOnTheScreen();
    expect(attempts).toBe(2);
  });

  it("resends with only the email and shows the server's generic answer", async () => {
    server = mockApi({ "POST /auth/verification-resend": () => json(202, ACK) });
    await show(undefined);
    fireEvent.changeText(screen.getByLabelText("Email"), " phu.huynh@example.test ");

    await press("Gửi lại liên kết");

    expect(await screen.findByText(ACK.message)).toBeOnTheScreen();
    expect(await server.requests[0].json()).toEqual({ email: "phu.huynh@example.test" });
  });

  it("shows the server's message when resending is rate limited", async () => {
    server = mockApi({
      "POST /auth/verification-resend": () => problem(429, "RATE_LIMITED", "Bạn đã yêu cầu quá nhiều lần. Vui lòng thử lại sau."),
    });
    await show(undefined);
    fireEvent.changeText(screen.getByLabelText("Email"), "phu.huynh@example.test");

    await press("Gửi lại liên kết");

    expect(await screen.findByText("Bạn đã yêu cầu quá nhiều lần. Vui lòng thử lại sau.")).toBeOnTheScreen();
  });

  it("AC_UC01_04_does_not_log_the_link_token", async () => {
    const spies = (["log", "info", "warn", "error", "debug"] as const).map((level) => jest.spyOn(console, level));
    server = mockApi({ "POST /auth/verify": noContent });
    try {
      await show(TOKEN);
      await press("Xác minh email");
      await screen.findByRole("header", { name: "Email đã được xác minh" });

      const logged = spies.flatMap((spy) => spy.mock.calls).map((args) => args.map(String).join(" "));
      expect(logged.filter((line) => line.includes(TOKEN))).toEqual([]);
    } finally {
      spies.forEach((spy) => spy.mockRestore());
    }
  });
});
