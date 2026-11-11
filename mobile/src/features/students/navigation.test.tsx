import { act, fireEvent, renderRouter, screen } from "expo-router/testing-library";
import { json, mockApi } from "@/shared/test/mockApi";
import { clearStore, fakeSession, storeSession } from "@/shared/test/session";

// Fake child only (AGENTS.md).
const CREATED = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "" };

let server: ReturnType<typeof mockApi>;

beforeEach(() => {
  clearStore();
  storeSession(fakeSession("PARENT"));
});
afterEach(() => server.restore());

// Uses the real route tree in src/app.
describe("PH02 routes", () => {
  it("opens PH02 from the temporary link on the parent home", async () => {
    server = mockApi({ "GET /students": () => json(200, { items: [], nextCursor: null }) });
    await renderRouter("./src/app", { initialUrl: "/parent" });
    await screen.findByRole("header", { name: "Trang chủ phụ huynh" });

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Hồ sơ các con" })));

    expect(await screen.findByRole("header", { name: "Hồ sơ các con" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/parent/account/students");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Quay lại" })));
    expect(await screen.findByRole("header", { name: "Trang chủ phụ huynh" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/parent");
  });

  it("opens AUTH06 from the temporary link on the parent home", async () => {
    server = mockApi({ "GET /me/sessions": () => json(200, { items: [], nextCursor: null }) });
    await renderRouter("./src/app", { initialUrl: "/parent" });
    await screen.findByRole("header", { name: "Trang chủ phụ huynh" });

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Bảo mật" })));

    expect(await screen.findByRole("header", { name: "Bảo mật và phiên đăng nhập" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/parent/security");
  });

  it("adds a child and returns to the refreshed list that names the saved profile", async () => {
    let created = false;
    server = mockApi({
      "GET /students": () => json(200, { items: created ? [CREATED] : [], nextCursor: null }),
      "POST /students": () => {
        created = true;
        return json(201, CREATED);
      },
    });
    await renderRouter("./src/app", { initialUrl: "/parent/account/students" });
    await screen.findByText("Chưa có hồ sơ con nào. Tạo hồ sơ cho con để bắt đầu tìm gia sư.");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thêm hồ sơ con" })));
    expect(await screen.findByRole("header", { name: "Thêm hồ sơ con" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/parent/account/students/new");

    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh An");
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Lưu hồ sơ" })));

    expect(await screen.findByText("Đã lưu hồ sơ Minh An.")).toBeOnTheScreen();
    expect(screen).toHavePathname("/parent/account/students");
    // The list was loaded again after the save, not patched on the device.
    expect(server.requests.filter((r) => r.method === "GET")).toHaveLength(2);
    // The form is closed, not left on screen under the list.
    expect(screen.queryByRole("header", { name: "Thêm hồ sơ con" })).toBeNull();
  });

  it("keeps a tutor out of PH02", async () => {
    server = mockApi({});
    clearStore();
    storeSession(fakeSession("TUTOR"));

    await renderRouter("./src/app", { initialUrl: "/parent/account/students" });

    expect(await screen.findByRole("header", { name: "Trang chủ gia sư" })).toBeOnTheScreen();
    expect(server.requests).toHaveLength(0);
  });
});
