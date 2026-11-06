import type { ReactNode } from "react";
import { cn } from "@/shared/lib/utils";

const TONES = {
  info: { label: "Thông tin", className: "border-primary bg-accent" },
  success: { label: "Đã xong", className: "border-success bg-success-surface" },
  warning: { label: "Lưu ý", className: "border-warning bg-warning-surface" },
  danger: { label: "Lỗi", className: "border-destructive bg-destructive-surface" },
} as const;

/** A message block. The tone is written as a word, not only shown as a color (same as mobile). */
export function Notice({ tone, message, action }: { tone: keyof typeof TONES; message: string; action?: ReactNode }) {
  const t = TONES[tone];
  return (
    <div
      role={tone === "danger" || tone === "warning" ? "alert" : "status"}
      className={cn("flex flex-col gap-2 rounded-md border-l-4 p-4", t.className)}
    >
      <p className="text-sm font-semibold">{t.label}</p>
      <p>{message}</p>
      {action}
    </div>
  );
}
