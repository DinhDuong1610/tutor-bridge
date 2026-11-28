import { act, fireEvent, renderRouter, screen, waitFor } from "expo-router/testing-library";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { clearStore, fakeSession, storeSession, storedSession } from "@/shared/test/session";
import { preloadRoutes } from "@/shared/test/preloadRoutes";

// COM01 on the real route tree: the role areas, the temporary way in from PH01 and the session.
const INBOX = {
  items: [
    {
      id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10",
      eventType: "CLASS_CREATED",
      targetType: "CLASS",
      targetId: "9f0e8d7c-6b5a-4c3d-8e2f-1a0b9c8d7e6f",
      targetVersion: "1",
      createdAt: "2026-11-17T14:02:00Z",
      read: false,
    },
  ],
  nextCursor: null,
};

// #123: compile the route tree before the first test, with its own timeout.
beforeAll(preloadRoutes, 120_000);

describe("COM01 routes", () => {
  let server: ReturnType<typeof mockApi>;
  beforeEach(() => clearStore());
  afterEach(() => server.restore());

  it("opens the parent's inbox from PH01 (temporary way in until #205)", async () => {
    server = mockApi({
      "GET /students": () => json(200, { items: [], nextCursor: null }),
      "GET /me/notifications": () => json(200, INBOX),
    });
    storeSession(fakeSession("PARENT"));
    await renderRouter("./src/app", { initialUrl: "/parent" });

    await act(async () => fireEvent.press(await screen.findByRole("button", { name: "Thông báo" })));

    expect(screen).toHavePathname("/parent/notifications");
    expect(await screen.findByText("Lớp học đã được chốt")).toBeOnTheScreen();
    expect(server.unexpected).toEqual([]);
  });

  it("shows the tutor's inbox at the tutor route", async () => {
    server = mockApi({ "GET /me/notifications": () => json(200, { ...INBOX, items: [{ ...INBOX.items[0], eventType: "OFFER_SENT" }] }) });
    storeSession(fakeSession("TUTOR"));
    await renderRouter("./src/app", { initialUrl: "/tutor/notifications" });

    expect(await screen.findByText("Bạn có đề nghị nhận lớp mới")).toBeOnTheScreen();
  });

  it("ends the session on a 401", async () => {
    server = mockApi({
      "GET /me/notifications": () => problem(401, "AUTHENTICATION_REQUIRED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."),
    });
    storeSession(fakeSession("PARENT"));
    await renderRouter("./src/app", { initialUrl: "/parent/notifications" });

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    await waitFor(() => expect(storedSession()).toBeUndefined());
  });

  it("keeps a tutor out of the parent's inbox route", async () => {
    server = mockApi({});
    storeSession(fakeSession("TUTOR"));
    await renderRouter("./src/app", { initialUrl: "/parent/notifications" });

    await waitFor(() => expect(screen).not.toHavePathname("/parent/notifications"));
    expect(server.requests.filter((r) => r.url.includes("/me/notifications"))).toHaveLength(0);
  });
});
