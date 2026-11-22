import { renderRouter, screen, waitFor } from "expo-router/testing-library";
import { mockApi, problem } from "@/shared/test/mockApi";
import { clearStore, fakeSession, storeSession, storedSession } from "@/shared/test/session";

// Uses the real route tree, so the session handling of the foundation (watchSessionEnd) is real too.
describe("GS01 with the session", () => {
  let server: ReturnType<typeof mockApi>;
  beforeEach(() => clearStore());
  afterEach(() => server.restore());

  it("ends the session on a 401 even when another read of GS01 fails first (Codex, #206 P1)", async () => {
    let answerVerification: (response: Response) => void = () => undefined;
    server = mockApi({
      // The 403 arrives first; the 401 comes after it.
      "GET /me/tutor-profile": () => problem(403, "ACCESS_UNAVAILABLE", "Không có quyền"),
      "GET /me/tutor-profile/verification": () => new Promise<Response>((resolve) => (answerVerification = resolve)),
      "GET /me/tutor-calendar": () => problem(404, "NOT_FOUND", "Không tìm thấy"),
    });
    storeSession(fakeSession("TUTOR"));
    await renderRouter("./src/app", { initialUrl: "/tutor" });
    await waitFor(() => expect(server.requests).toHaveLength(3));

    answerVerification(problem(401, "AUTHENTICATION_REQUIRED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."));

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    await waitFor(() => expect(storedSession()).toBeUndefined());
  });
});
