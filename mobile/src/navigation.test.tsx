import { renderRouter, screen } from "expo-router/testing-library";

// Uses the real route tree in src/app, so a missing or clashing route fails here.
describe("navigation", () => {
  it("sends the entry point to sign-in", async () => {
    renderRouter("./src/app", { initialUrl: "/" });

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/sign-in");
  });

  it.each([
    ["/sign-in", "Đăng nhập"],
    ["/parent", "Trang chủ phụ huynh"],
    ["/tutor", "Trang chủ gia sư"],
  ])("opens %s", async (url, title) => {
    renderRouter("./src/app", { initialUrl: url });

    expect(await screen.findByRole("header", { name: title })).toBeOnTheScreen();
    expect(screen).toHavePathname(url);
  });

  it("shows a Vietnamese not-found screen for an unknown link", async () => {
    renderRouter("./src/app", { initialUrl: "/admin" });

    expect(await screen.findByRole("header", { name: "Không tìm thấy màn hình" })).toBeOnTheScreen();
    expect(screen.queryByRole("header", { name: "Trang chủ phụ huynh" })).toBeNull();
  });
});
