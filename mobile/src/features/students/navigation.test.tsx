import { act, fireEvent, renderRouter, screen } from "expo-router/testing-library";
import { json, mockApi, problem } from "@/shared/test/mockApi";
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

    expect(await screen.findByRole("header", { name: "Bảo mật và phiên" })).toBeOnTheScreen();
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

  it("still says the profile was saved when the list fails to reload after the save", async () => {
    let created = false;
    server = mockApi({
      "GET /students": () =>
        created ? problem(503, "UNAVAILABLE", "Dịch vụ tạm thời không sẵn sàng.") : json(200, { items: [], nextCursor: null }),
      "POST /students": () => {
        created = true;
        return json(201, CREATED);
      },
    });
    await renderRouter("./src/app", { initialUrl: "/parent/account/students" });
    await screen.findByText("Chưa có hồ sơ con nào. Tạo hồ sơ cho con để bắt đầu tìm gia sư.");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thêm hồ sơ con" })));
    await screen.findByRole("header", { name: "Thêm hồ sơ con" });
    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh An");
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Lưu hồ sơ" })));

    // The reload fails (after its retries): old data with a warning, and the confirmed save still shown.
    expect(
      await screen.findByText("Không cập nhật được. Đang hiển thị dữ liệu đã tải trước đó.", {}, { timeout: 10_000 }),
    ).toBeOnTheScreen();
    expect(screen.getByText("Đã lưu hồ sơ Minh An.")).toBeOnTheScreen();
    expect(screen).toHavePathname("/parent/account/students");
  }, 20_000); // Reads retry twice on 5xx with the default backoff before the warning shows.

  it("edits a child from the list and returns to the list that names the saved profile", async () => {
    let name = "Minh An";
    server = mockApi({
      "GET /students": () => json(200, { items: [{ ...CREATED, displayName: name, revision: name === "Minh An" ? 1 : 2 }], nextCursor: null }),
      "GET /students/{studentId}": () =>
        new Response(JSON.stringify(CREATED), { status: 200, headers: { "Content-Type": "application/json", ETag: '"1"' } }),
      "PATCH /students/{studentId}": () => {
        name = "Minh An Nguyễn";
        return new Response(JSON.stringify({ ...CREATED, displayName: name, revision: 2 }), {
          status: 200,
          headers: { "Content-Type": "application/json", ETag: '"2"' },
        });
      },
    });
    await renderRouter("./src/app", { initialUrl: "/parent/account/students" });
    await screen.findByText("Minh An");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Sửa hồ sơ Minh An" })));
    expect(await screen.findByRole("header", { name: "Sửa hồ sơ" })).toBeOnTheScreen();
    expect(screen).toHavePathname(`/parent/account/students/${CREATED.id}`);

    await screen.findByDisplayValue("Minh An");
    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh An Nguyễn");
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Lưu thay đổi" })));

    expect(await screen.findByText("Đã lưu hồ sơ Minh An Nguyễn.")).toBeOnTheScreen();
    expect(screen).toHavePathname("/parent/account/students");
    expect(screen.queryByRole("header", { name: "Sửa hồ sơ" })).toBeNull();
  });

  it("after saving, a second edit starts from the saved revision and never sends back the old name", async () => {
    // Server state: the name changes on the first save, the goal on the second (Codex review of #138).
    let current = { ...CREATED, displayName: "Minh An", shortGoal: "", revision: 3 };
    const withEtag = (body: unknown, revision: number) =>
      new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json", ETag: `"${revision}"` } });
    server = mockApi({
      "GET /students": () => json(200, { items: [current], nextCursor: null }),
      "GET /students/{studentId}": () => withEtag(current, current.revision),
      "PATCH /students/{studentId}": async (request) => {
        const changes = (await request.json()) as Partial<typeof current>;
        if (request.headers.get("If-Match") !== `"${current.revision}"`) {
          return problem(412, "PRECONDITION_FAILED", "Dữ liệu đã thay đổi.");
        }
        current = { ...current, ...changes, revision: current.revision + 1 };
        return withEtag(current, current.revision);
      },
    });
    await renderRouter("./src/app", { initialUrl: "/parent/account/students" });
    await screen.findByText("Minh An");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Sửa hồ sơ Minh An" })));
    await screen.findByDisplayValue("Minh An");
    await fireEvent.changeText(screen.getByLabelText("Tên hiển thị của con"), "Minh Anh");
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Lưu thay đổi" })));
    expect(await screen.findByText("Đã lưu hồ sơ Minh Anh.")).toBeOnTheScreen();

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Sửa hồ sơ Minh Anh" })));
    expect(await screen.findByDisplayValue("Minh Anh")).toBeOnTheScreen();
    await fireEvent.changeText(screen.getByLabelText("Mục tiêu ngắn"), "Ôn tập phân thức.");
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Lưu thay đổi" })));

    expect(await screen.findByText("Đã lưu hồ sơ Minh Anh.")).toBeOnTheScreen();
    const patches = server.requests.filter((r) => r.method === "PATCH");
    expect(patches).toHaveLength(2);
    expect(patches[1].headers.get("If-Match")).toBe('"4"');
    expect(await patches[1].json()).toEqual({ shortGoal: "Ôn tập phân thức." });
    expect(current).toMatchObject({ displayName: "Minh Anh", shortGoal: "Ôn tập phân thức.", revision: 5 });
  });

  it("an edit opened from an older cached revision gets 412 instead of overwriting a newer change", async () => {
    let current = { ...CREATED, displayName: "Minh An", shortGoal: "", revision: 3 };
    const withEtag = (body: unknown, revision: number) =>
      new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json", ETag: `"${revision}"` } });
    server = mockApi({
      "GET /students": () => json(200, { items: [current], nextCursor: null }),
      "GET /students/{studentId}": () => withEtag(current, current.revision),
      "PATCH /students/{studentId}": async (request) => {
        const changes = (await request.json()) as Partial<typeof current>;
        if (request.headers.get("If-Match") !== `"${current.revision}"`) {
          return problem(412, "PRECONDITION_FAILED", "Dữ liệu đã thay đổi.");
        }
        current = { ...current, ...changes, revision: current.revision + 1 };
        return withEtag(current, current.revision);
      },
    });
    await renderRouter("./src/app", { initialUrl: "/parent/account/students" });
    await screen.findByText("Minh An");

    // Open the edit screen once, so revision 3 is cached, and go back without saving.
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Sửa hồ sơ Minh An" })));
    await screen.findByDisplayValue("Minh An");
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Quay lại" })));
    await screen.findByText("Minh An");

    // Someone else renames the child: revision 4. The cache still holds revision 3.
    current = { ...current, displayName: "Bé An", revision: 4 };

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Sửa hồ sơ Minh An" })));
    await screen.findByDisplayValue("Minh An");
    await fireEvent.changeText(screen.getByLabelText("Mục tiêu ngắn"), "Ôn tập phân thức.");
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Lưu thay đổi" })));

    expect(await screen.findByText(/Dữ liệu đã thay đổi từ lúc bạn mở/)).toBeOnTheScreen();
    // The newer name is not overwritten by the name the form was filled with.
    expect(current).toMatchObject({ displayName: "Bé An", shortGoal: "", revision: 4 });
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
