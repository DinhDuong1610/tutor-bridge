import { act, fireEvent, screen } from "@testing-library/react-native";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { RegisterScreen } from "./RegisterScreen";

const PASSWORD = "mot cau mat khau du dai";
const ACK = { message: "Nếu email hợp lệ, bạn sẽ nhận thư xác minh.", traceId: "0123456789abcdef" };

async function show({ termsVersion }: { termsVersion?: string } = { termsVersion: "2026-11" }) {
  await renderScreen(<RegisterScreen termsVersion={termsVersion} onGoToSignIn={jest.fn()} onResendVerification={jest.fn()} />);
}

async function fillValidForm() {
  await fireEvent.changeText(screen.getByLabelText("Email"), "  phu.huynh@example.test ");
  await fireEvent.changeText(screen.getByLabelText("Tên hiển thị"), "Phụ huynh A");
  await fireEvent.changeText(screen.getByLabelText("Mật khẩu"), PASSWORD);
  await fireEvent.press(screen.getByRole("radio", { name: "Tôi là phụ huynh" }));
  await fireEvent.press(screen.getByRole("checkbox", { name: "Tôi đồng ý với điều khoản sử dụng" }));
}

async function pressRegister() {
  await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đăng ký" })));
}

describe("AUTH01 RegisterScreen", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("AC_UC01_01_offers_only_parent_and_tutor_roles", async () => {
    server = mockApi({});
    await show();

    const roles = screen.getAllByRole("radio").map((node) => node.props.accessibilityLabel);
    expect(roles).toEqual(["Tôi là phụ huynh", "Tôi là gia sư"]);
  });

  it("sends the trimmed form with the chosen role and terms version, then asks to check email", async () => {
    server = mockApi({ "POST /auth/register": () => json(202, ACK) });
    await show();
    await fillValidForm();

    await pressRegister();

    expect(await screen.findByRole("header", { name: "Kiểm tra email của bạn" })).toBeOnTheScreen();
    expect(screen.getByText(ACK.message)).toBeOnTheScreen();
    expect(server.requests).toHaveLength(1);
    expect(await server.requests[0].json()).toEqual({
      email: "phu.huynh@example.test",
      password: PASSWORD,
      displayName: "Phụ huynh A",
      role: "PARENT",
      termsVersion: "2026-11",
    });
    expect(server.unexpected).toEqual([]);
  });

  it("does not send an invalid form and shows errors next to the fields", async () => {
    server = mockApi({});
    await show();

    await pressRegister();

    expect(await screen.findByText("Lỗi: Vui lòng nhập email.")).toBeOnTheScreen();
    expect(screen.getByText("Lỗi: Vui lòng nhập tên hiển thị.")).toBeOnTheScreen();
    expect(screen.getByText("Lỗi: Mật khẩu cần từ 15 đến 128 ký tự.")).toBeOnTheScreen();
    expect(screen.getByText("Lỗi: Vui lòng chọn bạn là phụ huynh hay gia sư.")).toBeOnTheScreen();
    expect(screen.getByText("Lỗi: Bạn cần đồng ý với điều khoản để đăng ký.")).toBeOnTheScreen();
    expect(server.requests).toHaveLength(0);
  });

  it("counts password length in code points like the contract", async () => {
    server = mockApi({ "POST /auth/register": () => json(202, ACK) });
    await show();
    await fillValidForm();
    // 14 emoji are 28 UTF-16 units but only 14 characters: still too short.
    await fireEvent.changeText(screen.getByLabelText("Mật khẩu"), "🙂".repeat(14));

    await pressRegister();

    expect(await screen.findByText("Lỗi: Mật khẩu cần từ 15 đến 128 ký tự.")).toBeOnTheScreen();
    expect(server.requests).toHaveLength(0);
  });

  it("shows server field errors next to the field and keeps what the user typed", async () => {
    server = mockApi({
      "POST /auth/register": () =>
        problem(422, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.", [
          { path: "displayName", message: "Tên không hợp lệ." },
        ]),
    });
    await show();
    await fillValidForm();

    await pressRegister();

    expect(await screen.findByText("Lỗi: Tên không hợp lệ.")).toBeOnTheScreen();
    expect(screen.getByLabelText("Email")).toHaveDisplayValue("  phu.huynh@example.test ");
    expect(screen.getByLabelText("Mật khẩu")).toHaveDisplayValue(PASSWORD);
    expect(screen.queryByRole("header", { name: "Kiểm tra email của bạn" })).toBeNull();
  });

  it("shows the server's message after too many attempts", async () => {
    server = mockApi({
      "POST /auth/register": () => problem(429, "RATE_LIMITED", "Bạn đã thử quá nhiều lần. Vui lòng thử lại sau."),
    });
    await show();
    await fillValidForm();

    await pressRegister();

    expect(await screen.findByText("Bạn đã thử quá nhiều lần. Vui lòng thử lại sau.")).toBeOnTheScreen();
  });

  it("INV_24_network_failure_reports_unknown_outcome_and_allows_retry", async () => {
    let attempts = 0;
    server = mockApi({
      "POST /auth/register": () => {
        attempts += 1;
        if (attempts === 1) throw new TypeError("Network request failed");
        return json(202, ACK);
      },
    });
    await show();
    await fillValidForm();

    await pressRegister();
    expect(await screen.findByText(/Chưa rõ kết quả/)).toBeOnTheScreen();
    expect(screen.queryByRole("header", { name: "Kiểm tra email của bạn" })).toBeNull();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thử lại" })));

    expect(await screen.findByRole("header", { name: "Kiểm tra email của bạn" })).toBeOnTheScreen();
    expect(attempts).toBe(2);
  });

  it("does not send without a configured terms version", async () => {
    server = mockApi({});
    await show({});
    await fillValidForm();

    await pressRegister();

    expect(screen.getByText("Ứng dụng chưa cấu hình phiên bản điều khoản.")).toBeOnTheScreen();
    expect(server.requests).toHaveLength(0);
  });
});
