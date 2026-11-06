import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { act, fireEvent, render, screen } from "@testing-library/react-native";
import type { ReactNode } from "react";
import { Text } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { ApiError, UnknownOutcomeError } from "@/shared/api/errors";
import { Button } from "./Button";
import { CommandStatus } from "./CommandStatus";
import { QueryScreen } from "./QueryScreen";
import { TextField } from "./TextField";

const problem = (status: number, title = "Không được phép.") =>
  new ApiError({
    type: "urn:tutorbridge:problem:TEST",
    title,
    status,
    code: "TEST",
    detail: title,
    traceId: "0123456789abcdef0123456789abcdef",
  });

function wrap(children: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } });
  return (
    <SafeAreaProvider initialMetrics={{ frame: { x: 0, y: 0, width: 400, height: 800 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } }}>
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    </SafeAreaProvider>
  );
}

function Probe({ load }: { load: () => Promise<string[]> }) {
  const query = useQuery({ queryKey: ["probe"], queryFn: load });
  return (
    <QueryScreen title="Hồ sơ các con" query={query} empty={{ when: (items) => items.length === 0, message: "Chưa có hồ sơ con nào." }}>
      {(items) => items.map((item) => <Text key={item}>{item}</Text>)}
    </QueryScreen>
  );
}

describe("QueryScreen", () => {
  it("shows loading, then the data", async () => {
    let resolve: (items: string[]) => void = () => undefined;
    await render(wrap(<Probe load={() => new Promise((r) => (resolve = r))} />));

    expect(screen.getByRole("header", { name: "Hồ sơ các con" })).toBeOnTheScreen();
    expect(screen.getByText("Đang tải…")).toBeOnTheScreen();

    await act(async () => resolve(["Minh"]));
    expect(await screen.findByText("Minh")).toBeOnTheScreen();
    expect(screen.queryByText("Đang tải…")).toBeNull();
  });

  it("shows the empty message when there is nothing", async () => {
    await render(wrap(<Probe load={async () => []} />));

    expect(await screen.findByText("Chưa có hồ sơ con nào.")).toBeOnTheScreen();
  });

  it("offers a retry after a connection or server error", async () => {
    const load = jest.fn().mockRejectedValueOnce(new UnknownOutcomeError()).mockResolvedValueOnce(["Minh"]);
    await render(wrap(<Probe load={load} />));

    expect(await screen.findByText("Không tải được dữ liệu. Kiểm tra kết nối rồi thử lại.")).toBeOnTheScreen();
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Thử lại" })));

    expect(await screen.findByText("Minh")).toBeOnTheScreen();
    expect(load).toHaveBeenCalledTimes(2);
  });

  it.each([
    [403, "Bạn không còn quyền xem nội dung này."],
    [404, "Không tìm thấy nội dung này. Có thể nó đã bị xóa hoặc bạn không có quyền xem."],
  ])("explains a %i without offering a pointless retry", async (status, message) => {
    await render(wrap(<Probe load={async () => Promise.reject(problem(status))} />));

    expect(await screen.findByText(message)).toBeOnTheScreen();
    expect(screen.queryByRole("button", { name: "Thử lại" })).toBeNull();
  });

  it("shows nothing for a 401, which the session handles", async () => {
    await render(wrap(<Probe load={async () => Promise.reject(problem(401))} />));

    await screen.findByRole("header", { name: "Hồ sơ các con" });
    await act(async () => undefined);
    expect(screen.queryByText(/Không tải được/)).toBeNull();
    expect(screen.queryByRole("alert")).toBeNull();
  });
});

describe("TextField", () => {
  it("writes the error as text next to the field", async () => {
    await render(<TextField label="Tên hiển thị" value="" onChangeText={() => undefined} error="Nhập tên hiển thị." />);

    expect(screen.getByLabelText("Tên hiển thị")).toBeOnTheScreen();
    expect(screen.getByRole("alert")).toHaveTextContent("Lỗi: Nhập tên hiển thị.");
  });
});

describe("Button", () => {
  it("ignores presses while busy", async () => {
    const onPress = jest.fn();
    await render(<Button title="Lưu" onPress={onPress} busy />);

    fireEvent.press(screen.getByRole("button", { name: "Lưu" }));

    expect(onPress).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Lưu" })).toBeDisabled();
  });
});

describe("CommandStatus", () => {
  const command = (state: "idle" | "sending" | "confirmed" | "failed" | "unknown", error: ApiError | null = null) => ({
    state,
    error,
    retry: jest.fn(),
  });

  it("shows nothing before the first send", async () => {
    await render(<CommandStatus command={command("idle")} confirmedMessage="Đã lưu." />);
    expect(screen.toJSON()).toBeNull();
  });

  it.each([
    ["sending", "Đang gửi…"],
    ["confirmed", "Đã lưu."],
  ] as const)("says %s in words", async (state, text) => {
    await render(<CommandStatus command={command(state)} confirmedMessage="Đã lưu." />);
    expect(screen.getByText(text)).toBeOnTheScreen();
  });

  it("shows the server's message for a failure, or points at the fields", async () => {
    await render(<CommandStatus command={command("failed", problem(409, "Lịch vừa bị trùng."))} confirmedMessage="Đã lưu." />);
    expect(screen.getByText("Lịch vừa bị trùng.")).toBeOnTheScreen();

    const withFields = new ApiError({ ...problem(422).problem, fields: [{ path: "displayName", message: "Nhập tên." }] });
    await render(<CommandStatus command={command("failed", withFields)} confirmedMessage="Đã lưu." />);
    expect(screen.getByText("Vui lòng sửa các ô được đánh dấu rồi gửi lại.")).toBeOnTheScreen();
  });

  it("offers a safe retry for an unknown outcome", async () => {
    const unknown = command("unknown");
    await render(<CommandStatus command={unknown} confirmedMessage="Đã lưu." />);

    expect(screen.getByText(/Chưa rõ kết quả/)).toBeOnTheScreen();
    fireEvent.press(screen.getByRole("button", { name: "Thử lại" }));
    expect(unknown.retry).toHaveBeenCalledTimes(1);
  });
});
