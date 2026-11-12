import { act, fireEvent, screen, waitFor } from "@testing-library/react-native";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import { AddStudentScreen } from "./AddStudentScreen";

// Fake child only (AGENTS.md).
const CREATED = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "Củng cố hằng đẳng thức." };

async function renderForm() {
  const handlers = { onBack: jest.fn(), onSaved: jest.fn() };
  await renderScreen(<AddStudentScreen {...handlers} />, { session: fakeSession("PARENT") });
  return handlers;
}

const save = () => act(async () => fireEvent.press(screen.getByRole("button", { name: "Lưu hồ sơ" })));
const posts = (server: ReturnType<typeof mockApi>) => server.requests.filter((r) => r.method === "POST");

describe("PH02 Hồ sơ các con: thêm hồ sơ con", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("asks only for the name and a short goal; grade 8 is fixed text", async () => {
    server = mockApi({});
    await renderForm();

    expect(screen.getByRole("header", { name: "Thêm hồ sơ con" })).toBeOnTheScreen();
    expect(screen.getByLabelText("Tên hiển thị của con")).toBeOnTheScreen();
    expect(screen.getByLabelText("Mục tiêu ngắn")).toBeOnTheScreen();
    expect(screen.getByText("Lớp 8")).toBeOnTheScreen();
    expect(screen.getByText("Không cần ảnh, ngày sinh, tên trường hay địa chỉ.")).toBeOnTheScreen();
    expect(screen.getByText("0/2.000 ký tự. Có thể để trống và bổ sung sau.")).toBeOnTheScreen();
    // No other inputs: no photo, birth date, school or address (PH02 "Không được").
    expect(screen.getAllByDisplayValue(/^/)).toHaveLength(2);
  });

  it("counts the goal in characters as the server does, an emoji as one", async () => {
    server = mockApi({});
    await renderForm();

    await fireEvent.changeText(screen.getByLabelText("Mục tiêu ngắn"), "Học tốt 🙂");

    expect(screen.getByText("9/2.000 ký tự. Có thể để trống và bổ sung sau.")).toBeOnTheScreen();
  });

  it("checks the name before sending anything", async () => {
    server = mockApi({});
    await renderForm();

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "   ");
    await save();

    expect(await screen.findByText("Lỗi: Nhập tên hiển thị của con (1–100 ký tự).")).toBeOnTheScreen();
    expect(screen.getByText("Vui lòng sửa các ô được đánh dấu rồi gửi lại.")).toBeOnTheScreen();
    expect(posts(server)).toHaveLength(0);
  });

  it("refuses a name over 100 characters and a goal over 2.000 characters", async () => {
    server = mockApi({});
    await renderForm();

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "a".repeat(101));
    await fireEvent.changeText(screen.getByLabelText("Mục tiêu ngắn"), "b".repeat(2001));
    await save();

    expect(await screen.findByText("Lỗi: Nhập tên hiển thị của con (1–100 ký tự).")).toBeOnTheScreen();
    expect(screen.getByText("Lỗi: Mục tiêu ngắn tối đa 2.000 ký tự.")).toBeOnTheScreen();
    expect(posts(server)).toHaveLength(0);
  });

  it("AC-UC02-02 sends the trimmed profile with grade 8, an Idempotency-Key and no owner, then reports the saved id", async () => {
    server = mockApi({ "POST /students": () => json(201, CREATED) });
    const { onSaved } = await renderForm();

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "  Minh An ");
    await fireEvent.changeText(screen.getByLabelText("Mục tiêu ngắn"), " Củng cố hằng đẳng thức. ");
    await save();

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(CREATED.id));
    expect(onSaved).toHaveBeenCalledTimes(1);
    const [request] = posts(server);
    expect(await request.json()).toEqual({ displayName: "Minh An", grade: 8, shortGoal: "Củng cố hằng đẳng thức." });
    expect(request.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f]{32}$/);
    expect(request.headers.get("Authorization")).toBe("Bearer test-access-token-not-a-secret");
    expect(server.unexpected).toEqual([]);
  });

  it("allows an empty goal", async () => {
    server = mockApi({ "POST /students": () => json(201, { ...CREATED, shortGoal: "" }) });
    const { onSaved } = await renderForm();

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh An");
    await save();

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(CREATED.id));
    expect(await posts(server)[0].json()).toEqual({ displayName: "Minh An", grade: 8, shortGoal: "" });
  });

  it("shows sending without claiming success before the server answers", async () => {
    let answer: (response: Response) => void = () => undefined;
    server = mockApi({ "POST /students": () => new Promise<Response>((resolve) => (answer = resolve)) });
    const { onSaved } = await renderForm();

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh An");
    // Not awaited: the press handler only settles once the server answers, which this test holds back.
    void fireEvent.press(screen.getByRole("button", { name: "Lưu hồ sơ" }));

    expect(await screen.findByText("Đang gửi…")).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Lưu hồ sơ" })).toBeDisabled();
    expect(onSaved).not.toHaveBeenCalled();

    await act(async () => answer(json(201, CREATED)));
    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(CREATED.id));
  });

  it("puts a server field error at the field, keeps the input, then sends again with a new key", async () => {
    let attempts = 0;
    server = mockApi({
      "POST /students": () => {
        attempts += 1;
        return attempts === 1
          ? problem(422, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.", [{ path: "displayName", message: "Nhập tên hiển thị của con (1–100 ký tự)." }])
          : json(201, CREATED);
      },
    });
    const { onSaved } = await renderForm();

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh An");
    await fireEvent.changeText(screen.getByLabelText("Mục tiêu ngắn"), "Củng cố hằng đẳng thức.");
    await save();

    expect(await screen.findByText("Lỗi: Nhập tên hiển thị của con (1–100 ký tự).")).toBeOnTheScreen();
    expect(screen.getByText("Vui lòng sửa các ô được đánh dấu rồi gửi lại.")).toBeOnTheScreen();
    expect(screen.getByLabelText("Tên hiển thị của con")).toHaveDisplayValue("Minh An");
    expect(screen.getByLabelText("Mục tiêu ngắn")).toHaveDisplayValue("Củng cố hằng đẳng thức.");
    expect(onSaved).not.toHaveBeenCalled();

    await save();

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(CREATED.id));
    const [first, second] = posts(server).map((r) => r.headers.get("Idempotency-Key"));
    expect(second).not.toBe(first);
  });

  it("shows the server's message for a refusal without fields and keeps the input", async () => {
    server = mockApi({ "POST /students": () => problem(403, "PARENT_NOT_VERIFIED", "Tài khoản cần xác minh email trước.") });
    const { onSaved } = await renderForm();

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh An");
    await save();

    expect(await screen.findByText("Tài khoản cần xác minh email trước.")).toBeOnTheScreen();
    expect(screen.getByLabelText("Tên hiển thị của con")).toHaveDisplayValue("Minh An");
    expect(onSaved).not.toHaveBeenCalled();
  });

  it("retries an unknown outcome with the same key, so no duplicate profile is created", async () => {
    let attempts = 0;
    server = mockApi({
      "POST /students": () => {
        attempts += 1;
        if (attempts === 1) throw new TypeError("Network request failed");
        return json(201, CREATED);
      },
    });
    const { onSaved } = await renderForm();

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh An");
    await save();

    expect(await screen.findByText("Chưa rõ kết quả. Yêu cầu có thể đã được ghi nhận. Thử lại sẽ không tạo bản trùng.")).toBeOnTheScreen();
    expect(onSaved).not.toHaveBeenCalled();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thử lại" })));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(CREATED.id));
    const [first, second] = posts(server).map((r) => r.headers.get("Idempotency-Key"));
    expect(second).toBe(first);
  });

  it("blocks a new save after an unknown outcome, so editing cannot lead to a second profile", async () => {
    // The first attempt reached the server and created the profile, but the answer was lost.
    let attempts = 0;
    server = mockApi({
      "POST /students": () => {
        attempts += 1;
        if (attempts === 1) throw new TypeError("Network request failed");
        return json(201, CREATED);
      },
    });
    const { onSaved } = await renderForm();

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh An");
    await save();
    await screen.findByText(/Chưa rõ kết quả/);

    // The user edits the name and presses save again, even with a field error on screen.
    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "");
    expect(screen.getByRole("button", { name: "Lưu hồ sơ" })).toBeDisabled();
    await save();
    expect(posts(server)).toHaveLength(1);

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh An Nguyễn");
    await save();
    expect(posts(server)).toHaveLength(1);
    expect(screen.getByRole("button", { name: "Thử lại" })).toBeOnTheScreen();

    // Only the retry is possible: same key, same input as the first attempt.
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thử lại" })));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(CREATED.id));
    const sent = posts(server);
    expect(sent).toHaveLength(2);
    expect(sent[1].headers.get("Idempotency-Key")).toBe(sent[0].headers.get("Idempotency-Key"));
    expect(await sent[1].json()).toEqual({ displayName: "Minh An", grade: 8, shortGoal: "" });
  });

  it("allows a corrected save after a known failure, with a new key", async () => {
    let attempts = 0;
    server = mockApi({
      "POST /students": () => {
        attempts += 1;
        return attempts === 1 ? problem(429, "RATE_LIMITED", "Bạn thao tác quá nhanh. Vui lòng đợi một lúc rồi thử lại.") : json(201, CREATED);
      },
    });
    const { onSaved } = await renderForm();

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh An");
    await save();
    await screen.findByText("Bạn thao tác quá nhanh. Vui lòng đợi một lúc rồi thử lại.");
    expect(screen.getByRole("button", { name: "Lưu hồ sơ" })).toBeEnabled();

    await save();

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(CREATED.id));
    const [first, second] = posts(server).map((r) => r.headers.get("Idempotency-Key"));
    expect(second).not.toBe(first);
  });

  it("goes back from the top bar without sending", async () => {
    server = mockApi({});
    const { onBack } = await renderForm();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Quay lại" })));

    expect(onBack).toHaveBeenCalledTimes(1);
    expect(server.requests).toHaveLength(0);
  });
});
