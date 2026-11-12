import { act, fireEvent, render, screen } from "@testing-library/react-native";
import { useForm } from "react-hook-form";
import { Button } from "@/shared/ui/Button";
import { FormTextField } from "./FormTextField";

type Values = { email: string; password: string };

function SignInLikeForm({ onSubmit }: { onSubmit: (values: Values) => void }) {
  const form = useForm<Values>({ defaultValues: { email: "", password: "" } });
  return (
    <>
      <FormTextField
        control={form.control}
        name="email"
        label="Email"
        keyboardType="email-address"
        autoCapitalize="none"
        autoComplete="email"
      />
      <FormTextField
        control={form.control}
        name="password"
        label="Mật khẩu"
        hint="Từ 15 ký tự."
        secureTextEntry
        autoComplete="current-password"
        textContentType="password"
      />
      <Button title="Gửi" onPress={form.handleSubmit(onSubmit)} />
    </>
  );
}

describe("FormTextField", () => {
  it("passes TextInput options through, so a password field is hidden", async () => {
    await render(<SignInLikeForm onSubmit={jest.fn()} />);

    const password = screen.getByLabelText("Mật khẩu");
    expect(password.props.secureTextEntry).toBe(true);
    expect(password.props.autoComplete).toBe("current-password");
    expect(password.props.textContentType).toBe("password");
    expect(screen.getByText("Từ 15 ký tự.")).toBeOnTheScreen();

    const email = screen.getByLabelText("Email");
    expect(email.props.keyboardType).toBe("email-address");
    expect(email.props.autoCapitalize).toBe("none");
    expect(email.props.autoComplete).toBe("email");
    expect(email.props.secureTextEntry).toBeUndefined();
  });

  it("keeps the value and its events with react-hook-form", async () => {
    const onSubmit = jest.fn();
    await render(<SignInLikeForm onSubmit={onSubmit} />);

    await fireEvent.changeText(screen.getByLabelText("Email"), "phu.huynh@example.test");
    await fireEvent.changeText(screen.getByLabelText("Mật khẩu"), "mot cau mat khau du dai");
    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Gửi" })));

    expect(screen.getByLabelText("Mật khẩu")).toHaveDisplayValue("mot cau mat khau du dai");
    expect(onSubmit).toHaveBeenCalledWith({ email: "phu.huynh@example.test", password: "mot cau mat khau du dai" }, expect.anything());
  });
});
