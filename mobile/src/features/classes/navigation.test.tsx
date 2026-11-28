import { act, fireEvent, renderRouter, screen } from "expo-router/testing-library";
import { json, mockApi } from "@/shared/test/mockApi";
import { clearStore, fakeSession, storeSession } from "@/shared/test/session";
import { preloadRoutes } from "@/shared/test/preloadRoutes";

// Fake data only (AGENTS.md). Uses the real route tree in src/app.
const CLASS_ID = "3e0f7c61-5d84-4dba-8d76-903c3f5e2143";
const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "" };
const NGOC = { id: "7c2e9a14-3b6d-4f8e-a1c5-2d4b6e8f0a13", displayName: "Bảo Ngọc", grade: 8, revision: 1, shortGoal: "" };
const CLASS = {
  id: CLASS_ID,
  studentId: MINH.id,
  tutorId: "2d9e6b50-4c73-4caf-9c65-8f3b2e4d1032",
  revision: 1,
  state: "WAITING",
  priceVndSession: 375000,
  durationMinutes: 90,
  effectiveGoalVersionId: "4f108d72-6e95-4ecb-9e87-a14d406f3254",
  goal: "Củng cố hằng đẳng thức.",
};
const SESSION = {
  id: "00000000-0000-4000-8000-000000000001",
  classId: CLASS_ID,
  ordinal: 1,
  revision: 1,
  state: "SCHEDULED",
  during: { start: "2030-11-20T11:00:00Z", end: "2030-11-20T12:30:00Z" },
};

// #123: compile the route tree before the first test, with its own timeout.
beforeAll(preloadRoutes, 120_000);

let server: ReturnType<typeof mockApi>;
beforeEach(() => clearStore());
afterEach(() => server.restore());

describe("CL02 routes", () => {
  it("opens a class's sessions for the parent", async () => {
    storeSession(fakeSession("PARENT"));
    server = mockApi({
      "GET /classes/{classId}": () => json(200, CLASS),
      "GET /classes/{classId}/sessions": () => json(200, { items: [SESSION], nextCursor: null }),
      "GET /students": () => json(200, { items: [MINH], nextCursor: null }),
      "GET /tutors/{tutorId}/public-profile": () => json(404, {}),
    });
    await renderRouter("./src/app", { initialUrl: `/parent/classes/${CLASS_ID}/sessions` });

    expect(await screen.findByText("Toán 8, 1 buổi từ 20/11 đến 20/11/2030")).toBeOnTheScreen();
    expect(screen).toHavePathname(`/parent/classes/${CLASS_ID}/sessions`);

    // Opened straight from a link: back has no history, so it lands on the parent home.
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Quay lại" })));
    expect(screen).toHavePathname("/parent");
  });

  // Đính, #260 review, 28/11/2026: choosing another child keeps the choice and leaves (home without history);
  // "Quản lý hồ sơ các con" opens PH02.
  it.each([
    ["choosing another child leaves for the parent home", "Bảo Ngọc. Lớp 8.", "radio", "/parent"],
    ["'Quản lý hồ sơ các con' opens PH02", "Quản lý hồ sơ các con", "button", "/parent/account/students"],
  ] as const)("in the switcher, %s", async (_, name, role, pathname) => {
    storeSession(fakeSession("PARENT"));
    server = mockApi({
      "GET /classes/{classId}": () => json(200, CLASS),
      "GET /classes/{classId}/sessions": () => json(200, { items: [SESSION], nextCursor: null }),
      "GET /students": () => json(200, { items: [MINH, NGOC], nextCursor: null }),
      "GET /tutors/{tutorId}/public-profile": () => json(404, {}),
    });
    await renderRouter("./src/app", { initialUrl: `/parent/classes/${CLASS_ID}/sessions` });
    await screen.findByText("Toán 8, 1 buổi từ 20/11 đến 20/11/2030");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đổi con. Đang xem Minh An, lớp 8" })));
    await act(async () => fireEvent.press(screen.getByRole(role, { name })));

    expect(screen).toHavePathname(pathname);
  });

  it("opens a class's sessions for the tutor", async () => {
    storeSession(fakeSession("TUTOR"));
    server = mockApi({
      "GET /classes/{classId}": () => json(200, CLASS),
      "GET /classes/{classId}/sessions": () => json(200, { items: [SESSION], nextCursor: null }),
    });
    await renderRouter("./src/app", { initialUrl: `/tutor/classes/${CLASS_ID}/sessions` });

    expect(await screen.findByText("Lớp Toán 8 đại số, 1 buổi từ 20/11 đến 20/11/2030")).toBeOnTheScreen();
    expect(screen).toHavePathname(`/tutor/classes/${CLASS_ID}/sessions`);
    expect(server.unexpected).toEqual([]);
  });
});
