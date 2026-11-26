import { act, fireEvent, render, screen } from "@testing-library/react-native";
import { useState } from "react";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { addSlot, emptyWeek, type WeekPattern } from "../weekly";
import { WeekAvailability } from "./WeekAvailability";

const metrics = { frame: { x: 0, y: 0, width: 360, height: 800 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } };

/** The component with its state, as PH04 and GS04 hold it; `changes` records every pattern it sends. */
function Harness({ initial, saved, changes }: { initial: WeekPattern; saved: WeekPattern; changes: WeekPattern[] }) {
  const [value, setValue] = useState(initial);
  return (
    <SafeAreaProvider initialMetrics={metrics}>
      <WeekAvailability
        value={value}
        saved={saved}
        onChange={(next) => {
          changes.push(next);
          setValue(next);
        }}
      />
    </SafeAreaProvider>
  );
}

const monday = addSlot(emptyWeek(), 0, { start: "18:00", end: "21:00" });

describe("WeekAvailability", () => {
  it("lists the 7 days, Thứ 2 to Chủ nhật, with 'Không rảnh' for a day without slots", async () => {
    await render(<Harness initial={monday} saved={monday} changes={[]} />);

    for (const day of ["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"]) {
      expect(screen.getByRole("header", { name: day })).toBeTruthy();
      expect(screen.getByRole("button", { name: `Thêm giờ, ${day}` })).toBeTruthy();
    }
    expect(screen.getAllByText("Không rảnh")).toHaveLength(6);
  });

  it("marks a saved slot as in effect and a new one as a draft, in words (03)", async () => {
    const edited = addSlot(monday, 0, { start: "07:00", end: "09:00" });
    await render(<Harness initial={edited} saved={monday} changes={[]} />);

    expect(screen.getByLabelText("18:00–21:00, đang hiệu lực")).toBeTruthy();
    expect(screen.getByLabelText("07:00–09:00, nháp, chưa xác nhận")).toBeTruthy();
    expect(screen.getByText("nháp", { exact: false })).toBeTruthy();
  });

  it("adds a slot from the sheet to that day", async () => {
    const changes: WeekPattern[] = [];
    await render(<Harness initial={monday} saved={monday} changes={changes} />);

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thêm giờ, Thứ 3" })));
    expect(screen.getByRole("header", { name: "Thêm khung giờ rảnh, Thứ 3" })).toBeTruthy();
    expect(screen.getByText("Giờ Việt Nam, 24 giờ. Khung sát nhau như 18:00–19:00 và 19:00–20:00 là hợp lệ.")).toBeTruthy();
    await act(async () => fireEvent.changeText(screen.getByLabelText("Từ"), "19:00"));
    await act(async () => fireEvent.changeText(screen.getByLabelText("Đến"), "20:30"));
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thêm khung giờ" })));

    expect(changes.at(-1)![1]).toEqual([{ start: "19:00", end: "20:30" }]);
    expect(screen.getByLabelText("19:00–20:30, nháp, chưa xác nhận")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Bỏ khung Thứ 3 19:00–20:30" })).toBeTruthy();
    expect(screen.queryByRole("header", { name: "Thêm khung giờ rảnh, Thứ 3" })).toBeNull();
  });

  it("AC-UC07-01: refuses an end before the start, with the error as text, and adds nothing", async () => {
    const changes: WeekPattern[] = [];
    await render(<Harness initial={monday} saved={monday} changes={changes} />);

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thêm giờ, Thứ 3" })));
    await act(async () => fireEvent.changeText(screen.getByLabelText("Từ"), "20:30"));
    await act(async () => fireEvent.changeText(screen.getByLabelText("Đến"), "19:00"));
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thêm khung giờ" })));

    expect(screen.getByText("Lỗi: Giờ kết thúc phải sau giờ bắt đầu.")).toBeTruthy();
    expect(changes).toHaveLength(0);

    // "Quay lại" closes without adding, and the sheet opens empty next time.
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Quay lại" })));
    expect(changes).toHaveLength(0);
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thêm giờ, Thứ 5" })));
    expect(screen.getByLabelText("Từ").props.value).toBe("");
    expect(screen.queryByText("Lỗi: Giờ kết thúc phải sau giờ bắt đầu.")).toBeNull();
  });

  it("removes a slot from the × at the end of its chip, named by day and time (#70 Q3)", async () => {
    const changes: WeekPattern[] = [];
    await render(<Harness initial={monday} saved={monday} changes={changes} />);

    // Pressing the chip itself does nothing: only the × removes (Đính, 26/11/2026).
    expect(screen.queryByRole("button", { name: "18:00–21:00, đang hiệu lực" })).toBeNull();
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Bỏ khung Thứ 2 18:00–21:00" })));

    expect(changes.at(-1)).toEqual(emptyWeek());
    expect(screen.getAllByText("Không rảnh")).toHaveLength(7);
  });
});
