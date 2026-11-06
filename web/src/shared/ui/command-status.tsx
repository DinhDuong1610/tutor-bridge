"use client";

import type { CommandState } from "@/shared/form/use-server-command";
import type { ActionResult } from "@/shared/api/result";
import { Button } from "./button";
import { Notice } from "./notice";

/** The outcome of a write (FR-37), same wording as mobile. Success only after the server confirms. */
export function CommandStatus({
  command,
  confirmedMessage,
}: {
  command: { state: CommandState; result: ActionResult<unknown> | null; retry: () => void };
  confirmedMessage: string;
}) {
  switch (command.state) {
    case "idle":
      return null;
    case "sending":
      return <Notice tone="info" message="Đang gửi…" />;
    case "confirmed":
      return <Notice tone="success" message={confirmedMessage} />;
    case "failed": {
      const failed = command.result?.state === "failed" ? command.result : null;
      const message =
        failed && Object.keys(failed.fields).length > 0
          ? "Vui lòng sửa các ô được đánh dấu rồi gửi lại."
          : (failed?.title ?? "Không thực hiện được. Vui lòng thử lại.");
      return <Notice tone="danger" message={message} />;
    }
    case "unknown":
      return (
        <Notice
          tone="warning"
          message="Chưa rõ kết quả. Yêu cầu có thể đã được ghi nhận. Thử lại sẽ không tạo bản trùng."
          action={
            <Button type="button" variant="outline" className="self-start" onClick={command.retry}>
              Thử lại
            </Button>
          }
        />
      );
  }
}
