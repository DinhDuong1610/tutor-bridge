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

function ErrorForm() {
  const form = useForm<{ name: string }>({ defaultValues: { name: "" } });
  return (
    <>
      <FormTextField control={form.control} name="name" label="Tên hiển thị" hint="Không cần họ tên đầy đủ." />
      <Button title="Báo lỗi" onPress={() => form.setError("name", { type: "server", message: "Nhập tên hiển thị." })} />
    </>
  );
}

// Compile-time check: accessibilityHint is set by TextField from the error or hint.
function NoHintOverride() {
  const form = useForm<{ name: string }>({ defaultValues: { name: "" } });
  // @ts-expect-error a screen cannot replace the hint that carries the field's error
  return <FormTextField control={form.control} name="name" label="Tên" accessibilityHint="Gợi ý khác" />;
}
void NoHintOverride;

/** TypeScript lets hyphenated attributes through, so this is checked at runtime. */
function AriaLabelForm() {
  const form = useForm<{ name: string }>({ defaultValues: { name: "" } });
  return <FormTextField control={form.control} name="name" label="Tên hiển thị" aria-label="Tên khác" aria-labelledby="khac" />;
}

describe("FormTextField", () => {
  it("reads the field's error to a screen reader, and the hint while there is no error", async () => {
    await render(<ErrorForm />);
    expect(screen.getByLabelText("Tên hiển thị").props.accessibilityHint).toBe("Không cần họ tên đầy đủ.");

    await act(async () => fireEvent.press(screen.getByRole("button", { name: "Báo lỗi" })));

    expect(screen.getByLabelText("Tên hiển thị").props.accessibilityHint).toBe("Nhập tên hiển thị.");
    expect(screen.getByText("Lỗi: Nhập tên hiển thị.")).toBeOnTheScreen();
  });

  it("keeps the label as the accessible name even when a screen passes aria-label", async () => {
    await render(<AriaLabelForm />);

    const input = screen.getByLabelText("Tên hiển thị");
    expect(input.props["aria-label"]).toBeUndefined();
    expect(input.props["aria-labelledby"]).toBeUndefined();
    expect(screen.queryByLabelText("Tên khác")).toBeNull();
  });

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
