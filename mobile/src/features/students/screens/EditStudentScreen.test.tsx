import { act, fireEvent, screen, waitFor } from "@testing-library/react-native";
import { mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import { EditStudentScreen } from "./EditStudentScreen";

// Fake child only (AGENTS.md).
const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 3, shortGoal: "Củng cố hằng đẳng thức." };
const PATH = "/students/{studentId}";

/** getStudentProfile answers with the strong ETag of the revision, as the backend does. */
function profile(student: typeof MINH, etag = `"${student.revision}"`) {
  return new Response(JSON.stringify(student), { status: 200, headers: { "Content-Type": "application/json", ETag: etag } });
}

function saved(student: typeof MINH) {
  return new Response(JSON.stringify(student), {
    status: 200,
    headers: { "Content-Type": "application/json", ETag: `"${student.revision}"` },
  });
}

async function renderEdit() {
  const handlers = { onBack: jest.fn(), onSaved: jest.fn() };
  await renderScreen(<EditStudentScreen studentId={MINH.id} {...handlers} />, { session: fakeSession("PARENT") });
  return handlers;
}

const saveButton = () => screen.getByRole("button", { name: "Lưu thay đổi" });
const save = () => act(async () => fireEvent.press(saveButton()));
const patches = (server: ReturnType<typeof mockApi>) => server.requests.filter((r) => r.method === "PATCH");

describe("PH02 Hồ sơ các con: sửa hồ sơ", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("shows the saved profile and has nothing to save until a field changes", async () => {
    server = mockApi({ [`GET ${PATH}`]: () => profile(MINH) });
    await renderEdit();

    expect(await screen.findByDisplayValue("Minh An")).toBeOnTheScreen();
    expect(screen.getByRole("header", { name: "Sửa hồ sơ" })).toBeOnTheScreen();
    expect(screen.getByLabelText("Mục tiêu ngắn")).toHaveDisplayValue("Củng cố hằng đẳng thức.");
    expect(screen.getByText("Lớp 8")).toBeOnTheScreen();
    expect(saveButton()).toBeDisabled();

    // Spaces alone are not a change: the server would store the same trimmed name.
    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), " Minh An ");
    expect(saveButton()).toBeDisabled();
    expect(server.requests.map((r) => r.method)).toEqual(["GET"]);
  });

  it("AC-UC02-02 sends only the changed field with If-Match from the server and an Idempotency-Key", async () => {
    server = mockApi({
      [`GET ${PATH}`]: () => profile(MINH),
      [`PATCH ${PATH}`]: () => saved({ ...MINH, displayName: "Minh An Nguyễn", revision: 4 }),
    });
    const { onSaved } = await renderEdit();
    await screen.findByDisplayValue("Minh An");

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "  Minh An Nguyễn ");
    await save();

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(MINH.id));
    const [request] = patches(server);
    expect(new URL(request.url).pathname).toBe(`/api/v1/students/${MINH.id}`);
    expect(request.headers.get("If-Match")).toBe('"3"');
    expect(request.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f]{32}$/);
    expect(await request.json()).toEqual({ displayName: "Minh An Nguyễn" });
    expect(server.unexpected).toEqual([]);
  });

  it("can clear the goal, which sends an empty goal", async () => {
    server = mockApi({
      [`GET ${PATH}`]: () => profile(MINH),
      [`PATCH ${PATH}`]: () => saved({ ...MINH, shortGoal: "", revision: 4 }),
    });
    const { onSaved } = await renderEdit();
    await screen.findByDisplayValue("Minh An");

    await fireEvent.changeText(screen.getByLabelText("Mục tiêu ngắn"), "");
    await save();

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(MINH.id));
    expect(await patches(server)[0].json()).toEqual({ shortGoal: "" });
  });

  it("INV-23 on a stale ETag reloads on request, keeps the input and saves against the new revision", async () => {
    let gets = 0;
    let attempts = 0;
    server = mockApi({
      [`GET ${PATH}`]: () => {
        gets += 1;
        // Someone else changed the goal in the meantime: revision 4.
        return gets === 1 ? profile(MINH) : profile({ ...MINH, shortGoal: "Ôn tập phân thức.", revision: 4 });
      },
      [`PATCH ${PATH}`]: () => {
        attempts += 1;
        return attempts === 1
          ? problem(412, "PRECONDITION_FAILED", "Dữ liệu đã thay đổi.")
          : saved({ ...MINH, displayName: "Minh An Nguyễn", shortGoal: "Ôn tập phân thức.", revision: 5 });
      },
    });
    const { onSaved } = await renderEdit();
    await screen.findByDisplayValue("Minh An");

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh An Nguyễn");
    await save();

    expect(
      await screen.findByText("Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."),
    ).toBeOnTheScreen();
    expect(saveButton()).toBeDisabled();
    expect(onSaved).not.toHaveBeenCalled();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Tải lại" })));

    await waitFor(() => expect(gets).toBe(2));
    await waitFor(() => expect(screen.queryByText(/Dữ liệu đã thay đổi từ lúc bạn mở/)).toBeNull());
    // What the user typed stays; nothing is merged on the device.
    expect(screen.getByLabelText("Tên hiển thị của con")).toHaveDisplayValue("Minh An Nguyễn");

    await save();

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(MINH.id));
    const [first, second] = patches(server);
    expect(first.headers.get("If-Match")).toBe('"3"');
    expect(second.headers.get("If-Match")).toBe('"4"');
    expect(second.headers.get("Idempotency-Key")).not.toBe(first.headers.get("Idempotency-Key"));
    expect(await second.json()).toEqual({ displayName: "Minh An Nguyễn", shortGoal: "Củng cố hằng đẳng thức." });
  });

  it("after an unknown outcome allows only the retry, with the same key, changes and ETag", async () => {
    let attempts = 0;
    server = mockApi({
      [`GET ${PATH}`]: () => profile(MINH),
      [`PATCH ${PATH}`]: () => {
        attempts += 1;
        if (attempts === 1) throw new TypeError("Network request failed");
        return saved({ ...MINH, displayName: "Minh An Nguyễn", revision: 4 });
      },
    });
    const { onSaved } = await renderEdit();
    await screen.findByDisplayValue("Minh An");

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh An Nguyễn");
    await save();
    expect(await screen.findByText(/Chưa rõ kết quả/)).toBeOnTheScreen();

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Bin");
    expect(saveButton()).toBeDisabled();
    await save();
    expect(patches(server)).toHaveLength(1);

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thử lại" })));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(MINH.id));
    const [first, second] = patches(server);
    expect(second.headers.get("Idempotency-Key")).toBe(first.headers.get("Idempotency-Key"));
    expect(second.headers.get("If-Match")).toBe('"3"');
    expect(await second.json()).toEqual({ displayName: "Minh An Nguyễn" });
  });

  it("puts a server field error at the field and keeps the input", async () => {
    server = mockApi({
      [`GET ${PATH}`]: () => profile(MINH),
      [`PATCH ${PATH}`]: () =>
        problem(422, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.", [
          { path: "displayName", message: "Nhập tên hiển thị của con (1–100 ký tự)." },
        ]),
    });
    const { onSaved } = await renderEdit();
    await screen.findByDisplayValue("Minh An");

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh An Nguyễn");
    await save();

    expect(await screen.findByText("Lỗi: Nhập tên hiển thị của con (1–100 ký tự).")).toBeOnTheScreen();
    expect(screen.getByText("Vui lòng sửa các ô được đánh dấu rồi gửi lại.")).toBeOnTheScreen();
    expect(screen.getByLabelText("Tên hiển thị của con")).toHaveDisplayValue("Minh An Nguyễn");
    expect(onSaved).not.toHaveBeenCalled();
  });

  it.each([
    [403, "FORBIDDEN", "Bạn không còn quyền xem nội dung này."],
    [404, "NOT_FOUND", "Không tìm thấy nội dung này. Có thể nó đã bị xóa hoặc bạn không có quyền xem."],
  ])("AC-UC02-02 a %i on load shows nothing about the profile", async (status, code, message) => {
    server = mockApi({ [`GET ${PATH}`]: () => problem(status, code, "Không được phép.") });
    await renderEdit();

    expect(await screen.findByText(message)).toBeOnTheScreen();
    expect(screen.queryByLabelText("Tên hiển thị của con")).toBeNull();
    expect(screen.queryByRole("button", { name: "Lưu thay đổi" })).toBeNull();
  });

  it("goes back from the top bar without sending", async () => {
    server = mockApi({ [`GET ${PATH}`]: () => profile(MINH) });
    const { onBack } = await renderEdit();
    await screen.findByDisplayValue("Minh An");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Quay lại" })));

    expect(onBack).toHaveBeenCalledTimes(1);
    expect(patches(server)).toHaveLength(0);
  });
});
