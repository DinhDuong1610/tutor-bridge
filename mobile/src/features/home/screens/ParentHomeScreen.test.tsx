import { act, fireEvent, screen } from "@testing-library/react-native";
import { SelectedStudentProvider } from "@/features/students/selection";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import { ParentHomeScreen } from "./ParentHomeScreen";

// Fake children only (AGENTS.md): no real names.
const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "" };
const NGOC = { id: "1c8d5a4f-3b62-4b9f-8b54-7e2a1d3c0f21", displayName: "Bảo Ngọc", grade: 8, revision: 1, shortGoal: "" };

const page = (items: unknown[]) => json(200, { items, nextCursor: null });

async function renderHome() {
  const handlers = { onAddStudent: jest.fn(), onManageStudents: jest.fn(), onSecurity: jest.fn() };
  await renderScreen(
    <SelectedStudentProvider>
      <ParentHomeScreen {...handlers} />
    </SelectedStudentProvider>,
    { session: fakeSession("PARENT") },
  );
  return handlers;
}

const switcher = (label: string) => screen.getByRole("button", { name: `Đổi con. Đang xem ${label}` });

describe("PH01 Trang chủ phụ huynh", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("AC-UC02-02 shows the first child of the session's own list and no owner in the request", async () => {
    server = mockApi({ "GET /students": () => page([MINH, NGOC]) });

    await renderHome();

    expect(await screen.findByRole("header", { name: "Tổng quan" })).toBeOnTheScreen();
    expect(switcher("Minh An, lớp 8")).toBeOnTheScreen();
    expect(screen.getByText("Đang xem")).toBeOnTheScreen();
    expect(screen.getByText("Đổi con ⌄")).toBeOnTheScreen();
    const url = new URL(server.requests[0].url);
    expect([...url.searchParams.keys()].sort()).toEqual(["limit"]);
    expect(server.unexpected).toEqual([]);
  });

  it("hides every block whose API is not merged yet (Đính, #14 câu 1)", async () => {
    server = mockApi({ "GET /students": () => page([MINH]) });

    await renderHome();
    await screen.findByText("Minh An, lớp 8");

    for (const block of ["Buổi sắp tới", "Việc đang chờ", "Thông báo mới", "Chưa có buổi học nào."]) {
      expect(screen.queryByText(block, { exact: false })).toBeNull();
    }
    // Only listStudents is called: nothing is read for the hidden blocks.
    expect(server.requests.map((request) => new URL(request.url).pathname)).toEqual(["/api/v1/students"]);
  });

  it("AC-UC02-01 switches the child from the sheet, which marks the child being viewed", async () => {
    server = mockApi({ "GET /students": () => page([MINH, NGOC]) });
    await renderHome();
    await screen.findByText("Minh An, lớp 8");

    await act(async () => fireEvent.press(switcher("Minh An, lớp 8")));

    expect(screen.getByRole("header", { name: "Chọn con" })).toBeOnTheScreen();
    expect(screen.getByText("Chọn con khác sẽ tải lại toàn bộ trang theo con đó.")).toBeOnTheScreen();
    expect(screen.getByRole("radio", { name: "Minh An. Lớp 8." })).toBeChecked();
    expect(screen.getByRole("radio", { name: "Bảo Ngọc. Lớp 8." })).not.toBeChecked();

    await act(async () => fireEvent.press(screen.getByRole("radio", { name: "Bảo Ngọc. Lớp 8." })));

    expect(switcher("Bảo Ngọc, lớp 8")).toBeOnTheScreen();
    expect(screen.queryByRole("header", { name: "Chọn con" })).toBeNull();
  });

  it("opens PH02 from the sheet", async () => {
    server = mockApi({ "GET /students": () => page([MINH]) });
    const { onManageStudents } = await renderHome();
    await screen.findByText("Minh An, lớp 8");

    await act(async () => fireEvent.press(switcher("Minh An, lớp 8")));
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Quản lý hồ sơ các con" })));

    expect(onManageStudents).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole("header", { name: "Chọn con" })).toBeNull();
  });

  it("shows the no-child state with a way to add the first child", async () => {
    server = mockApi({ "GET /students": () => page([]) });
    const { onAddStudent } = await renderHome();

    expect(await screen.findByText("Chào bạn")).toBeOnTheScreen();
    expect(screen.getByText("Tạo hồ sơ cho con trước. Mọi việc tìm gia sư, lịch và lớp đều gắn với một con.")).toBeOnTheScreen();
    expect(screen.queryByText("Đang xem")).toBeNull();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thêm hồ sơ con" })));
    expect(onAddStudent).toHaveBeenCalledTimes(1);
  });

  it("keeps the temporary way in to AUTH06 until the Account tab exists", async () => {
    server = mockApi({ "GET /students": () => page([MINH]) });
    const { onSecurity } = await renderHome();
    await screen.findByText("Minh An, lớp 8");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Bảo mật" })));

    expect(onSecurity).toHaveBeenCalledTimes(1);
  });

  it("shows the load error with a retry that reads the list again", async () => {
    // Reads retry twice on 5xx with the default backoff before the error shows (same as PH02's test).
    let attempts = 0;
    server = mockApi({
      "GET /students": () => {
        attempts += 1;
        return attempts <= 3 ? problem(503, "UNAVAILABLE", "Dịch vụ tạm thời không sẵn sàng.") : page([MINH]);
      },
    });
    await renderHome();

    expect(await screen.findByText("Không tải được dữ liệu. Kiểm tra kết nối rồi thử lại.", {}, { timeout: 10_000 })).toBeOnTheScreen();
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thử lại" })));

    expect(await screen.findByText("Minh An, lớp 8")).toBeOnTheScreen();
  }, 15_000);

  it("lets a very long name wrap instead of cutting it (worst data, 200% text)", async () => {
    const long = { ...MINH, displayName: "Nguyễn Hoàng Gia Bảo Khôi Nguyên ".repeat(3).trim() };
    server = mockApi({ "GET /students": () => page([long]) });
    await renderHome();

    const name = await screen.findByText(`${long.displayName}, lớp 8`);
    expect(name).not.toHaveProp("numberOfLines");
    expect(name).not.toHaveProp("maxFontSizeMultiplier");
    expect(screen.getByText("Đổi con ⌄")).not.toHaveProp("numberOfLines");
  });
});
