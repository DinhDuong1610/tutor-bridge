import { screen } from "@testing-library/react-native";
import { renderRoute } from "@/shared/test/renderRoute";

// Uses the real route tree in src/app, so a missing or clashing route fails here.
describe("navigation", () => {
  it("sends the entry point to sign-in", async () => {
    await renderRoute("/");

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
  });

  it.each([
    ["/sign-in", "Đăng nhập"],
    ["/parent", "Trang chủ phụ huynh"],
    ["/tutor", "Trang chủ gia sư"],
  ])("opens %s", async (url, title) => {
    await renderRoute(url);

    expect(await screen.findByRole("header", { name: title })).toBeOnTheScreen();
  });

  it("shows a Vietnamese not-found screen for an unknown link", async () => {
    await renderRoute("/admin");

    expect(await screen.findByRole("header", { name: "Không tìm thấy màn hình" })).toBeOnTheScreen();
    expect(screen.queryByRole("header", { name: "Trang chủ phụ huynh" })).toBeNull();
  });
});
