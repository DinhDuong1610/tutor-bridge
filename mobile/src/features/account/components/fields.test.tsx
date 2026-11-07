import { act, fireEvent, render, screen } from "@testing-library/react-native";
import { useForm } from "react-hook-form";
import { AccountTextField } from "./fields";

function PasswordForm() {
  const form = useForm<{ password: string; email: string }>({ defaultValues: { password: "", email: "" } });
  return (
    <>
      <AccountTextField control={form.control} name="password" label="Mật khẩu" secureTextEntry />
      <AccountTextField control={form.control} name="email" label="Email" />
    </>
  );
}

describe("AccountTextField", () => {
  it("hides a password until the user asks to show it, then hides it again", async () => {
    await render(<PasswordForm />);
    const input = screen.getByLabelText("Mật khẩu");
    fireEvent.changeText(input, "mot cau mat khau du dai");
    expect(input.props.secureTextEntry).toBe(true);

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Hiện mật khẩu" })));
    expect(screen.getByLabelText("Mật khẩu").props.secureTextEntry).toBe(false);
    expect(screen.getByLabelText("Mật khẩu")).toHaveDisplayValue("mot cau mat khau du dai");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Ẩn mật khẩu" })));
    expect(screen.getByLabelText("Mật khẩu").props.secureTextEntry).toBe(true);
  });

  it("offers no show button for a field that is not a password", async () => {
    await render(<PasswordForm />);

    expect(screen.queryByRole("button", { name: "Hiện email" })).toBeNull();
    expect(screen.getAllByRole("button")).toHaveLength(1);
  });
});
