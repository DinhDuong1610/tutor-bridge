import { act, fireEvent, renderRouter, screen } from "expo-router/testing-library";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { clearStore, fakeSession, storeSession } from "@/shared/test/session";
import { preloadRoutes } from "@/shared/test/preloadRoutes";
import type { Need, Offer } from "./summary";

// Fake data only (AGENTS.md).
const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "" };
const TUTOR = "2d9e6b50-4c73-4caf-9c65-8f3b2e4d1032";
const CLASS = "3e0f7c61-5d84-4dba-8d76-903c3f5e2143";
const need = (id: string, status: Need["status"], goal: string): Need => ({
  id,
  studentId: MINH.id,
  revision: 1,
  versionId: id.replace(/^./, "9"),
  status,
  draft: { goal, budgetVndSession: 400000, durationMinutes: 90, frequencyPer7Days: 2, cycleStartDate: "2026-11-18" },
  studentCalendarId: null,
});
const CONFIRMED = need("4f108d72-6e95-4ecb-9e87-a14d406f3254", "CONFIRMED", "hằng đẳng thức và phân thức");
const DRAFT = need("50219e83-7fa6-4fdc-8f98-b25e5170436a", "DRAFT", "ôn thi học kỳ 2");
const CLOSED = need("61320f94-80b7-4aed-90a9-c36f6281547b", "CLOSED", "nhân chia đa thức");
const ACCEPTED = { id: "94653fc7-b3ea-4d01-83dc-f69295b47802", needId: CLOSED.id, state: "ACCEPTED", tutorId: TUTOR, classId: CLASS } as Offer;

const CLASS_BODY = {
  id: CLASS,
  studentId: MINH.id,
  studentDisplayName: MINH.displayName,
  tutorId: TUTOR,
  revision: 1,
  state: "WAITING",
  priceVndSession: 375000,
  durationMinutes: 90,
  effectiveGoalVersionId: "a7b8c9d0-1e2f-4a3b-8c4d-5e6f7a8b9c0d",
  goal: "nhân chia đa thức",
};

// #123: compile the route tree before the first test, with its own timeout.
beforeAll(preloadRoutes, 120_000);

let server: ReturnType<typeof mockApi>;

beforeEach(() => {
  clearStore();
  storeSession(fakeSession("PARENT"));
});
afterEach(() => server.restore());

function serve(needs: Need[], offers: Offer[] = []) {
  server = mockApi({
    "GET /students": () => json(200, { items: [MINH], nextCursor: null }),
    "GET /students/{studentId}/needs": () => json(200, { items: needs, nextCursor: null }),
    "GET /me/offers": () => json(200, { items: offers, nextCursor: null }),
    "GET /tutors/{tutorId}/public-profile": () => json(404, {}),
    // CL01, the class of a closed need (#73 PR 2).
    "GET /classes/{classId}": () => json(200, CLASS_BODY),
    "GET /classes/{classId}/sessions": () => json(200, { items: [], nextCursor: null }),
    // PH04 reads the child's calendar (#70 PR 3b) and the need it edits, with their ETags (#70 PR 3a).
    "GET /students/{studentId}/calendar": () => problem(404, "CALENDAR_NOT_FOUND", "Chưa có lịch rảnh."),
    "GET /needs/{needId}": (request) => {
      const found = needs.find((n) => request.url.endsWith(`/needs/${n.id}`));
      return found
        ? new Response(JSON.stringify(found), { status: 200, headers: { "Content-Type": "application/json", ETag: `"${found.revision}"` } })
        : problem(404, "NEED_NOT_FOUND", "Không tìm thấy nhu cầu.");
    },
  });
}

const press = (name: string) => act(async () => fireEvent.press(screen.getByRole("button", { name })));

// Uses the real route tree in src/app. Targets not built yet show ComingSoon (#70 Q1).
describe("PH03 routes", () => {
  it("opens PH03 from the temporary button on PH01 (#70 PR 2 Q4)", async () => {
    serve([CONFIRMED]);
    await renderRouter("./src/app", { initialUrl: "/parent" });
    await screen.findByRole("button", { name: "Đổi con. Đang xem Minh An, lớp 8" });

    await press("Tìm gia sư");

    expect(await screen.findByText("Toán 8: hằng đẳng thức và phân thức")).toBeOnTheScreen();
    expect(screen).toHavePathname("/parent/needs");
  });

  it.each([
    ["Tìm gia sư", `/parent/needs/${CONFIRMED.id}/matches`, "Kết quả và so sánh gia sư"],
    ["Xem đề nghị", `/parent/needs/${CONFIRMED.id}/offers`, "Danh sách và chi tiết đề nghị"],
  ])("a confirmed need's %s opens %s", async (action, path, title) => {
    serve([CONFIRMED]);
    await renderRouter("./src/app", { initialUrl: "/parent/needs" });
    await screen.findByText("Toán 8: hằng đẳng thức và phân thức");

    await press(action);

    expect(await screen.findByRole("header", { name: title })).toBeOnTheScreen();
    expect(screen).toHavePathname(path);
  });

  it("a draft continues in PH04's edit route", async () => {
    serve([DRAFT]);
    await renderRouter("./src/app", { initialUrl: "/parent/needs" });
    await screen.findByText("Toán 8: ôn thi học kỳ 2");

    await press("Tiếp tục nhập");

    // PH04 opens on the draft it continues (#70 PR 3a).
    expect(await screen.findByRole("header", { name: "Nhu cầu học kèm" })).toBeOnTheScreen();
    expect(await screen.findByDisplayValue("ôn thi học kỳ 2")).toBeOnTheScreen();
    expect(screen).toHavePathname(`/parent/needs/${DRAFT.id}/edit`);
  });

  it("a closed need opens its class", async () => {
    serve([CLOSED], [ACCEPTED]);
    await renderRouter("./src/app", { initialUrl: "/parent/needs" });
    await screen.findByText("Toán 8: nhân chia đa thức");

    await press("Xem lớp");
    expect(await screen.findByRole("header", { name: "Chi tiết lớp" })).toBeOnTheScreen();
    expect(await screen.findByText("nhân chia đa thức")).toBeOnTheScreen();
    expect(screen).toHavePathname(`/parent/classes/${CLASS}`);
  });

  it("creates a new need in PH04 once every need is closed", async () => {
    serve([CLOSED]);
    await renderRouter("./src/app", { initialUrl: "/parent/needs" });
    await screen.findByText("Toán 8: nhân chia đa thức");

    await press("Tạo nhu cầu mới");

    expect(await screen.findByRole("header", { name: "Nhu cầu học kèm" })).toBeOnTheScreen();
    expect(await screen.findByText("Minh An, lớp 8")).toBeOnTheScreen();
    expect(screen).toHavePathname("/parent/needs/new");
  });

  it("keeps a tutor out of PH03", async () => {
    // GS01 reads the tutor's own data; a new tutor has none of it yet.
    const none = () => problem(404, "NOT_FOUND", "Không tìm thấy");
    server = mockApi({ "GET /me/tutor-profile": none, "GET /me/tutor-profile/verification": none, "GET /me/tutor-calendar": none });
    clearStore();
    storeSession(fakeSession("TUTOR"));

    await renderRouter("./src/app", { initialUrl: "/parent/needs" });

    expect(await screen.findByText("Bước 1")).toBeOnTheScreen();
    expect(screen).toHavePathname("/tutor");
    // Nothing of PH03 (listNeeds, listOffers) is requested.
    expect(server.requests.some((request) => /needs|offers/.test(request.url))).toBe(false);
    expect(server.unexpected).toEqual([]);
  });
});
