// @vitest-environment jsdom
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { SignInStep } from "./actions";
import { COPY } from "./copy";
import { AdminSignInForm, type SignInReason } from "./sign-in-form";

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

function setup(steps: { signIn?: SignInStep[]; verifyCode?: SignInStep[] } = {}, reason: SignInReason = null) {
  const signIn = vi.fn(async () => steps.signIn?.shift() ?? { state: "mfa" as const });
  const verifyCode = vi.fn(async () => steps.verifyCode?.shift() ?? { state: "failed" as const });
  render(<AdminSignInForm actions={{ signIn, verifyCode }} reason={reason} next="/admin/verifications" />);
  return { signIn, verifyCode };
}

async function fillPassword() {
  await userEvent.type(screen.getByLabelText(COPY.email), "xacminh01@example.com");
  await userEvent.type(screen.getByLabelText(COPY.password), "mot-mat-khau-du-dai-15");
  await userEvent.click(screen.getByRole("button", { name: COPY.continue }));
}

describe("AD01 sign-in form", () => {
  it("checks the form before calling the server and has no sign-up link", async () => {
    const { signIn } = setup();

    await userEvent.click(screen.getByRole("button", { name: COPY.continue }));

    expect(await screen.findByText(`Lỗi: ${COPY.emailRequired}`)).toBeTruthy();
    expect(screen.getByText(`Lỗi: ${COPY.passwordRequired}`)).toBeTruthy();
    expect(signIn).not.toHaveBeenCalled();
    expect(screen.queryByRole("link")).toBeNull();
    expect(screen.getByText(COPY.note)).toBeTruthy();
  });

  it("shows and hides the password with a named toggle", async () => {
    setup();
    const password = screen.getByLabelText(COPY.password) as HTMLInputElement;
    expect(password.type).toBe("password");

    await userEvent.click(screen.getByRole("button", { name: COPY.showPassword }));
    expect(password.type).toBe("text");
    expect(screen.getByRole("button", { name: COPY.hidePassword }).getAttribute("aria-pressed")).toBe("true");
  });

  it.each([
    ["wrong-credentials", COPY.wrongCredentials, "Lỗi"],
    ["locked", COPY.locked, "Lỗi"],
    ["failed", COPY.failed, "Lỗi"],
  ] as const)("shows the %s state with its approved words and keeps the input", async (state, message, tone) => {
    setup({ signIn: [{ state }] });

    await fillPassword();

    const notice = await screen.findByText(message);
    expect(notice.closest("[role=alert]")?.querySelector(`[aria-label="${tone}"]`)).toBeTruthy();
    expect((screen.getByLabelText(COPY.email) as HTMLInputElement).value).toBe("xacminh01@example.com");
  });

  it("shows the delay with its approved words and keeps the input", async () => {
    setup({ signIn: [{ state: "delayed", retryAfterSeconds: 60 }] });

    await fillPassword();

    const notice = await screen.findByText(COPY.delayed);
    expect(notice.closest("[role=alert]")?.querySelector('[aria-label="Lưu ý"]')).toBeTruthy();
    expect((screen.getByLabelText(COPY.email) as HTMLInputElement).value).toBe("xacminh01@example.com");
  });

  it("keeps the button disabled while delayed, even after an edit", async () => {
    setup({ signIn: [{ state: "delayed", retryAfterSeconds: 60 }] });

    await fillPassword();
    await screen.findByText(COPY.delayed);
    await userEvent.type(screen.getByLabelText(COPY.password), "x");

    expect((screen.getByRole("button", { name: COPY.continue }) as HTMLButtonElement).disabled).toBe(true);
  });

  it.each([
    ["password", COPY.continue],
    ["code", COPY.confirm],
  ] as const)("opens the %s step again once the delay the API named is over (Codex on #253)", async (where, button) => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const steps =
      where === "password"
        ? { signIn: [{ state: "delayed" as const, retryAfterSeconds: 60 }] }
        : { signIn: [{ state: "mfa" as const }], verifyCode: [{ state: "delayed" as const, retryAfterSeconds: 60 }] };
    setup(steps);
    await fillPassword();
    if (where === "code") {
      await userEvent.type(await screen.findByLabelText(COPY.code), "482913");
      await userEvent.click(screen.getByRole("button", { name: COPY.confirm }));
    }
    await screen.findByText(COPY.delayed);

    await act(async () => vi.advanceTimersByTime(59_000));
    expect((screen.getByRole("button", { name: button }) as HTMLButtonElement).disabled).toBe(true);

    await act(async () => vi.advanceTimersByTime(2_000));
    expect((screen.getByRole("button", { name: button }) as HTMLButtonElement).disabled).toBe(false);
    expect(screen.queryByText(COPY.delayed)).toBeNull();
  });

  it("shows the note only while nothing else is said, as the default frame does", async () => {
    setup({ signIn: [{ state: "wrong-credentials" }] });
    expect(screen.getByText(COPY.note)).toBeTruthy();

    await fillPassword();

    await screen.findByText(COPY.wrongCredentials);
    expect(screen.queryByText(COPY.note)).toBeNull();
  });

  it("goes to step 2, then shows a wrong code at the field", async () => {
    const { signIn, verifyCode } = setup({ signIn: [{ state: "mfa" }], verifyCode: [{ state: "wrong-code" }] });

    await fillPassword();
    expect(await screen.findByRole("heading", { name: COPY.mfaTitle })).toBeTruthy();
    expect(signIn).toHaveBeenCalledWith({ email: "xacminh01@example.com", password: "mot-mat-khau-du-dai-15" });
    expect(screen.getByText(COPY.mfaNote)).toBeTruthy();

    await userEvent.type(screen.getByLabelText(COPY.code), "482910");
    await userEvent.click(screen.getByRole("button", { name: COPY.confirm }));

    expect(await screen.findByText(`Lỗi: ${COPY.wrongCode}`)).toBeTruthy();
    expect(verifyCode).toHaveBeenCalledWith({ code: "482910" }, "/admin/verifications");
  });

  it("refuses a code that is not 6 to 8 digits without calling the server", async () => {
    const { verifyCode } = setup({ signIn: [{ state: "mfa" }] });

    await fillPassword();
    await userEvent.type(await screen.findByLabelText(COPY.code), "12ab");
    await userEvent.click(screen.getByRole("button", { name: COPY.confirm }));

    expect(await screen.findByText(`Lỗi: ${COPY.codeInvalid}`)).toBeTruthy();
    expect(verifyCode).not.toHaveBeenCalled();
  });

  it("goes back to the password step when the challenge ended", async () => {
    setup({ signIn: [{ state: "mfa" }], verifyCode: [{ state: "challenge-ended" }] });

    await fillPassword();
    await userEvent.type(await screen.findByLabelText(COPY.code), "482913");
    await userEvent.click(screen.getByRole("button", { name: COPY.confirm }));

    expect(await screen.findByRole("heading", { name: COPY.signInTitle })).toBeTruthy();
    expect(screen.getByText(COPY.wrongCode)).toBeTruthy();
  });

  it("treats a crashed action as a failure to retry", async () => {
    const signIn = vi.fn(async (): Promise<SignInStep> => Promise.reject(new TypeError("Failed to fetch")));
    render(<AdminSignInForm actions={{ signIn, verifyCode: vi.fn() }} reason={null} next={null} />);

    await fillPassword();

    expect(await screen.findByText(COPY.failed)).toBeTruthy();
  });

  it.each([
    ["expired", COPY.expired],
    ["forbidden", COPY.forbidden],
  ] as const)("tells why the console sent the admin here (%s)", async (reason, message) => {
    setup({}, reason);
    expect(screen.getByText(message)).toBeTruthy();
    expect(screen.queryByText(COPY.note)).toBeNull();
  });

  it("drops the reason once the admin has tried again", async () => {
    setup({ signIn: [{ state: "wrong-credentials" }] }, "expired");

    await fillPassword();

    await waitFor(() => expect(screen.queryByText(COPY.expired)).toBeNull());
    expect(screen.getByText(COPY.wrongCredentials)).toBeTruthy();
  });
});
