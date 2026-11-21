import { screen } from "@testing-library/react-native";
import type { ReactElement } from "react";
import { mockApi } from "@/shared/test/mockApi";
import { renderScreen } from "@/shared/test/renderScreen";
import { ChangePasswordScreen } from "./ChangePasswordScreen";
import { RecoveryScreen } from "./RecoveryScreen";
import { RegisterScreen } from "./RegisterScreen";
import { ResetPasswordScreen } from "./ResetPasswordScreen";
import { SignInScreen } from "./SignInScreen";
import { VerifyEmailScreen } from "./VerifyEmailScreen";

type Instance = ReturnType<typeof screen.getByRole>;

const TOKEN = "t".repeat(43);

// Without a safe area the title ran under the iPhone status bar (#179, screenshots of 21/11/2026).
const SCREENS: [string, () => ReactElement][] = [
  ["AUTH01", () => <RegisterScreen termsVersion="v1" onGoToSignIn={jest.fn()} onResendVerification={jest.fn()} />],
  ["AUTH02", () => <VerifyEmailScreen token={TOKEN} onGoToSignIn={jest.fn()} />],
  ["AUTH03", () => <SignInScreen onGoToRegister={jest.fn()} onForgotPassword={jest.fn()} onResendVerification={jest.fn()} />],
  ["AUTH04", () => <RecoveryScreen onGoToSignIn={jest.fn()} />],
  ["AUTH05", () => <ResetPasswordScreen token={TOKEN} onGoToSignIn={jest.fn()} onRequestNewLink={jest.fn()} />],
  ["AUTH06 đổi mật khẩu", () => <ChangePasswordScreen onCancel={jest.fn()} />],
];

// The host view that SafeAreaView of react-native-safe-area-context renders; it pads by the insets.
function insideSafeArea(node: Instance | null): boolean {
  for (let current = node; current; current = current.parent) {
    if (current.type === "RNCSafeAreaView") return true;
  }
  return false;
}

describe("account screens keep their title below the status bar", () => {
  let server: ReturnType<typeof mockApi>;
  afterEach(() => server.restore());

  it.each(SCREENS)("%s puts its title inside a safe area", async (_name, screenElement) => {
    server = mockApi({});
    await renderScreen(screenElement());

    expect(insideSafeArea(screen.getAllByRole("header")[0])).toBe(true);
  });
});
