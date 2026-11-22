import { act, fireEvent, screen, waitFor, within } from "@testing-library/react-native";
import { API_BASE_URL } from "@/shared/api/client";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import type { TutorProfile } from "../../tutor-profile/api";
import type { PickedFile } from "../upload";
import { TutorEvidenceScreen } from "./TutorEvidenceScreen";

// Made-up profile and file names only (AGENTS.md).
const DRAFT: TutorProfile = {
  id: "5d2f8a61-3b4c-4d7e-8f90-1a2b3c4d5e6f",
  version: 1,
  revision: 5,
  status: "DRAFT",
  description: "Tôi dạy Toán THCS 2 năm, tập trung vào đại số lớp 8, chữa lỗi ngay trong buổi học.",
  experienceMonths: 24,
  rateVnd60: 250000,
};
const UPLOAD_ID = "8a5c1d94-6e7f-4a01-9c23-4d5e6f708192";
const VERIFICATION_ID = "7f4b0c83-5d6e-4f90-8b12-3c4d5e6f7081";
const BYTES = new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x37]); // "%PDF-1.7"
/** SHA-256 by the runtime's Web Crypto, since the native module of expo-crypto does not run in Jest. */
const nodeSha = async (bytes: Uint8Array<ArrayBuffer>) =>
  Array.from(new Uint8Array(await globalThis.crypto.subtle.digest("SHA-256", bytes)), (b) => b.toString(16).padStart(2, "0")).join("");
// SHA-256 of "%PDF-1.7", computed with sha256sum outside the code under test.
const SHA = "86edbaa24831badfa0a8b04bb410141e2ee4182b6d0014493fe262a7a331c20b";

function verification(overrides: Record<string, unknown> = {}) {
  return {
    id: VERIFICATION_ID,
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

function withEtag(profile: TutorProfile) {
  return new Response(JSON.stringify(profile), {
    status: 200,
    headers: { "Content-Type": "application/json", ETag: `"${profile.revision}"` },
  });
}

const noVerification = () => problem(404, "TUTOR_VERIFICATION_NOT_FOUND", "Không tìm thấy.");
const file = (overrides: Partial<PickedFile> = {}): PickedFile => ({
  name: "bang-tot-nghiep-dai-hoc-su-pham.pdf",
  mediaType: "application/pdf",
  sizeBytes: 2_516_582,
  read: async () => BYTES,
  ...overrides,
});

/** The three upload steps answering as the backend does, the file ending READY. */
const uploadRoutes = (uploadUrl = `${API_BASE_URL}/me/tutor-profile/evidence-uploads/${UPLOAD_ID}/content`) => ({
  "POST /me/tutor-profile/evidence-uploads": () => json(201, { uploadId: UPLOAD_ID, uploadUrl, expiresAt: "2026-11-22T10:00:00Z" }),
  "PUT /me/tutor-profile/evidence-uploads/{uploadId}/content": () =>
    json(200, { id: UPLOAD_ID, state: "UPLOADING", expiresAt: "2026-11-22T10:00:00Z" }),
  "POST /me/tutor-profile/evidence-uploads/{uploadId}/complete": () =>
    json(200, { id: UPLOAD_ID, state: "READY", expiresAt: "2026-11-22T10:00:00Z" }),
});

async function renderEvidence(picked: PickedFile | null = file()) {
  const handlers = { onBack: jest.fn(), onEditProfile: jest.fn(), pickFile: jest.fn(async () => picked) };
  await renderScreen(<TutorEvidenceScreen {...handlers} hash={nodeSha} />, { session: fakeSession("TUTOR") });
  return handlers;
}

const press = (name: string) => act(async () => fireEvent.press(screen.getByRole("button", { name })));
const sent = (server: ReturnType<typeof mockApi>, method: string, end: string) =>
  server.requests.filter((r) => r.method === method && new URL(r.url).pathname.endsWith(end));

describe("GS03 Minh chứng và kết quả xác minh", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it("starts with no file and nothing to send", async () => {
    server = mockApi({ "GET /me/tutor-profile": () => withEtag(DRAFT), "GET /me/tutor-profile/verification": noVerification });
    await renderEvidence();

    expect(await screen.findByText("Chưa gửi duyệt")).toBeOnTheScreen();
    expect(screen.getByText("Chưa có tệp nào. Tải ít nhất 1 tệp để gửi hồ sơ đi xác minh.")).toBeOnTheScreen();
    expect(screen.getByText("Gửi duyệt cần ít nhất 1 tệp ở trạng thái Sẵn sàng.")).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Gửi duyệt" })).toBeDisabled();
  });

  it("FR-07 uploads a file in three steps that declare the same size and SHA-256, each with its own key", async () => {
    server = mockApi({ "GET /me/tutor-profile": () => withEtag(DRAFT), "GET /me/tutor-profile/verification": noVerification, ...uploadRoutes() });
    await renderEvidence();
    await screen.findByText("Chưa gửi duyệt");

    await press("Tải minh chứng");

    expect(await screen.findByText("Sẵn sàng")).toBeOnTheScreen();
    expect(screen.getByText("PDF, 2,4 MB")).toBeOnTheScreen();
    const [create] = sent(server, "POST", "/evidence-uploads");
    const [content] = sent(server, "PUT", "/content");
    const [complete] = sent(server, "POST", "/complete");
    expect(await create.json()).toEqual({ mediaType: "application/pdf", sizeBytes: BYTES.byteLength, sha256: SHA });
    expect(new URL(content.url).pathname).toBe(`/api/v1/me/tutor-profile/evidence-uploads/${UPLOAD_ID}/content`);
    expect(content.headers.get("Content-Type")).toBe("application/pdf");
    expect(new Uint8Array(await content.arrayBuffer())).toEqual(BYTES);
    expect(await complete.json()).toEqual({ sizeBytes: BYTES.byteLength, sha256: SHA });
    const keys = [create, content, complete].map((r) => r.headers.get("Idempotency-Key"));
    keys.forEach((key) => expect(key).toMatch(/^[0-9a-f]{32}$/));
    expect(new Set(keys).size).toBe(3);
    expect(screen.getByRole("button", { name: "Tải thêm minh chứng" })).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Gửi duyệt" })).toBeEnabled();
  });

  it("G3-D07-04 refuses a file of another kind or over 5 MB on the device, without a request", async () => {
    server = mockApi({ "GET /me/tutor-profile": () => withEtag(DRAFT), "GET /me/tutor-profile/verification": noVerification });
    await renderEvidence(
      file({ name: "ho-so-giang-day.docx", mediaType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", sizeBytes: 655_360 }),
    );
    await screen.findByText("Chưa gửi duyệt");

    await press("Tải minh chứng");

    expect(await screen.findByText("Lỗi")).toBeOnTheScreen();
    expect(screen.getByText("DOCX, 640 KB")).toBeOnTheScreen();
    expect(
      screen.getByText("Không nhận được tệp này. Kiểm tra định dạng (PDF, JPEG, PNG) và dung lượng (tối đa 5 MB) rồi tải lại."),
    ).toBeOnTheScreen();
    expect(server.requests.filter((r) => r.method !== "GET")).toEqual([]);
  });

  it("never sends the file or the session to an upload URL on another origin", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag(DRAFT),
      "GET /me/tutor-profile/verification": noVerification,
      ...uploadRoutes(`https://store.example/evidence/${UPLOAD_ID}`),
    });
    await renderEvidence();
    await screen.findByText("Chưa gửi duyệt");

    await press("Tải minh chứng");

    expect(await screen.findByText("Lỗi")).toBeOnTheScreen();
    expect(sent(server, "PUT", "/content")).toEqual([]);
    expect(server.unexpected).toEqual([]);
  });

  it("FR-07 asks first, then sends the draft with If-Match and the ready files, and shows it waiting", async () => {
    let submitted = false;
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag(submitted ? { ...DRAFT, status: "PENDING", revision: 6 } : DRAFT),
      "GET /me/tutor-profile/verification": () => (submitted ? json(200, verification()) : noVerification()),
      ...uploadRoutes(),
      "POST /me/tutor-profile/submit": () => {
        submitted = true;
        return json(201, { id: VERIFICATION_ID, profileVersionId: DRAFT.id, assignedTo: null, status: "PENDING", scope: "Toán 8, đại số", revision: 1 });
      },
    });
    await renderEvidence();
    await screen.findByText("Chưa gửi duyệt");
    await press("Tải minh chứng");
    await screen.findByText("Sẵn sàng");

    await press("Gửi duyệt");
    expect(screen.getByRole("header", { name: "Gửi hồ sơ để xác minh?" })).toBeOnTheScreen();
    expect(
      screen.getByText(
        "Bản hồ sơ hiện tại và 1 tệp minh chứng sẽ được giữ nguyên để người xác minh xem. Phụ huynh chưa thấy hồ sơ cho tới khi được xác minh.",
      ),
    ).toBeOnTheScreen();
    expect(sent(server, "POST", "/submit")).toEqual([]);

    const sheet = screen.getByRole("header", { name: "Gửi hồ sơ để xác minh?" }).parent!;
    await act(async () => fireEvent.press(within(sheet).getByRole("button", { name: "Gửi duyệt" })));

    expect(await screen.findByText("Đã gửi hồ sơ đi xác minh.")).toBeOnTheScreen();
    expect(screen.getByText("Đang chờ xác minh")).toBeOnTheScreen();
    expect(screen.getByText("Người xác minh sẽ kiểm tra phạm vi Toán 8, đại số. Kết quả hiện ở đây và trong Thông báo.")).toBeOnTheScreen();
    const [request] = sent(server, "POST", "/submit");
    expect(request.headers.get("If-Match")).toBe('"5"');
    expect(await request.json()).toEqual({ profileVersionId: DRAFT.id, evidenceUploadIds: [UPLOAD_ID] });
  });

  it("marks a file the server refuses at submission with the server's message", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag(DRAFT),
      "GET /me/tutor-profile/verification": noVerification,
      ...uploadRoutes(),
      "POST /me/tutor-profile/submit": () =>
        problem(422, "VALIDATION_FAILED", "Dữ liệu chưa hợp lệ.", [
          { path: "evidenceUploadIds[0]", message: "Minh chứng này không dùng được. Vui lòng chọn lại." },
        ]),
    });
    await renderEvidence();
    await screen.findByText("Chưa gửi duyệt");
    await press("Tải minh chứng");
    await screen.findByText("Sẵn sàng");
    await press("Gửi duyệt");
    const sheet = screen.getByRole("header", { name: "Gửi hồ sơ để xác minh?" }).parent!;
    await act(async () => fireEvent.press(within(sheet).getByRole("button", { name: "Gửi duyệt" })));

    expect(await screen.findByText("Minh chứng này không dùng được. Vui lòng chọn lại.")).toBeOnTheScreen();
    await act(async () => fireEvent.press(within(sheet).getByRole("button", { name: "Quay lại" })));
    expect(screen.getByRole("button", { name: "Gửi duyệt" })).toBeDisabled();
  });

  it("OP-24 sends again after Cần bổ sung: the waiting version is saved as the next draft first", async () => {
    const next = { ...DRAFT, id: "6e3a9b72-4c5d-4e8f-9a01-2b3c4d5e6f70", version: 2, revision: 6 };
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag({ ...DRAFT, status: "PENDING" }),
      "GET /me/tutor-profile/verification": () =>
        json(200, verification({ state: "NEEDS_INFO", reason: "Ảnh bằng cấp bị mờ.", decidedAt: "2026-11-20T08:00:00Z" })),
      ...uploadRoutes(),
      "PUT /me/tutor-profile": () => withEtag(next),
      "POST /me/tutor-profile/submit": () =>
        json(201, { id: VERIFICATION_ID, profileVersionId: next.id, assignedTo: null, status: "PENDING", scope: "Toán 8, đại số", revision: 1 }),
    });
    await renderEvidence();

    expect(await screen.findByText("Cần bổ sung")).toBeOnTheScreen();
    expect(screen.getByText("Người xác minh cần thêm thông tin trước khi kết luận.")).toBeOnTheScreen();
    expect(screen.getByText("Lý do: Ảnh bằng cấp bị mờ.")).toBeOnTheScreen();
    await press("Tải minh chứng");
    await screen.findByText("Sẵn sàng");
    await press("Gửi lại để xác minh");
    const sheet = screen.getByRole("header", { name: "Gửi hồ sơ để xác minh?" }).parent!;
    await act(async () => fireEvent.press(within(sheet).getByRole("button", { name: "Gửi lại để xác minh" })));

    await waitFor(() => expect(sent(server, "POST", "/submit")).toHaveLength(1));
    const [save] = sent(server, "PUT", "/me/tutor-profile");
    expect(save.headers.get("If-Match")).toBe('"5"');
    expect(await save.json()).toEqual({
      description: DRAFT.description,
      experienceMonths: 24,
      rateVnd60: 250000,
      subject: "MATH8_ALGEBRA",
    });
    const [submit] = sent(server, "POST", "/submit");
    expect(submit.headers.get("If-Match")).toBe('"6"');
    expect(await submit.json()).toEqual({ profileVersionId: next.id, evidenceUploadIds: [UPLOAD_ID] });
  });

  it("INV-20 retries a submission with an unknown outcome with the same key", async () => {
    let calls = 0;
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag(DRAFT),
      "GET /me/tutor-profile/verification": noVerification,
      ...uploadRoutes(),
      "POST /me/tutor-profile/submit": () =>
        ++calls === 1
          ? problem(503, "TEMPORARILY_UNAVAILABLE", "Hệ thống đang bận.")
          : json(201, { id: VERIFICATION_ID, profileVersionId: DRAFT.id, assignedTo: null, status: "PENDING", scope: "Toán 8, đại số", revision: 1 }),
    });
    await renderEvidence();
    await screen.findByText("Chưa gửi duyệt");
    await press("Tải minh chứng");
    await screen.findByText("Sẵn sàng");
    await press("Gửi duyệt");
    const sheet = screen.getByRole("header", { name: "Gửi hồ sơ để xác minh?" }).parent!;
    await act(async () => fireEvent.press(within(sheet).getByRole("button", { name: "Gửi duyệt" })));

    await act(async () => fireEvent.press(await within(sheet).findByRole("button", { name: "Thử lại" })));

    await waitFor(() => expect(sent(server, "POST", "/submit")).toHaveLength(2));
    const [lost, retried] = sent(server, "POST", "/submit");
    expect(retried.headers.get("Idempotency-Key")).toBe(lost.headers.get("Idempotency-Key"));
  });

  it("allows at most 10 files a submission", async () => {
    let next = 0;
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag(DRAFT),
      "GET /me/tutor-profile/verification": noVerification,
      "POST /me/tutor-profile/evidence-uploads": () => {
        const id = `8a5c1d94-6e7f-4a01-9c23-4d5e6f7081${String(next++).padStart(2, "0")}`;
        return json(201, { uploadId: id, uploadUrl: `${API_BASE_URL}/me/tutor-profile/evidence-uploads/${id}/content`, expiresAt: "2026-11-22T10:00:00Z" });
      },
      "PUT /me/tutor-profile/evidence-uploads/{uploadId}/content": (r) =>
        json(200, { id: new URL(r.url).pathname.split("/")[6], state: "UPLOADING", expiresAt: "2026-11-22T10:00:00Z" }),
      "POST /me/tutor-profile/evidence-uploads/{uploadId}/complete": (r) =>
        json(200, { id: new URL(r.url).pathname.split("/")[6], state: "READY", expiresAt: "2026-11-22T10:00:00Z" }),
    });
    await renderEvidence();
    await screen.findByText("Chưa gửi duyệt");

    await press("Tải minh chứng");
    for (let i = 1; i < 10; i++) {
      await waitFor(() => expect(screen.getAllByText("Sẵn sàng")).toHaveLength(i));
      await press("Tải thêm minh chứng");
    }

    await waitFor(() => expect(screen.getAllByText("Sẵn sàng")).toHaveLength(10));
    expect(screen.getByText("Đã đủ 10 tệp, không thể tải thêm.")).toBeOnTheScreen();
    expect(screen.getByRole("button", { name: "Tải thêm minh chứng" })).toBeDisabled();
  });

  it("frame Đã duyệt: the verified scope and what the label does not promise", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag({ ...DRAFT, status: "APPROVED" }),
      "GET /me/tutor-profile/verification": () =>
        json(200, verification({ state: "VERIFIED", decidedAt: "2026-11-20T08:00:00Z", approvedVersion: 1 })),
    });
    await renderEvidence();

    expect(await screen.findByText("Đã duyệt")).toBeOnTheScreen();
    expect(screen.getByText("Đã xác minh: Toán 8, đại số")).toBeOnTheScreen();
    expect(
      screen.getByText("Phụ huynh tìm gia sư sẽ thấy nhãn này cùng phạm vi đã kiểm. Nhãn không bảo đảm chất lượng giảng dạy, và minh chứng gốc không công khai."),
    ).toBeOnTheScreen();
    expect(screen.getByText("Kinh nghiệm và học phí vẫn hiện là thông tin tự khai.")).toBeOnTheScreen();
    expect(screen.queryByRole("button", { name: "Gửi duyệt" })).toBeNull();
  });

  it("frame Chưa được duyệt: the reason verbatim and a way back to GS02", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag({ ...DRAFT, status: "REJECTED" }),
      "GET /me/tutor-profile/verification": () =>
        json(200, verification({ state: "REJECTED", reason: "Minh chứng không khớp mô tả.", decidedAt: "2026-11-20T08:00:00Z" })),
    });
    const { onEditProfile } = await renderEvidence();

    expect(await screen.findByText("Chưa được duyệt")).toBeOnTheScreen();
    expect(screen.getByText("Hồ sơ chưa được xác minh nên chưa xuất hiện khi phụ huynh tìm gia sư.")).toBeOnTheScreen();
    expect(screen.getByText("Lý do: Minh chứng không khớp mô tả.")).toBeOnTheScreen();
    await press("Sửa hồ sơ và gửi lại");
    expect(onEditProfile).toHaveBeenCalledTimes(1);
  });

  it("OP-24 never says a refused edit hides the profile while an approved version stays public", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => withEtag({ ...DRAFT, version: 3, status: "REJECTED" }),
      "GET /me/tutor-profile/verification": () =>
        json(200, verification({ profileVersion: 3, state: "REJECTED", reason: "Thiếu minh chứng.", decidedAt: "2026-11-20T08:00:00Z", approvedVersion: 2 })),
    });
    await renderEvidence();

    expect(await screen.findByText("Phụ huynh vẫn thấy bản hồ sơ đã duyệt trước của bạn. Bản sửa: chưa được duyệt.")).toBeOnTheScreen();
    expect(screen.queryByText(/chưa xuất hiện/)).toBeNull();
  });

  it("sends the tutor to GS02 when there is no profile yet", async () => {
    server = mockApi({
      "GET /me/tutor-profile": () => problem(404, "TUTOR_PROFILE_NOT_FOUND", "Chưa có hồ sơ gia sư."),
      "GET /me/tutor-profile/verification": noVerification,
    });
    const { onEditProfile, pickFile } = await renderEvidence();

    await act(async () => fireEvent.press(await screen.findByRole("button", { name: "Mở hồ sơ chuyên môn" })));
    expect(onEditProfile).toHaveBeenCalledTimes(1);
    expect(pickFile).not.toHaveBeenCalled();
  });
});
