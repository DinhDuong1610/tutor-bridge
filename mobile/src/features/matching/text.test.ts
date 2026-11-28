import { commonScheduleText, detailScheduleText, shownReasons, verifiedLabels, verifiedText } from "./text";

/** A session on a Vietnam day and time, as the server sends it in UTC. */
const at = (day: string, from: string, to: string) => ({
  start: new Date(`${day}T${from}:00+07:00`).toISOString(),
  end: new Date(`${day}T${to}:00+07:00`).toISOString(),
});

describe("PH06 card text", () => {
  it("groups the common sessions by time, Monday first, as the approved frame writes them", () => {
    // Thứ 2 30/11 and Thứ 4 02/12/2026, twice a week for four weeks.
    const weeks = [0, 7, 14, 21].flatMap((offset) => {
      const monday = new Date(Date.UTC(2026, 10, 30 + offset)).toISOString().slice(0, 10);
      const wednesday = new Date(Date.UTC(2026, 11, 2 + offset)).toISOString().slice(0, 10);
      return [at(wednesday, "18:00", "19:30"), at(monday, "18:00", "19:30")];
    });
    expect(commonScheduleText(weeks)).toBe("Giờ chung: đủ 8 buổi, Thứ 2 và Thứ 4, 18:00–19:30");
  });

  it("names three days, Sunday last, and keeps sessions of different times apart", () => {
    expect(
      commonScheduleText([
        at("2026-12-06", "08:00", "09:00"),
        at("2026-11-30", "18:00", "19:00"),
        at("2026-12-02", "18:00", "19:00"),
        at("2026-12-04", "19:30", "20:30"),
      ]),
    ).toBe("Giờ chung: đủ 4 buổi, Thứ 2 và Thứ 4, 18:00–19:00; Thứ 6, 19:30–20:30; Chủ nhật, 08:00–09:00");
    expect(
      commonScheduleText([at("2026-11-30", "18:00", "19:00"), at("2026-12-02", "18:00", "19:00"), at("2026-12-04", "18:00", "19:00")]),
    ).toBe("Giờ chung: đủ 3 buổi, Thứ 2, Thứ 4 và Thứ 6, 18:00–19:00");
  });

  it("a session late in the evening in Vietnam is on that Vietnam day, not the UTC one", () => {
    // 23:00 Thứ 2 in Vietnam is 16:00 Monday UTC; 06:00 Thứ 3 in Vietnam is still Monday in UTC.
    expect(commonScheduleText([at("2026-12-01", "06:00", "07:00")])).toBe("Giờ chung: đủ 1 buổi, Thứ 3, 06:00–07:00");
  });

  it("names the scopes the app knows and shows nothing for one it does not", () => {
    expect(verifiedText(["MATH8_ALGEBRA"])).toBe("Đã xác minh: Toán 8, đại số");
    expect(verifiedText(["SOMETHING_NEW"])).toBeNull();
    expect(verifiedText([])).toBeNull();
  });

  it("PH07 writes the common sessions with the first and last day of the cycle", () => {
    const sessions = [at("2026-11-18", "18:00", "19:30"), at("2026-12-14", "18:00", "19:30"), at("2026-11-23", "18:00", "19:30")];
    expect(detailScheduleText(sessions)).toBe(
      "Đủ 3 buổi: Thứ 2 và Thứ 4, 18:00–19:30, từ 18/11 đến 14/12/2026. Giờ này chưa giữ chỗ.",
    );
    // Across a new year the first day keeps its year.
    expect(detailScheduleText([at("2026-12-28", "18:00", "19:30"), at("2027-01-04", "18:00", "19:30")])).toBe(
      "Đủ 2 buổi: Thứ 2, 18:00–19:30, từ 28/12/2026 đến 04/01/2027. Giờ này chưa giữ chỗ.",
    );
  });

  it("PH07 shows two verified scopes, then how many more, and none it does not know", () => {
    expect(verifiedLabels(["MATH8_ALGEBRA"])).toEqual({ labels: ["Đã xác minh: Toán 8, đại số"], more: null });
    expect(verifiedLabels(["MATH8_ALGEBRA", "MATH8_ALGEBRA", "MATH8_ALGEBRA", "UNKNOWN"])).toEqual({
      labels: ["Đã xác minh: Toán 8, đại số", "Đã xác minh: Toán 8, đại số"],
      more: "và 1 phạm vi khác",
    });
    expect(verifiedLabels(["UNKNOWN"])).toEqual({ labels: [], more: null });
  });

  it("lists three reasons, then how many more", () => {
    expect(shownReasons(["a", "b"])).toEqual({ lines: ["a", "b"], more: null });
    expect(shownReasons(["a", "b", "c", "d", "e"])).toEqual({ lines: ["a", "b", "c"], more: "và 2 lý do khác" });
  });
});
