import { act, fireEvent, screen } from "@testing-library/react-native";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { RecoveryScreen } from "./RecoveryScreen";

// AUTH04 "Đã gửi": the screen's approved text replaces the server's, and is just as conditional.
const SENT =
  "Kiểm tra email của bạn. Nếu email này có tài khoản, chúng tôi đã gửi liên kết đặt lại mật khẩu. Liên kết có hạn 15 phút. Không thấy email? Xem thư mục Spam, hoặc gửi lại sau ít phút.";
const ACK = { message: "Nếu email đã đăng ký, bạn sẽ nhận được liên kết đặt lại mật khẩu.", traceId: "0123456789abcdef" };

async function show() {
  await renderScreen(<RecoveryScreen onGoToSignIn={jest.fn()} />);
}

async function pressSend() {
  await act(async () => fireEvent.press(screen.getByRole("button", { name: "Gửi liên kết" })));
}

describe("AUTH04 RecoveryScreen", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("sends the trimmed email and shows only the approved conditional answer", async () => {
    server = mockApi({ "POST /auth/recovery": () => json(202, ACK) });
    await show();
    await fireEvent.changeText(screen.getByLabelText("Email"), "  gia.su@example.test ");

    await pressSend();

    expect(await screen.findByText(SENT)).toBeOnTheScreen();
    expect(screen.queryByText(ACK.message)).toBeNull();
    expect(await server.requests[0].json()).toEqual({ email: "gia.su@example.test" });
    // Nothing on screen says the account exists or not.
    expect(screen.queryByText(/không tồn tại|đã tìm thấy/i)).toBeNull();
    expect(server.unexpected).toEqual([]);
  });

  it("does not send an invalid email and shows the error next to the field", async () => {
    server = mockApi({});
    await show();
    await fireEvent.changeText(screen.getByLabelText("Email"), "khong-phai-email");

    await pressSend();

    expect(await screen.findByText("Lỗi: Email chưa đúng dạng, ví dụ an@example.com.")).toBeOnTheScreen();
    expect(server.requests).toHaveLength(0);
  });

  it("shows the server's message when sending again is rate limited, keeping the email", async () => {
    server = mockApi({
      "POST /auth/recovery": () => problem(429, "RATE_LIMITED", "Bạn đã yêu cầu quá nhiều lần. Vui lòng thử lại sau."),
    });
    await show();
    await fireEvent.changeText(screen.getByLabelText("Email"), "gia.su@example.test");

    await pressSend();

    expect(await screen.findByText("Bạn đã yêu cầu quá nhiều lần. Vui lòng thử lại sau.")).toBeOnTheScreen();
    expect(screen.getByLabelText("Email")).toHaveDisplayValue("gia.su@example.test");
  });

  it("INV_24_network_failure_reports_unknown_outcome_and_allows_retry", async () => {
    let attempts = 0;
    server = mockApi({
      "POST /auth/recovery": () => {
        attempts += 1;
        if (attempts === 1) throw new TypeError("Network request failed");
        return json(202, ACK);
      },
    });
    await show();
    await fireEvent.changeText(screen.getByLabelText("Email"), "gia.su@example.test");

    await pressSend();
    expect(await screen.findByText(/Chưa rõ kết quả/)).toBeOnTheScreen();
    expect(screen.queryByText(SENT)).toBeNull();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thử lại" })));

    expect(await screen.findByText(SENT)).toBeOnTheScreen();
    expect(attempts).toBe(2);
  });
});
