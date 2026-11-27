import { act, fireEvent, screen, waitFor } from "@testing-library/react-native";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import type { TutorProfile } from "../api";
import { TutorProfileScreen } from "./TutorProfileScreen";

// Made-up profile text only (AGENTS.md).
const DESCRIPTION =
  "Tôi dạy Toán THCS 2 năm, tập trung vào đại số lớp 8. Mỗi buổi tôi cho học sinh tự làm trước, chữa lỗi ngay.";
const DRAFT: TutorProfile = {
  id: "5d2f8a61-3b4c-4d7e-8f90-1a2b3c4d5e6f",
  version: 1,
  revision: 5,
  status: "DRAFT",
  description: DESCRIPTION,
  experienceMonths: 24,
  rateVnd60: 250000,
};
const NEXT_ID = "6e3a9b72-4c5d-4e8f-9a01-2b3c4d5e6f70";

function verification(overrides: Record<string, unknown> = {}) {
  return {
    id: "7f4b0c83-5d6e-4f90-8b12-3c4d5e6f7081",
    profileVersionId: DRAFT.id,
    profileVersion: 1,
    state: "WAITING",
    scope: "Toán 8, đại số",
    reason: null,
    decidedAt: null,
    approvedVersion: null,
    ...overrides,
  };
}

/** getOwnTutorProfile and saveTutorProfile answer with the strong ETag of the revision, as the backend does. */
function withEtag(profile: TutorProfile) {
  return new Response(JSON.stringify(profile), {
    status: 200,
    headers: { "Content-Type": "application/json", ETag: `"${profile.revision}"` },
  });
}

const notFound = (code: string) => () => problem(404, code, "Không tìm thấy.");

async function renderProfile() {
  const handlers = { onBack: jest.fn(), onNext: jest.fn() };
  const { queryClient } = await renderScreen(<TutorProfileScreen {...handlers} />, { session: fakeSession("TUTOR") });
  return { ...handlers, queryClient };
}

const puts = (server: ReturnType<typeof mockApi>) => server.requests.filter((r) => r.method === "PUT");
const press = (name: string) => act(async () => fireEvent.press(screen.getByRole("button", { name })));
const type = (label: string, text: string) => act(async () => fireEvent.changeText(screen.getByLabelText(label), text));

describe("GS02 Hồ sơ chuyên môn và học phí", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("AC-UC03-01 creates the first version with If-None-Match: *, then saves it again with its own ETag", async () => {
    let revision = 1;
    server = mockApi({
      "GET /me/tutor-profile": notFound("TUTOR_PROFILE_NOT_FOUND"),
      "GET /me/tutor-profile/verification": notFound("TUTOR_VERIFICATION_NOT_FOUND"),
      "PUT /me/tutor-profile": async (request) => {
        const body = (await request.json()) as Pick<TutorProfile, "description" | "experienceMonths" | "rateVnd60">;
        return withEtag({ ...DRAFT, ...body, revision: revision++ });
      },
    });
    await renderProfile();

    expect(await screen.findByText("Toán 8, đại số")).toBeOnTheScreen();
    // Nothing saved yet: no status label, empty fields.
    expect(screen.queryByText("Trạng thái")).toBeNull();
    expect(screen.getByLabelText("Mô tả cách dạy")).toHaveDisplayValue("");

    await type("Mô tả cách dạy", `  ${DESCRIPTION} `);
    await type("Kinh nghiệm liên quan (tháng)", "0");
    await type("Học phí cho 60 phút (VND)", "250.000");
    await press("Lưu nháp");

    expect(await screen.findByText("Đã lưu nháp hồ sơ.")).toBeOnTheScreen();
    expect(screen.getByText("Nháp, chưa gửi duyệt")).toBeOnTheScreen();
    const [first] = puts(server);
    expect(first.headers.get("If-None-Match")).toBe("*");
    expect(first.headers.get("If-Match")).toBeNull();
    expect(first.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f]{32}$/);
    // 0 months is valid; the price is sent as a whole number; the subject is the only one of the pilot.
    expect(await first.json()).toEqual({ description: DESCRIPTION, experienceMonths: 0, rateVnd60: 250000, subject: "MATH8_ALGEBRA" });

    await type("Học phí cho 60 phút (VND)", "300000");
    await press("Lưu nháp");
    await waitFor(() => expect(puts(server)).toHaveLength(2));
    const second = puts(server)[1];
    expect(second.headers.get("If-Match")).toBe('"1"');
    expect(second.headers.get("If-None-Match")).toBeNull();
    expect(second.headers.get("Idempotency-Key")).not.toBe(first.headers.get("Idempotency-Key"));
    expect(server.unexpected).toEqual([]);
  });

  it("AC-UC03-01 shows the approved field errors and sends nothing while a field is wrong", async () => {
    server = mockApi({
      "GET /me/tutor-profile": notFound("TUTOR_PROFILE_NOT_FOUND"),
      "GET /me/tutor-profile/verification": notFound("TUTOR_VERIFICATION_NOT_FOUND"),
    });
    await renderProfile();
    await screen.findByText("Toán 8, đại số");

    await type("Mô tả cách dạy", "Dạy Toán 8 nhiệt tình, dễ hiểu.");
    await type("Kinh nghiệm liên quan (tháng)", "601");
    await type("Học phí cho 60 phút (VND)", "0");
    await press("Lưu nháp");

    expect(await screen.findByText("Lỗi: Mô tả cần ít nhất 50 ký tự (hiện 31).")).toBeOnTheScreen();
    expect(screen.getByText("Lỗi: Kinh nghiệm cần từ 0 đến 600 tháng.")).toBeOnTheScreen();
    expect(screen.getByText("Lỗi: Học phí phải là số nguyên lớn hơn 0.")).toBeOnTheScreen();
    expect(screen.getByText("Vui lòng sửa các ô được đánh dấu rồi gửi lại.")).toBeOnTheScreen();
    expect(puts(server)).toEqual([]);
  });

  it("puts the server's field problems next to the fields", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag(DRAFT),
      "GET /me/tutor-profile/verification": notFound("TUTOR_VERIFICATION_NOT_FOUND"),
      "PUT /me/tutor-profile": () =>
        problem(422, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.", [{ path: "rateVnd60", message: "Học phí phải là số nguyên dương." }]),
    });
    await renderProfile();
    await screen.findByDisplayValue("250.000");

    await type("Học phí cho 60 phút (VND)", "260000");
    await press("Lưu nháp");

    expect(await screen.findByText("Lỗi: Học phí phải là số nguyên dương.")).toBeOnTheScreen();
    expect(screen.getByLabelText("Học phí cho 60 phút (VND)")).toHaveDisplayValue("260000");
  });

  it("edits a draft against its ETag and shows the saved values", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag(DRAFT),
      "GET /me/tutor-profile/verification": notFound("TUTOR_VERIFICATION_NOT_FOUND"),
      "PUT /me/tutor-profile": () => withEtag({ ...DRAFT, experienceMonths: 30, revision: 6 }),
    });
    await renderProfile();

    expect(await screen.findByText("Nháp, chưa gửi duyệt")).toBeOnTheScreen();
    expect(screen.getByLabelText("Học phí cho 60 phút (VND)")).toHaveDisplayValue("250.000");
    expect(screen.getByText(`${[...DESCRIPTION].length}/3.000 ký tự, tối thiểu 50.`)).toBeOnTheScreen();

    await type("Kinh nghiệm liên quan (tháng)", "30");
    await press("Lưu nháp");

    expect(await screen.findByText("Đã lưu nháp hồ sơ.")).toBeOnTheScreen();
    const [request] = puts(server);
    expect(request.headers.get("If-Match")).toBe('"5"');
    expect(await request.json()).toEqual({ description: DESCRIPTION, experienceMonths: 30, rateVnd60: 250000, subject: "MATH8_ALGEBRA" });
  });

  it("OP-24 shows a version waiting for its verifier read-only, with no way to save", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag({ ...DRAFT, status: "PENDING" }),
      "GET /me/tutor-profile/verification": () => json(200, verification()),
    });
    await renderProfile();

    expect(await screen.findByText("Đang chờ xác minh")).toBeOnTheScreen();
    expect(
      screen.getByText("Hồ sơ đã gửi. Bản đã gửi được giữ nguyên để người xác minh xem. Phụ huynh chưa thấy hồ sơ cho tới khi được xác minh."),
    ).toBeOnTheScreen();
    expect(screen.getByText(DESCRIPTION)).toBeOnTheScreen();
    expect(screen.getByText("Kinh nghiệm 24 tháng. Học phí 250.000 ₫ / 60 phút.")).toBeOnTheScreen();
    expect(screen.queryByRole("button", { name: "Lưu nháp" })).toBeNull();
    expect(screen.queryByLabelText("Mô tả cách dạy")).toBeNull();
  });

  it("OP-24 never says parents cannot see the profile while an approved version stays public", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag({ ...DRAFT, version: 3, status: "PENDING" }),
      "GET /me/tutor-profile/verification": () => json(200, verification({ profileVersion: 3, approvedVersion: 2 })),
    });
    await renderProfile();

    expect(await screen.findByText(/Phụ huynh vẫn thấy bản hồ sơ đã duyệt trước của bạn\./)).toBeOnTheScreen();
    expect(screen.queryByText(/Phụ huynh chưa thấy hồ sơ/)).toBeNull();
  });

  it("OP-24 lets the tutor edit a version that needs information, with the reason verbatim", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag({ ...DRAFT, status: "PENDING" }),
      "GET /me/tutor-profile/verification": () =>
        json(200, verification({ state: "NEEDS_INFO", reason: "Ảnh bằng cấp bị mờ.", decidedAt: "2026-11-20T08:00:00Z" })),
      "PUT /me/tutor-profile": () => withEtag({ ...DRAFT, id: NEXT_ID, version: 2, revision: 6 }),
    });
    await renderProfile();

    expect(await screen.findByText("Người xác minh cần thêm thông tin. Lý do: Ảnh bằng cấp bị mờ.")).toBeOnTheScreen();
    expect(screen.getByText("Cần bổ sung")).toBeOnTheScreen();
    await type("Kinh nghiệm liên quan (tháng)", "25");
    await press("Lưu nháp");

    await waitFor(() => expect(puts(server)).toHaveLength(1));
    expect(puts(server)[0].headers.get("If-Match")).toBe('"5"');
  });

  describe("the screen follows newer data while it is open (Codex review of #209)", () => {
    type Answers = { profile: TutorProfile; verification: Record<string, unknown> | null };
    const serve = (answers: Answers) =>
      mockApi({
        "GET /me/tutor-profile": () => withEtag(answers.profile),
        "GET /me/tutor-profile/verification": () =>
          answers.verification ? json(200, answers.verification) : problem(404, "TUTOR_VERIFICATION_NOT_FOUND", "Không tìm thấy."),
        "PUT /me/tutor-profile": () => withEtag({ ...answers.profile, status: "DRAFT", revision: answers.profile.revision + 1 }),
      });
    const readAgain = (client: { invalidateQueries: () => Promise<void> }) =>
      act(async () => {
        await client.invalidateQueries();
      });

    it("a waiting version that turns NEEDS_INFO opens for editing, with the reason", async () => {
      const answers: Answers = { profile: { ...DRAFT, status: "PENDING" }, verification: verification() };
      server = serve(answers);
      const { queryClient } = await renderProfile();
      expect(await screen.findByText("Đang chờ xác minh")).toBeOnTheScreen();
      expect(screen.queryByLabelText("Mô tả cách dạy")).toBeNull();

      answers.verification = verification({ state: "NEEDS_INFO", reason: "Ảnh bằng cấp bị mờ.", decidedAt: "2026-11-20T08:00:00Z" });
      await readAgain(queryClient);

      expect(await screen.findByText("Người xác minh cần thêm thông tin. Lý do: Ảnh bằng cấp bị mờ.")).toBeOnTheScreen();
      await type("Kinh nghiệm liên quan (tháng)", "25");
      await press("Lưu nháp");
      await waitFor(() => expect(puts(server)).toHaveLength(1));
      expect(puts(server)[0].headers.get("If-Match")).toBe('"5"');
    });

    it("a draft sent from GS03 or another device becomes read-only", async () => {
      const answers: Answers = { profile: DRAFT, verification: null };
      server = serve(answers);
      const { queryClient } = await renderProfile();
      expect(await screen.findByLabelText("Mô tả cách dạy")).toBeOnTheScreen();

      answers.profile = { ...DRAFT, status: "PENDING", revision: 6 };
      answers.verification = verification();
      await readAgain(queryClient);

      expect(await screen.findByText("Đang chờ xác minh")).toBeOnTheScreen();
      expect(screen.queryByLabelText("Mô tả cách dạy")).toBeNull();
      expect(screen.queryByRole("button", { name: "Lưu nháp" })).toBeNull();
    });

    it("a draft sent elsewhere while the tutor is typing locks the screen, as the server would refuse the save", async () => {
      const answers: Answers = { profile: DRAFT, verification: null };
      server = serve(answers);
      const { queryClient } = await renderProfile();
      await screen.findByLabelText("Mô tả cách dạy");
      await type("Kinh nghiệm liên quan (tháng)", "30");

      answers.profile = { ...DRAFT, status: "PENDING", revision: 6 };
      answers.verification = verification();
      await readAgain(queryClient);

      expect(await screen.findByText("Đang chờ xác minh")).toBeOnTheScreen();
      expect(screen.queryByRole("button", { name: "Lưu nháp" })).toBeNull();
    });

    it("INV-23 a newer revision never replaces what the tutor typed, nor the ETag it was typed against", async () => {
      const answers: Answers = { profile: DRAFT, verification: null };
      server = serve(answers);
      const { queryClient } = await renderProfile();
      await screen.findByLabelText("Mô tả cách dạy");
      await type("Kinh nghiệm liên quan (tháng)", "30");

      answers.profile = { ...DRAFT, revision: 6, experienceMonths: 40 };
      await readAgain(queryClient);

      expect(screen.getByLabelText("Kinh nghiệm liên quan (tháng)")).toHaveDisplayValue("30");
      await press("Lưu nháp");
      await waitFor(() => expect(puts(server)).toHaveLength(1));
      expect(puts(server)[0].headers.get("If-Match")).toBe('"5"');
    });

    it("with nothing typed, a newer revision becomes the form and the ETag of the next save", async () => {
      const answers: Answers = { profile: DRAFT, verification: null };
      server = serve(answers);
      const { queryClient } = await renderProfile();
      await screen.findByLabelText("Mô tả cách dạy");

      answers.profile = { ...DRAFT, revision: 6, experienceMonths: 40 };
      await readAgain(queryClient);

      await waitFor(() => expect(screen.getByLabelText("Kinh nghiệm liên quan (tháng)")).toHaveDisplayValue("40"));
      await type("Kinh nghiệm liên quan (tháng)", "41");
      await press("Lưu nháp");
      await waitFor(() => expect(puts(server)).toHaveLength(1));
      expect(puts(server)[0].headers.get("If-Match")).toBe('"6"');
    });
  });

  it("shows why a version was rejected and lets the tutor save the next one", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag({ ...DRAFT, status: "REJECTED" }),
      "GET /me/tutor-profile/verification": () =>
        json(200, verification({ state: "REJECTED", reason: "Minh chứng không khớp mô tả.", decidedAt: "2026-11-20T08:00:00Z" })),
    });
    await renderProfile();

    expect(await screen.findByText("Hồ sơ chưa được duyệt. Lý do: Minh chứng không khớp mô tả.")).toBeOnTheScreen();
    expect(screen.getByText("Chưa được duyệt")).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Lưu nháp" })).toBeEnabled();
  });

  it("frame Đã duyệt, đang sửa: the public version above, the warning that an edit is verified again", async () => {
    const approved = { ...DRAFT, version: 2, status: "APPROVED" as const };
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag(approved),
      "GET /me/tutor-profile/verification": () =>
        json(200, verification({ profileVersion: 2, state: "VERIFIED", decidedAt: "2026-11-20T08:00:00Z", approvedVersion: 2 })),
    });
    await renderProfile();

    expect(await screen.findByRole("header", { name: "Bản đang công khai (phiên bản 2)" })).toBeOnTheScreen();
    expect(screen.queryByText(/Phụ huynh vẫn thấy/)).toBeNull();
    expect(screen.getByText("Đã xác minh: Toán 8, đại số")).toBeOnTheScreen();
    expect(screen.getByText("Kinh nghiệm 24 tháng (tự khai). Học phí 250.000 ₫ / 60 phút.")).toBeOnTheScreen();
    expect(screen.getByRole("header", { name: "Bản đang sửa" })).toBeOnTheScreen();
    expect(
      screen.getByText("Bạn đang sửa phần chuyên môn. Sau khi gửi lại, phần này phải được xác minh lại; nhãn xác minh cũ không dùng cho nội dung mới."),
    ).toBeOnTheScreen();
    expect(screen.getByText("Nháp, chưa gửi duyệt")).toBeOnTheScreen();
  });

  it("Q7 a newer draft after the approved version: parents still see that version, by its number (Đính, #14)", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag({ ...DRAFT, version: 3 }),
      "GET /me/tutor-profile/verification": () =>
        json(200, verification({ profileVersionId: NEXT_ID, profileVersion: 2, state: "VERIFIED", decidedAt: "2026-11-20T08:00:00Z", approvedVersion: 2 })),
    });
    await renderProfile();

    expect(await screen.findByText("Phụ huynh vẫn thấy bản hồ sơ đã duyệt trước của bạn (phiên bản 2).")).toBeOnTheScreen();
    // The text of version 2 is not readable from here, so its card is not drawn.
    expect(screen.queryByRole("header", { name: /Bản đang công khai/ })).toBeNull();
    expect(screen.getByRole("header", { name: "Bản đang sửa" })).toBeOnTheScreen();
    expect(screen.getByText("Nháp, chưa gửi duyệt")).toBeOnTheScreen();
  });

  it("INV-23 on a stale ETag reloads, keeps what the tutor typed and saves against the new revision", async () => {
    let reads = 0;
    let saves = 0;
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag(reads++ === 0 ? DRAFT : { ...DRAFT, rateVnd60: 280000, revision: 7 }),
      "GET /me/tutor-profile/verification": notFound("TUTOR_VERIFICATION_NOT_FOUND"),
      "PUT /me/tutor-profile": () =>
        saves++ === 0
          ? problem(412, "VERSION_CONFLICT", "Dữ liệu đã thay đổi. Vui lòng tải lại.")
          : withEtag({ ...DRAFT, experienceMonths: 30, rateVnd60: 280000, revision: 8 }),
    });
    await renderProfile();
    await screen.findByDisplayValue("250.000");

    await type("Kinh nghiệm liên quan (tháng)", "30");
    await press("Lưu nháp");
    expect(
      await screen.findByText("Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."),
    ).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Lưu nháp" })).toBeDisabled();

    await press("Tải lại");
    // The typed field stays; the untouched price takes the newer value from the server.
    await waitFor(() => expect(screen.getByLabelText("Học phí cho 60 phút (VND)")).toHaveDisplayValue("280.000"));
    expect(screen.getByLabelText("Kinh nghiệm liên quan (tháng)")).toHaveDisplayValue("30");

    await press("Lưu nháp");
    await waitFor(() => expect(puts(server)).toHaveLength(2));
    expect(puts(server)[1].headers.get("If-Match")).toBe('"7"');
  });

  it("INV-20 retries an unknown outcome with the same key, then saves on from the confirmed revision", async () => {
    let saves = 0;
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag(DRAFT),
      "GET /me/tutor-profile/verification": notFound("TUTOR_VERIFICATION_NOT_FOUND"),
      "PUT /me/tutor-profile": () => {
        saves += 1;
        if (saves === 1) return problem(503, "TEMPORARILY_UNAVAILABLE", "Hệ thống đang bận.");
        return withEtag({ ...DRAFT, experienceMonths: 30, revision: 5 + saves });
      },
    });
    await renderProfile();
    await screen.findByDisplayValue("250.000");

    await type("Kinh nghiệm liên quan (tháng)", "30");
    await press("Lưu nháp");
    expect(await screen.findByRole("button", { name: "Thử lại" })).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Lưu nháp" })).toBeDisabled();

    await press("Thử lại");
    expect(await screen.findByText("Đã lưu nháp hồ sơ.")).toBeOnTheScreen();
    const [lost, retried] = puts(server);
    expect(retried.headers.get("Idempotency-Key")).toBe(lost.headers.get("Idempotency-Key"));
    expect(retried.headers.get("If-Match")).toBe('"5"');

    await type("Kinh nghiệm liên quan (tháng)", "31");
    await press("Lưu nháp");
    await waitFor(() => expect(puts(server)).toHaveLength(3));
    expect(puts(server)[2].headers.get("If-Match")).toBe('"7"');
  });

  it("Tiếp goes to GS03 at once from a saved draft, and saves first when something changed", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag(DRAFT),
      "GET /me/tutor-profile/verification": notFound("TUTOR_VERIFICATION_NOT_FOUND"),
      "PUT /me/tutor-profile": () => withEtag({ ...DRAFT, experienceMonths: 30, revision: 6 }),
    });
    const { onNext } = await renderProfile();
    await screen.findByDisplayValue("250.000");

    await press("Tiếp: tải minh chứng");
    expect(onNext).toHaveBeenCalledTimes(1);
    expect(puts(server)).toEqual([]);

    await type("Kinh nghiệm liên quan (tháng)", "30");
    await press("Tiếp: tải minh chứng");
    await waitFor(() => expect(onNext).toHaveBeenCalledTimes(2));
    expect(puts(server)).toHaveLength(1);
  });

  it("Tiếp from an approved version saves the next draft before going on", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag({ ...DRAFT, version: 2, status: "APPROVED" }),
      "GET /me/tutor-profile/verification": () =>
        json(200, verification({ profileVersion: 2, state: "VERIFIED", decidedAt: "2026-11-20T08:00:00Z", approvedVersion: 2 })),
      "PUT /me/tutor-profile": () => withEtag({ ...DRAFT, id: NEXT_ID, version: 3, revision: 6 }),
    });
    const { onNext } = await renderProfile();
    await screen.findByText("Bản đang sửa");

    await press("Tiếp: tải minh chứng");
    await waitFor(() => expect(onNext).toHaveBeenCalledTimes(1));
    expect(puts(server)).toHaveLength(1);
  });

  it("INV-06 lost access says nothing about the profile", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => problem(403, "ACCESS_UNAVAILABLE", "Không có quyền"),
      "GET /me/tutor-profile/verification": notFound("TUTOR_VERIFICATION_NOT_FOUND"),
    });
    await renderProfile();

    expect(await screen.findByText("Bạn không còn quyền xem nội dung này.")).toBeOnTheScreen();
    expect(screen.queryByLabelText("Mô tả cách dạy")).toBeNull();
  });
});
