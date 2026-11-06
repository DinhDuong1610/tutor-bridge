import type { ReactNode } from "react";
import { Notice } from "./notice";

/** Frame for an admin page. Put the screen code (AD03...) in a comment at the top of the page file. */
export function AdminPage({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-4 px-4 py-8">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {children}
    </section>
  );
}

type FailedLoad = { status: "forbidden" | "not-found" | "rate-limited" | "error" };

/** The required states for a failed read (FR-37), same wording as mobile. */
export function StateMessage({ result }: { result: FailedLoad }) {
  switch (result.status) {
    case "forbidden":
      return <Notice tone="danger" message="Bạn không còn quyền xem nội dung này." />;
    case "not-found":
      return <Notice tone="danger" message="Không tìm thấy nội dung này. Có thể nó đã bị xóa hoặc bạn không có quyền xem." />;
    case "rate-limited":
      return <Notice tone="warning" message="Bạn thao tác quá nhanh. Vui lòng đợi một lúc rồi tải lại trang." />;
    case "error":
      return <Notice tone="danger" message="Không tải được dữ liệu. Kiểm tra kết nối rồi tải lại trang." />;
  }
}

export function EmptyState({ message, action }: { message: string; action?: ReactNode }) {
  return <Notice tone="info" message={message} action={action} />;
}
