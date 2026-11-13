import { act, fireEvent, screen } from "@testing-library/react-native";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import { StudentsScreen } from "./StudentsScreen";

// Fake children only (AGENTS.md): no real names.
const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "Củng cố hằng đẳng thức." };
const NGOC = { id: "1c8d5a4f-3b62-4b9f-8b54-7e2a1d3c0f21", displayName: "Bảo Ngọc", grade: 8, revision: 1, shortGoal: "" };
const BIN = { id: "2d9e6b5a-4c73-4caf-9c65-8f3b2e4d1a32", displayName: "Bin", grade: 8, revision: 2 };

const page = (items: unknown[], nextCursor: string | null = null) => json(200, { items, nextCursor });

function renderList(props: Partial<Parameters<typeof StudentsScreen>[0]> = {}) {
  const handlers = { onBack: jest.fn(), onAdd: jest.fn() };
  return renderScreen(<StudentsScreen {...handlers} {...props} />, { session: fakeSession("PARENT") }).then(() => handlers);
}

describe("PH02 Hồ sơ các con: danh sách", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("AC-UC02-02 lists the signed-in parent's children with the session token and no owner in the request", async () => {
    server = mockApi({ "GET /students": () => page([MINH, NGOC, BIN]) });

    await renderList();

    expect(await screen.findByText("Minh An")).toBeOnTheScreen();
    expect(screen.getByText("Mục tiêu: Củng cố hằng đẳng thức.")).toBeOnTheScreen();
    expect(screen.getByText("Bảo Ngọc")).toBeOnTheScreen();
    // An empty or missing goal reads as "no goal", never a blank line.
    expect(screen.getAllByText("Chưa ghi mục tiêu.")).toHaveLength(2);
    expect(screen.getAllByText("Lớp 8")).toHaveLength(3);
    expect(screen.getByText("Mỗi thao tác tìm gia sư, lịch và lớp đều gắn với một con. Chỉ bạn quản lý các hồ sơ này.")).toBeOnTheScreen();

    const request = server.requests[0];
    expect(request.headers.get("Authorization")).toBe("Bearer test-access-token-not-a-secret");
    const url = new URL(request.url);
    expect(url.searchParams.get("limit")).toBe("50");
    expect([...url.searchParams.keys()].sort()).toEqual(["limit"]);
    expect(server.unexpected).toEqual([]);
  });

  it("cuts a long name to 2 lines and a long goal to 3 lines", async () => {
    const long = { ...MINH, displayName: "Nguyễn Hoàng Gia Bảo Khôi Nguyên ".repeat(3).trim(), shortGoal: "Con đang học lớp 8. ".repeat(40).trim() };
    server = mockApi({ "GET /students": () => page([long]) });

    await renderList();

    expect(await screen.findByText(long.displayName)).toHaveProp("numberOfLines", 2);
    expect(screen.getByText(`Mục tiêu: ${long.shortGoal}`)).toHaveProp("numberOfLines", 3);
  });

  it("shows the empty state with a way to add the first child", async () => {
    server = mockApi({ "GET /students": () => page([]) });
    const { onAdd } = await renderList();

    expect(await screen.findByText("Chưa có hồ sơ con nào. Tạo hồ sơ cho con để bắt đầu tìm gia sư.")).toBeOnTheScreen();
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thêm hồ sơ con" })));
    expect(onAdd).toHaveBeenCalledTimes(1);
  });

  it("offers adding a child below the list", async () => {
    server = mockApi({ "GET /students": () => page([MINH]) });
    const { onAdd } = await renderList();

    await screen.findByText("Minh An");
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thêm hồ sơ con" })));
    expect(onAdd).toHaveBeenCalledTimes(1);
  });

  it("goes back from the top bar", async () => {
    server = mockApi({ "GET /students": () => page([MINH]) });
    const { onBack } = await renderList();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Quay lại" })));
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it("loads the next page with the server's cursor", async () => {
    server = mockApi({
      "GET /students": (request) =>
        new URL(request.url).searchParams.get("cursor") === "trang-2" ? page([NGOC]) : page([MINH], "trang-2"),
    });
    await renderList();
    await screen.findByText("Minh An");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Xem thêm" })));

    expect(await screen.findByText("Bảo Ngọc")).toBeOnTheScreen();
    expect(screen.queryByRole("button", { name: "Xem thêm" })).toBeNull();
    expect(new URL(server.requests[1].url).searchParams.get("cursor")).toBe("trang-2");
  });

  it("says which profile was saved, using the name from the server's list", async () => {
    server = mockApi({ "GET /students": () => page([MINH, NGOC]) });

    await renderList({ savedId: MINH.id });

    expect(await screen.findByText("Đã lưu hồ sơ Minh An.")).toBeOnTheScreen();
  });

  it("says nothing about a saved id that is not in the list", async () => {
    server = mockApi({ "GET /students": () => page([NGOC]) });

    await renderList({ savedId: MINH.id });

    await screen.findByText("Bảo Ngọc");
    expect(screen.queryByText(/Đã lưu hồ sơ/)).toBeNull();
  });

  it("shows a load error with a retry that loads again", async () => {
    let attempts = 0;
    server = mockApi({
      "GET /students": () => {
        attempts += 1;
        return attempts <= 3 ? problem(503, "UNAVAILABLE", "Dịch vụ tạm thời không sẵn sàng.") : page([MINH]);
      },
    });
    await renderList();

    expect(await screen.findByText("Không tải được dữ liệu. Kiểm tra kết nối rồi thử lại.", {}, { timeout: 10_000 })).toBeOnTheScreen();
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thử lại" })));

    expect(await screen.findByText("Minh An")).toBeOnTheScreen();
  }, 15_000); // Reads retry twice on 5xx with the default backoff before the error shows.

  it("shows lost access without any child data", async () => {
    server = mockApi({ "GET /students": () => problem(403, "FORBIDDEN", "Không được phép.") });

    await renderList();

    expect(await screen.findByText("Bạn không còn quyền xem nội dung này.")).toBeOnTheScreen();
    expect(screen.queryByRole("button", { name: "Thêm hồ sơ con" })).toBeNull();
  });
});
