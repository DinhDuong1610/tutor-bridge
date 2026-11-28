import { act, fireEvent, screen, waitFor } from "@testing-library/react-native";
import type { Need } from "@/features/needs/api";
import { SelectedStudentProvider } from "@/features/students/selection";
import { json, mockApi, problem } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { fakeSession } from "@/shared/test/session";
import type { Offer } from "../api";
import { NeedOffersScreen } from "./NeedOffersScreen";
import { OfferDetailScreen } from "./OfferDetailScreen";

// Fake children, tutors and texts only (AGENTS.md).
const NOW = () => new Date("2026-11-18T11:00:00Z");
const MINH = { id: "0b7c4f3e-2a51-4a8e-9a43-6d1f0c2b9e10", displayName: "Minh An", grade: 8, revision: 1, shortGoal: "" };
const NEED_ID = "3d9e6b5a-4c73-4caf-9c65-8f3b2e4d1a32";
const OTHER_NEED = "2a7c6b5d-4e3f-4d2c-9b1a-0f9e8d7c6b5a";
const GOAL = "Toán 8, hằng đẳng thức và phân thức";
const LAN = "7b3c0f9e-8ab7-40ed-9a09-2d7f6c8b5e01";
const MINH_TUTOR = "7b3c0f9e-8ab7-40ed-9a09-2d7f6c8b5e02";
const HA = "7b3c0f9e-8ab7-40ed-9a09-2d7f6c8b5e03";
const CLASS_ID = "5c6d7e8f-9a0b-4c1d-8e2f-3a4b5c6d7e8f";
const NAMES: Record<string, string> = { [LAN]: "Nguyễn Thị Lan", [MINH_TUTOR]: "Trần Văn Minh" }; // HA is hidden now

const need: Need = {
  id: NEED_ID,
  studentId: MINH.id,
  revision: 2,
  versionId: "4e0f7c6b-5d84-4dba-8d76-9a4c3f5e2b43",
  status: "CONFIRMED",
  draft: { goal: GOAL, durationMinutes: 90, frequencyPer7Days: 2, budgetVndSession: 400000, cycleStartDate: "2026-11-18" },
  studentCalendarId: null,
};

const SCHEDULE = ["2026-11-18", "2026-11-23", "2026-11-25", "2026-11-30", "2026-12-02", "2026-12-07", "2026-12-09", "2026-12-14"].map(
  (day) => ({ start: new Date(`${day}T18:00:00+07:00`).toISOString(), end: new Date(`${day}T19:30:00+07:00`).toISOString() }),
);

let seq = 0;
function offer(tutorId: string, extra: Partial<Offer> = {}): Offer {
  seq += 1;
  return {
    id: `1d2e3f4a-5b6c-4d7e-8f9a-0b1c2d3e4f${String(seq).padStart(2, "0")}`,
    needId: NEED_ID,
    needVersionId: need.versionId,
    revision: 1,
    state: "PENDING",
    tutorId,
    goalSnapshot: GOAL,
    durationMinutes: 90,
    frequencyPer7Days: 2,
    cycleStartDate: "2026-11-18",
    rateVnd60: 250000,
    priceVndSession: 375000,
    schedule: SCHEDULE,
    sentAt: "2026-11-18T11:00:00Z",
    expiresAt: "2026-11-20T11:00:00Z",
    reasonCode: null,
    classId: null,
    ...extra,
  };
}

const withEtag = (body: unknown, revision: number) =>
  new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json", ETag: `"${revision}"` } });

function routes(offers: Offer[], extra: Record<string, (request: Request) => Response> = {}) {
  return mockApi({
    "GET /me/offers": () => json(200, { items: offers, nextCursor: null }),
    "GET /needs/{needId}": () => withEtag(need, 2),
    "GET /students": () => json(200, { items: [MINH], nextCursor: null }),
    "GET /tutors/{tutorId}/public-profile": (request) => {
      const id = new URL(request.url).pathname.split("/")[4];
      return NAMES[id]
        ? json(200, { tutorId: id, profileVersionId: "8c4d1a0f-9bc8-41fe-8b1a-3e8a7d9c6f76", displayName: NAMES[id], description: "", rateVnd60: 250000, experienceMonths: 24, experienceLabel: "SELF_DECLARED", verificationScopes: [] })
        : problem(404, "TUTOR_NOT_FOUND", "Không tìm thấy gia sư.");
    },
    ...extra,
  });
}

const press = (name: string) => act(async () => fireEvent.press(screen.getByRole("button", { name })));
const pressInSheet = (name: string) =>
  act(async () => {
    const buttons = screen.getAllByRole("button", { name });
    fireEvent.press(buttons[buttons.length - 1]);
  });

async function renderList(cancelledOfferId?: string) {
  const handlers = {
    onBack: jest.fn(),
    onOpenOffer: jest.fn(),
    onOpenClass: jest.fn(),
    onFindTutors: jest.fn(),
    onOtherChild: jest.fn(),
    onManageStudents: jest.fn(),
  };
  await renderScreen(
    <SelectedStudentProvider>
      <NeedOffersScreen needId={NEED_ID} cancelledOfferId={cancelledOfferId} {...handlers} now={NOW} />
    </SelectedStudentProvider>,
    { session: fakeSession("PARENT") },
  );
  return handlers;
}

async function renderDetail(offerId: string) {
  const handlers = {
    onBack: jest.fn(),
    onFindTutors: jest.fn(),
    onOpenClass: jest.fn(),
    onCancelled: jest.fn(),
    onOtherChild: jest.fn(),
    onManageStudents: jest.fn(),
  };
  await renderScreen(
    <SelectedStudentProvider>
      <OfferDetailScreen offerId={offerId} {...handlers} />
    </SelectedStudentProvider>,
    { session: fakeSession("PARENT") },
  );
  return handlers;
}

describe("PH09 Danh sách đề nghị", () => {
  let api: ReturnType<typeof mockApi>;
  afterEach(() => api.restore());

  it("lists this need's offers, waiting first, each with its label, terms and the line of its state", async () => {
    const pending = offer(LAN);
    const rejected = offer(MINH_TUTOR, { state: "REJECTED", reasonCode: "NOT_AVAILABLE", revision: 2 });
    const expired = offer(HA, { state: "EXPIRED", reasonCode: "OFFER_EXPIRED", expiresAt: "2026-11-15T11:00:00Z" });
    const elsewhere = offer(LAN, { needId: OTHER_NEED });
    api = routes([rejected, expired, pending, elsewhere]);
    const { onOpenOffer } = await renderList();

    expect(await screen.findByText(`Nhu cầu: ${GOAL}`)).toBeTruthy();
    // Names come from the public profiles, read after the list.
    expect(await screen.findByText("Trần Văn Minh")).toBeTruthy();
    expect(screen.getByRole("header", { name: "Đề nghị đã gửi" })).toBeTruthy();
    expect(
      screen.getByText("Bạn có thể gửi đề nghị cho nhiều gia sư. Khi một gia sư chấp nhận, lớp được chốt và các đề nghị còn lại tự kết thúc."),
    ).toBeTruthy();
    for (const text of [
      "Đang chờ gia sư trả lời",
      "Nguyễn Thị Lan",
      "Hết hạn lúc 18:00 20/11/2026 (còn 2 ngày). Chưa giữ chỗ.",
      "Gia sư đã từ chối",
      "Trần Văn Minh",
      "Lý do: không còn rảnh các giờ này.",
      "Đã hết hạn",
      "Hết hạn lúc 18:00 15/11/2026, gia sư chưa trả lời.",
    ]) {
      expect(screen.getByText(text)).toBeTruthy();
    }
    // Another need's offer is not this need's; a tutor hidden since has no name to show.
    expect(screen.getAllByText("8 buổi, 90 phút, 375.000 ₫ / buổi")).toHaveLength(3);
    expect(screen.getByText("Gia sư")).toBeTruthy();
    // Only the waiting offer opens; the answered ones are kept for reference.
    expect(screen.getAllByRole("button", { name: /^Xem chi tiết/ })).toHaveLength(1);
    await press("Xem chi tiết đề nghị gửi Nguyễn Thị Lan");
    expect(onOpenOffer).toHaveBeenCalledWith(pending.id);
  });

  it("an accepted offer opens its class, and the one the system ended says why", async () => {
    const accepted = offer(LAN, { state: "ACCEPTED", classId: CLASS_ID, revision: 2 });
    const ended = offer(MINH_TUTOR, { state: "CANCELLED", reasonCode: "NEED_CLOSED", revision: 2 });
    api = routes([ended, accepted]);
    const { onOpenClass } = await renderList();

    expect(await screen.findByText("Đã chốt lớp")).toBeTruthy();
    expect(await screen.findByText("Nguyễn Thị Lan")).toBeTruthy();
    expect(screen.getByText("Lớp và đủ 8 buổi đã được đặt.")).toBeTruthy();
    expect(screen.getByText("Đã kết thúc")).toBeTruthy();
    expect(screen.getByText("Đã kết thúc vì nhu cầu này đã chốt lớp với gia sư khác.")).toBeTruthy();
    await press("Xem lớp với Nguyễn Thị Lan");
    expect(onOpenClass).toHaveBeenCalledWith(CLASS_ID);
  });

  it("no offer yet: says so and leads to the tutors of the need", async () => {
    api = routes([offer(LAN, { needId: OTHER_NEED })]);
    const { onFindTutors } = await renderList();

    expect(
      await screen.findByText("Bạn chưa gửi đề nghị nào cho nhu cầu này. Chọn gia sư trong danh sách gợi ý để gửi."),
    ).toBeTruthy();
    await press("Xem gia sư phù hợp");
    expect(onFindTutors).toHaveBeenCalled();
  });

  it("back from a cancel, it names the offer that was cancelled", async () => {
    const cancelled = offer(LAN, { state: "CANCELLED", reasonCode: "PARENT_CANCELLED", revision: 2 });
    api = routes([cancelled]);
    await renderList(cancelled.id);

    expect(await screen.findByText("Đã hủy đề nghị gửi Nguyễn Thị Lan.")).toBeTruthy();
    expect(screen.getByText("Bạn đã hủy đề nghị này.")).toBeTruthy();
  });
});

describe("PH09 Chi tiết đề nghị", () => {
  let api: ReturnType<typeof mockApi>;
  afterEach(() => api.restore());

  it("shows the waiting offer's terms as sent, and cancels it against its ETag after asking", async () => {
    const pending = offer(LAN);
    api = routes([], {
      "GET /offers/{offerId}": () => withEtag(pending, 1),
      "POST /offers/{offerId}/cancel": () => json(200, { ...pending, state: "CANCELLED", reasonCode: "PARENT_CANCELLED", revision: 2 }),
    });
    const { onCancelled, onFindTutors } = await renderDetail(pending.id);

    expect(await screen.findByRole("header", { name: "Nguyễn Thị Lan" })).toBeTruthy();
    expect(screen.getByRole("header", { name: "Chi tiết đề nghị" })).toBeTruthy();
    for (const text of [
      "Đang chờ gia sư trả lời",
      "Hết hạn lúc 18:00 20/11/2026 nếu gia sư chưa trả lời. Đề nghị chưa giữ chỗ.",
      "Điều kiện đã gửi (không đổi được)",
      `Mục tiêu: ${GOAL}`,
      "375.000 ₫ / buổi 90 phút",
      "8 buổi, Thứ 2 và Thứ 4, 18:00–19:30, từ 18/11 đến 14/12/2026",
      "Vòng nét đứt: buổi trong đề nghị, chưa chốt, chưa giữ chỗ.",
      "Gửi lúc 18:00 18/11/2026",
    ]) {
      expect(screen.getByText(text)).toBeTruthy();
    }
    await press("Gửi cho gia sư khác");
    expect(onFindTutors).toHaveBeenCalledWith(NEED_ID);

    await press("Hủy đề nghị");
    expect(screen.getByRole("header", { name: "Hủy đề nghị gửi Nguyễn Thị Lan?" })).toBeTruthy();
    expect(
      screen.getByText("Gia sư sẽ không chấp nhận được đề nghị này nữa. Các đề nghị khác của nhu cầu này không bị ảnh hưởng."),
    ).toBeTruthy();
    await pressInSheet("Hủy đề nghị");

    const post = api.requests.find((r) => r.method === "POST");
    expect(post && new URL(post.url).pathname).toBe(`/api/v1/offers/${pending.id}/cancel`);
    expect(post?.headers.get("If-Match")).toBe('"1"');
    expect(post?.headers.get("Idempotency-Key")).toBeTruthy();
    expect(await post?.json()).toEqual({ reasonCode: "PARENT_CANCELLED" });
    expect(onCancelled).toHaveBeenCalledTimes(1);
    expect(onCancelled).toHaveBeenCalledWith(NEED_ID, pending.id);
  });

  it("the tutor answered meanwhile (409 OFFER_NOT_PENDING): the offer is read again and shown as it is now", async () => {
    let stored = offer(LAN);
    api = routes([], {
      "GET /offers/{offerId}": () => withEtag(stored, stored.revision),
      "POST /offers/{offerId}/cancel": () => {
        stored = { ...stored, state: "ACCEPTED", classId: CLASS_ID, revision: 2 };
        return problem(409, "OFFER_NOT_PENDING", "Đề nghị đã kết thúc nên không đổi được nữa.");
      },
    });
    const { onCancelled, onOpenClass } = await renderDetail(stored.id);
    await screen.findByRole("header", { name: "Nguyễn Thị Lan" });

    await press("Hủy đề nghị");
    await pressInSheet("Hủy đề nghị");

    expect(await screen.findByText("Nguyễn Thị Lan đã chấp nhận. Lớp và đủ 8 buổi đã được đặt.")).toBeTruthy();
    expect(screen.getByText("Đề nghị đã kết thúc nên không đổi được nữa.")).toBeTruthy();
    expect(screen.getByText("Chấm đặc: buổi đã chốt.")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Hủy đề nghị" })).toBeNull();
    expect(onCancelled).not.toHaveBeenCalled();
    await press("Xem lớp");
    expect(onOpenClass).toHaveBeenCalledWith(CLASS_ID);
  });

  it("a stale ETag (412) reloads before another cancel can be sent (INV-23)", async () => {
    let calls = 0;
    const pending = offer(LAN);
    api = routes([], {
      "GET /offers/{offerId}": () => withEtag(pending, 1),
      "POST /offers/{offerId}/cancel": () => {
        calls += 1;
        return problem(412, "VERSION_CONFLICT", "Dữ liệu đã thay đổi.");
      },
    });
    await renderDetail(pending.id);
    await screen.findByRole("header", { name: "Nguyễn Thị Lan" });
    await press("Hủy đề nghị");
    await pressInSheet("Hủy đề nghị");

    expect(
      await screen.findByText("Dữ liệu đã thay đổi từ lúc bạn mở. Tải lại để xem bản mới; phần bạn đã nhập hợp lệ được giữ lại."),
    ).toBeTruthy();
    expect(screen.getByRole("button", { name: "Hủy đề nghị" })).toBeDisabled();
    expect(calls).toBe(1);
  });

  it("an unknown outcome retries the cancel with the same key (INV-20)", async () => {
    let calls = 0;
    const pending = offer(LAN);
    api = routes([], {
      "GET /offers/{offerId}": () => withEtag(pending, 1),
      "POST /offers/{offerId}/cancel": () =>
        ++calls === 1
          ? problem(503, "UNAVAILABLE", "Dịch vụ tạm gián đoạn.")
          : json(200, { ...pending, state: "CANCELLED", reasonCode: "PARENT_CANCELLED", revision: 2 }),
    });
    const { onCancelled } = await renderDetail(pending.id);
    await screen.findByRole("header", { name: "Nguyễn Thị Lan" });
    await press("Hủy đề nghị");
    await pressInSheet("Hủy đề nghị");

    expect(
      await screen.findByText("Chưa rõ kết quả. Yêu cầu có thể đã được ghi nhận. Thử lại sẽ không tạo bản trùng."),
    ).toBeTruthy();
    expect(screen.getByRole("button", { name: "Hủy đề nghị" })).toBeDisabled();
    await press("Thử lại");

    const posts = api.requests.filter((r) => r.method === "POST");
    expect(posts).toHaveLength(2);
    expect(posts[1].headers.get("Idempotency-Key")).toBe(posts[0].headers.get("Idempotency-Key"));
    await waitFor(() => expect(onCancelled).toHaveBeenCalledTimes(1));
  });

  it("a rejected offer shows its reason and leads to another tutor, never to a cancel", async () => {
    const rejected = offer(LAN, { state: "REJECTED", reasonCode: "DECLINED", revision: 2 });
    api = routes([], { "GET /offers/{offerId}": () => withEtag(rejected, 2) });
    await renderDetail(rejected.id);

    expect(await screen.findByText("Lý do: không nhận lớp này.")).toBeTruthy();
    expect(screen.getByText("Gia sư đã từ chối")).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Hủy đề nghị" })).toBeNull();
    expect(screen.getByRole("button", { name: "Gửi cho gia sư khác" })).toBeTruthy();
  });
});
