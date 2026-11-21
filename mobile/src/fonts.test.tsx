import { renderRouter, screen } from "expo-router/testing-library";
import { useFonts } from "expo-font";
import { clearStore } from "@/shared/test/session";
import { fonts } from "@/shared/ui/theme";

// The root layout waits for Be Vietnam Pro (#95). Only the loading result is replaced here; the
// route tree in src/app is the real one, as in navigation.test.tsx.
jest.mock("expo-font", () => ({ ...jest.requireActual("expo-font"), useFonts: jest.fn() }));
const mockedUseFonts = jest.mocked(useFonts);

beforeEach(() => {
  clearStore();
  mockedUseFonts.mockReset();
});

describe("root layout fonts", () => {
  it("loads the three approved Be Vietnam Pro families", async () => {
    mockedUseFonts.mockReturnValue([true, null]);
    await renderRouter("./src/app", { initialUrl: "/" });

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
    expect(Object.keys(mockedUseFonts.mock.calls[0][0] as object)).toEqual([fonts.regular, fonts.medium, fonts.semiBold]);
  });

  it("shows nothing while the fonts are loading, so text does not jump", async () => {
    mockedUseFonts.mockReturnValue([false, null]);
    await renderRouter("./src/app", { initialUrl: "/" });

    expect(screen.queryByRole("header", { name: "Đăng nhập" })).not.toBeOnTheScreen();
    // expo-router wraps the root layout in its own SafeAreaProvider; inside it there is nothing.
    expect(screen.toJSON()).toMatchObject({ type: "RNCSafeAreaProvider", children: [] });
  });

  it("still opens the app in the system font when the fonts fail to load", async () => {
    mockedUseFonts.mockReturnValue([false, new Error("font download failed")]);
    await renderRouter("./src/app", { initialUrl: "/" });

    expect(await screen.findByRole("header", { name: "Đăng nhập" })).toBeOnTheScreen();
  });
});
