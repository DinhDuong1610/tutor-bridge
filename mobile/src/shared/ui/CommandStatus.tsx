import type { CommandState } from "@/shared/api/command";
import type { ApiError } from "@/shared/api/errors";
import { Button } from "./Button";
import { Notice } from "./Notice";

type Props = {
  command: { state: CommandState; error: ApiError | null; retry: () => unknown };
  /** What the user sees after the server confirms, for example "Đã lưu hồ sơ." */
  confirmedMessage: string;
};

/**
 * The outcome of a write (FR-37). Success is only shown after the server confirms. An unknown
 * outcome offers a retry that reuses the same Idempotency-Key, so it cannot create a duplicate.
 */
export function CommandStatus({ command, confirmedMessage }: Props) {
  switch (command.state) {
    case "idle":
      return null;
    case "sending":
      return <Notice tone="info" message="Đang gửi…" />;
    case "confirmed":
      return <Notice tone="success" message={confirmedMessage} />;
    case "failed": {
      const hasFields = command.error !== null && Object.keys(command.error.fields).length > 0;
      const message = hasFields
        ? "Vui lòng sửa các ô được đánh dấu rồi gửi lại."
        : (command.error?.problem.title ?? "Không thực hiện được. Vui lòng thử lại.");
      return <Notice tone="danger" message={message} />;
    }
    case "unknown":
      return (
        <Notice
          tone="warning"
          message="Chưa rõ kết quả. Yêu cầu có thể đã được ghi nhận. Thử lại sẽ không tạo bản trùng."
          action={<Button variant="secondary" title="Thử lại" onPress={() => void command.retry()} />}
        />
      );
  }
}
