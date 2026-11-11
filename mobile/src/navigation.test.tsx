import { useQuery } from "@tanstack/react-query";
import { Text } from "react-native";
import { act, fireEvent, renderRouter, screen, waitFor } from "expo-router/testing-library";
import AuthLayout from "@/app/(auth)/_layout";
import SignInRoute from "@/app/(auth)/sign-in";
import RootLayout from "@/app/_layout";
import Index from "@/app/index";
import ParentLayout from "@/app/parent/_layout";
import { ApiError } from "@/shared/api/errors";
import { clearStore, fakeSession, storeSession, storedSession } from "@/shared/test/session";

beforeEach(() => {
  clearStore();
});

// Uses the real route tree in src/app, so a missing or clashing route fails here.
describe("navigation without a session", () => {
  it.each(["/", "/parent", "/tutor"])("sends %s to sign-in", async (url) => {
    await renderRouter("./src/app", { initialUrl: url });

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/sign-in");
  });

  it("shows a Vietnamese not-found screen for an unknown link", async () => {
    await renderRouter("./src/app", { initialUrl: "/admin" });

    expect(await screen.findByRole("header", { name: "Không tìm thấy màn hình" })).toBeOnTheScreen();
  });

  it("ignores a damaged saved session and removes it", async () => {
    storeSession("{not json");
    await renderRouter("./src/app", { initialUrl: "/" });

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    await waitFor(() => expect(storedSession()).toBeUndefined());
  });

  it("ignores a session past its absolute expiry and removes it", async () => {
    storeSession(fakeSession("PARENT", { absoluteExpiresAt: "2020-01-01T00:00:00Z" }));
    await renderRouter("./src/app", { initialUrl: "/parent" });

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    await waitFor(() => expect(storedSession()).toBeUndefined());
  });
});

describe("navigation with a session", () => {
  it.each([
    ["PARENT", "/parent", "Trang chủ phụ huynh"],
    ["TUTOR", "/tutor", "Trang chủ gia sư"],
  ] as const)("sends a %s to %s", async (role, home, title) => {
    storeSession(fakeSession(role));
    await renderRouter("./src/app", { initialUrl: "/" });

    expect(await screen.findByRole("header", { name: title })).toBeOnTheScreen();
    expect(screen).toHavePathname(home);
  });

  it("keeps a parent out of the tutor area", async () => {
    storeSession(fakeSession("PARENT"));
    await renderRouter("./src/app", { initialUrl: "/tutor" });

    expect(await screen.findByRole("header", { name: "Trang chủ phụ huynh" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/parent");
  });

  it("keeps a signed-in user out of sign-in", async () => {
    storeSession(fakeSession("TUTOR"));
    await renderRouter("./src/app", { initialUrl: "/sign-in" });

    expect(await screen.findByRole("header", { name: "Trang chủ gia sư" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/tutor");
  });

  it("tells an admin the app is not for them and lets them sign out", async () => {
    storeSession(fakeSession("ADMIN"));
    await renderRouter("./src/app", { initialUrl: "/" });

    expect(await screen.findByRole("header", { name: "Tài khoản này chưa dùng được trên ứng dụng" })).toBeOnTheScreen();
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Đăng xuất" })));

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    expect(storedSession()).toBeUndefined();
  });
});

describe("session end", () => {
  function FailingHome() {
    const query = useQuery({
      queryKey: ["probe"],
      queryFn: async () => {
        throw new ApiError({
          type: "urn:tutorbridge:problem:AUTHENTICATION_REQUIRED",
          title: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
          status: 401,
          code: "AUTHENTICATION_REQUIRED",
          detail: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
          traceId: "0123456789abcdef0123456789abcdef",
        });
      },
    });
    return <Text>{query.status}</Text>;
  }

  it("ends the session on a 401 and forgets the token", async () => {
    storeSession(fakeSession("PARENT"));
    await renderRouter(
      {
        _layout: RootLayout,
        index: Index,
        "(auth)/_layout": AuthLayout,
        "(auth)/sign-in": SignInRoute,
        "parent/_layout": ParentLayout,
        "parent/index": FailingHome,
      },
      { initialUrl: "/parent" },
    );

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    expect(screen).toHavePathname("/sign-in");
    expect(storedSession()).toBeUndefined();
  });
});
