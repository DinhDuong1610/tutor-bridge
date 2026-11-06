// @vitest-environment jsdom
/**
 * The pattern for an admin form and its test: react-hook-form with zod, a Server Action through
 * useServerCommand, server field errors, CommandStatus. The form lives in this test on purpose;
 * real forms belong to GD-13. In a real page the action is a "use server" function calling
 * runCommand((await adminApi()).POST(..., { params: { header: { "Idempotency-Key": key } } })).
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useForm } from "react-hook-form";
import { afterEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";
import type { ActionResult } from "@/shared/api/result";
import { useServerCommand } from "@/shared/form/use-server-command";
import { useServerFieldErrors } from "@/shared/form/use-server-field-errors";
import { Button } from "@/shared/ui/button";
import { CommandStatus } from "@/shared/ui/command-status";
import { FormField } from "@/shared/ui/form-field";
import { Notice } from "@/shared/ui/notice";

// --- Example form ---------------------------------------------------------------------------------

const decisionForm = z.object({
  reason: z.string().trim().min(1, "Nhập lý do.").max(500, "Lý do tối đa 500 ký tự."),
});
type DecisionForm = z.infer<typeof decisionForm>;

type Action = (input: DecisionForm, idempotencyKey: string) => Promise<ActionResult<{ id: string }>>;

function ExampleDecisionForm({ action }: { action: Action }) {
  const form = useForm<DecisionForm>({ resolver: zodResolver(decisionForm), defaultValues: { reason: "" } });
  const command = useServerCommand(action);
  const otherErrors = useServerFieldErrors(command.result, form.setError, ["reason"]);

  return (
    <form noValidate onSubmit={form.handleSubmit((values) => command.submit(values))} className="flex flex-col gap-4">
      <FormField id="reason" label="Lý do" error={form.formState.errors.reason?.message} {...form.register("reason")} />
      {otherErrors.map((message) => (
        <Notice key={message} tone="danger" message={message} />
      ))}
      <CommandStatus command={command} confirmedMessage="Đã ghi nhận quyết định." />
      <Button type="submit" disabled={command.state === "sending"}>
        Gửi quyết định
      </Button>
    </form>
  );
}

// --- Test -----------------------------------------------------------------------------------------

afterEach(cleanup);

const confirmed: ActionResult<{ id: string }> = { state: "confirmed", data: { id: "v1" } };

describe("example admin form", () => {
  it("checks the form before calling the server", async () => {
    const action = vi.fn<Action>();
    render(<ExampleDecisionForm action={action} />);

    await userEvent.click(screen.getByRole("button", { name: "Gửi quyết định" }));

    expect(await screen.findByText("Lỗi: Nhập lý do.")).toBeTruthy();
    expect(action).not.toHaveBeenCalled();
  });

  it("puts a server field error at the field, keeps the input, then succeeds with a new key", async () => {
    const action = vi
      .fn<Action>()
      .mockResolvedValueOnce({ state: "failed", title: "Dữ liệu chưa hợp lệ.", fields: { reason: "Lý do quá chung chung." } })
      .mockResolvedValueOnce(confirmed);
    render(<ExampleDecisionForm action={action} />);

    await userEvent.type(screen.getByLabelText("Lý do"), "Không rõ");
    await userEvent.click(screen.getByRole("button", { name: "Gửi quyết định" }));

    expect(await screen.findByText("Lỗi: Lý do quá chung chung.")).toBeTruthy();
    expect(screen.getByText("Vui lòng sửa các ô được đánh dấu rồi gửi lại.")).toBeTruthy();
    expect((screen.getByLabelText("Lý do") as HTMLInputElement).value).toBe("Không rõ");

    await userEvent.clear(screen.getByLabelText("Lý do"));
    await userEvent.type(screen.getByLabelText("Lý do"), "Minh chứng bằng cấp không khớp tên");
    await userEvent.click(screen.getByRole("button", { name: "Gửi quyết định" }));

    expect(await screen.findByText("Đã ghi nhận quyết định.")).toBeTruthy();
    const [firstKey, secondKey] = action.mock.calls.map((call) => call[1]);
    expect(firstKey).toMatch(/^[0-9a-f]{32}$/);
    expect(secondKey).not.toBe(firstKey);
    expect(action.mock.calls[1][0]).toEqual({ reason: "Minh chứng bằng cấp không khớp tên" });
  });

  it("retries an unknown outcome with the same key", async () => {
    const action = vi.fn<Action>().mockResolvedValueOnce({ state: "unknown" }).mockResolvedValueOnce(confirmed);
    render(<ExampleDecisionForm action={action} />);

    await userEvent.type(screen.getByLabelText("Lý do"), "Hồ sơ đầy đủ");
    await userEvent.click(screen.getByRole("button", { name: "Gửi quyết định" }));
    expect(await screen.findByText(/Chưa rõ kết quả/)).toBeTruthy();

    await userEvent.click(screen.getByRole("button", { name: "Thử lại" }));

    expect(await screen.findByText("Đã ghi nhận quyết định.")).toBeTruthy();
    await waitFor(() => expect(action).toHaveBeenCalledTimes(2));
    expect(action.mock.calls[1][1]).toBe(action.mock.calls[0][1]);
  });

  it("treats a crashed action call as an unknown outcome", async () => {
    const action = vi.fn<Action>().mockRejectedValueOnce(new TypeError("Failed to fetch"));
    render(<ExampleDecisionForm action={action} />);

    await userEvent.type(screen.getByLabelText("Lý do"), "Hồ sơ đầy đủ");
    await userEvent.click(screen.getByRole("button", { name: "Gửi quyết định" }));

    expect(await screen.findByText(/Chưa rõ kết quả/)).toBeTruthy();
  });
});
