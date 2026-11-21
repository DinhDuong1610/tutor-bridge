import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { act, fireEvent, render, screen } from "@testing-library/react-native";
import type { ReactNode } from "react";
import { StyleSheet, Text } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { ApiError, UnknownOutcomeError } from "@/shared/api/errors";
import { Button, buttonStyle } from "./Button";
import { CommandStatus } from "./CommandStatus";
import { QueryScreen } from "./QueryScreen";
import { NavigationBar, type NavigationTab } from "./NavigationBar";
import { Notice } from "./Notice";
import { StickyActionBar } from "./StickyActionBar";
import { TextField } from "./TextField";
import { TopAppBar } from "./TopAppBar";
import { colors, radius, typography } from "./theme";

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
  it("turns primaryPressed, not see-through, while a primary button is pressed", async () => {
    const look = (pressed: boolean, inactive = false) => StyleSheet.flatten(buttonStyle("primary", pressed, inactive));

    expect(look(false)).toMatchObject({ backgroundColor: colors.primary });
    expect(look(true)).toMatchObject({ backgroundColor: colors.primaryPressed });
    expect(look(true)).not.toHaveProperty("opacity");
    expect(StyleSheet.flatten(buttonStyle("secondary", false, false))).toMatchObject({ borderColor: colors.primary });
  });

  it("gives a pressed secondary button the infoSurface background, not see-through", async () => {
    const look = (pressed: boolean) => StyleSheet.flatten(buttonStyle("secondary", pressed, false));

    expect(look(false)).toMatchObject({ backgroundColor: colors.background, borderColor: colors.primary });
    expect(look(true)).toMatchObject({ backgroundColor: colors.infoSurface, borderColor: colors.primary });
    expect(look(true)).not.toHaveProperty("opacity");
  });

  it("lets a long label wrap inside the button instead of overflowing", async () => {
    await render(<Button title="Đăng xuất khỏi thiết bị này" onPress={() => undefined} />);
    const label = screen.getByText("Đăng xuất khỏi thiết bị này");

    expect(label).toHaveStyle({ flexShrink: 1, ...typography.label });
    expect(label.props.numberOfLines).toBeUndefined();
  });

  it("ignores presses while disabled", async () => {
    const onPress = jest.fn();
    await render(<Button title="Lưu" onPress={onPress} disabled />);

    await fireEvent.press(screen.getByRole("button", { name: "Lưu" }));

    expect(onPress).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Lưu" })).toBeDisabled();
  });

  it("ignores presses while busy", async () => {
    const onPress = jest.fn();
    await render(<Button title="Lưu" onPress={onPress} busy />);

    await fireEvent.press(screen.getByRole("button", { name: "Lưu" }));

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
    await fireEvent.press(screen.getByRole("button", { name: "Thử lại" }));
    expect(unknown.retry).toHaveBeenCalledTimes(1);
  });
});

describe("TopAppBar", () => {
  it("names the screen as a header and goes back from a labelled button", async () => {
    const onBack = jest.fn();
    await render(<TopAppBar title="Hồ sơ các con" onBack={onBack} />);

    expect(screen.getByRole("header", { name: "Hồ sơ các con" })).toBeOnTheScreen();
    await fireEvent.press(screen.getByRole("button", { name: "Quay lại" }));
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it("has no back button when the screen cannot go back", async () => {
    await render(<TopAppBar title="Trang chủ" />);
    expect(screen.queryByRole("button", { name: "Quay lại" })).toBeNull();
  });
});

describe("StickyActionBar", () => {
  it("shows its actions", async () => {
    const onPress = jest.fn();
    await render(wrap(<StickyActionBar><Button title="Lưu hồ sơ" onPress={onPress} /></StickyActionBar>));

    await fireEvent.press(screen.getByRole("button", { name: "Lưu hồ sơ" }));
    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it("is set apart by the divider line, not a shadow", async () => {
    await render(wrap(<StickyActionBar><Text>Hành động</Text></StickyActionBar>));

    let bar = screen.getByText("Hành động").parent;
    while (bar && !StyleSheet.flatten(bar.props.style)?.borderTopWidth) bar = bar.parent;
    expect(StyleSheet.flatten(bar?.props.style)).toMatchObject({ borderTopColor: colors.divider });
  });
});

describe("Notice", () => {
  const box = (message: string) => {
    let node = screen.getByText(message).parent;
    while (node && !StyleSheet.flatten(node.props.style)?.padding) node = node.parent;
    return StyleSheet.flatten(node?.props.style);
  };

  it.each([
    ["info", "Thông tin", "icon-info"],
    ["success", "Đã xong", "icon-check_circle"],
    ["warning", "Lưu ý", "icon-warning"],
    ["danger", "Lỗi", "icon-error"],
  ] as const)("says the %s tone with an icon named %s, not a label line above the text", async (tone, word, icon) => {
    await render(<Notice tone={tone} message="Không tải được dữ liệu." />);

    expect(screen.getByRole("image", { name: word })).toBeOnTheScreen();
    expect(screen.getByTestId(icon)).toBeOnTheScreen();
    expect(screen.queryByText(word)).toBeNull();
    expect(screen.getByText("Không tải được dữ liệu.")).toBeOnTheScreen();
  });

  it("has no colored side edge and the 12 radius of a message box (02)", async () => {
    await render(<Notice tone="warning" message="Không cập nhật được." />);

    const style = box("Không cập nhật được.");
    expect(style).toMatchObject({ backgroundColor: colors.warningSurface, borderRadius: radius.md });
    expect(style).not.toHaveProperty("borderLeftWidth");
  });

  it("announces errors and warnings as alerts and keeps the action at its own width", async () => {
    const onPress = jest.fn();
    await render(<Notice tone="danger" message="Không tải được dữ liệu." action={<Button variant="secondary" title="Thử lại" onPress={onPress} />} />);

    // The box is not focusable itself; its role and live region make the reader announce it.
    expect(screen.toJSON()).toMatchObject({ props: { accessibilityRole: "alert", accessibilityLiveRegion: "polite" } });
    const action = screen.getByRole("button", { name: "Thử lại" }).parent;
    let wrapper = action;
    while (wrapper && StyleSheet.flatten(wrapper.props.style)?.alignSelf !== "flex-start") wrapper = wrapper.parent;
    expect(wrapper).not.toBeNull();
    await fireEvent.press(screen.getByRole("button", { name: "Thử lại" }));
    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it("does not announce an info notice as an alert", async () => {
    await render(<Notice tone="info" message="Tạo hồ sơ cho con trước." />);
    expect(screen.toJSON()).not.toMatchObject({ props: { accessibilityRole: "alert" } });
  });
});

describe("NavigationBar", () => {
  const TABS: NavigationTab[] = [
    { key: "home", label: "Tổng quan", icon: "home_fill" },
    { key: "search", label: "Tìm gia sư", icon: "search" },
    { key: "classes", label: "Lớp học", icon: "event" },
    { key: "notifications", label: "Thông báo", icon: "notifications_fill", unread: 2 },
    { key: "account", label: "Tài khoản", icon: "person_fill" },
  ];

  it("marks the selected tab and reports the tab that is pressed", async () => {
    const onSelect = jest.fn();
    await render(wrap(<NavigationBar tabs={TABS} selectedKey="home" onSelect={onSelect} />));

    expect(screen.getByRole("tab", { name: "Tổng quan" })).toBeSelected();
    expect(screen.getByRole("tab", { name: "Tìm gia sư" })).not.toBeSelected();
    await fireEvent.press(screen.getByRole("tab", { name: "Lớp học" }));
    expect(onSelect).toHaveBeenCalledWith("classes");
  });

  it("shows the unread count in the badge and in the tab's name; over 99 is 99+ (PH01)", async () => {
    const many = TABS.map((tab) => (tab.key === "notifications" ? { ...tab, unread: 120 } : tab));
    await render(wrap(<NavigationBar tabs={many} selectedKey="home" onSelect={() => undefined} />));

    expect(screen.getByRole("tab", { name: "Thông báo, 99+ chưa đọc" })).toBeOnTheScreen();
    expect(screen.getByText("99+")).toBeOnTheScreen();
  });

  it("shows no badge when nothing is unread", async () => {
    const none = TABS.map((tab) => ({ ...tab, unread: 0 }));
    await render(wrap(<NavigationBar tabs={none} selectedKey="home" onSelect={() => undefined} />));

    expect(screen.getByRole("tab", { name: "Thông báo" })).toBeOnTheScreen();
    expect(screen.queryByText("0")).toBeNull();
  });

  it("limits only the tab labels to 1.3x text (OP-22)", async () => {
    await render(wrap(<NavigationBar tabs={TABS} selectedKey="home" onSelect={() => undefined} />));

    for (const tab of TABS) expect(screen.getByText(tab.label)).toHaveProp("maxFontSizeMultiplier", 1.3);
    expect(screen.getByText("2")).not.toHaveProp("maxFontSizeMultiplier");
  });

  it("puts the infoSurface pill only behind the selected tab", async () => {
    await render(wrap(<NavigationBar tabs={TABS} selectedKey="search" onSelect={() => undefined} />));

    // Tab icons are hidden from screen readers (the tab is named by its label), hence includeHiddenElements.
    const pill = (icon: string) =>
      StyleSheet.flatten(screen.getByTestId(icon, { includeHiddenElements: true }).parent?.props.style);
    expect(pill("icon-search")).toMatchObject({ backgroundColor: colors.infoSurface });
    expect(pill("icon-home_fill")?.backgroundColor).toBeUndefined();
  });
});
