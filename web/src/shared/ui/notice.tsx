import { CircleAlert, CircleCheck, Info, TriangleAlert, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { cn } from "@/shared/lib/utils";

// The tone word is the icon's accessible name, so state is never shown by color alone (`02`).
const TONES = {
  info: { label: "Thông tin", Icon: Info, className: "bg-accent", iconClassName: "text-primary" },
  success: { label: "Đã xong", Icon: CircleCheck, className: "bg-success-surface", iconClassName: "text-success" },
  warning: { label: "Lưu ý", Icon: TriangleAlert, className: "bg-warning-surface", iconClassName: "text-warning" },
  danger: { label: "Lỗi", Icon: CircleAlert, className: "bg-destructive-surface", iconClassName: "text-destructive" },
} as const satisfies Record<string, { label: string; Icon: LucideIcon; className: string; iconClassName: string }>;

/**
 * A message block, as mobile `Notice` and the approved frames: a tone icon beside the text on a light
 * surface, no colored side edge and no separate label line (Đính on #95, 22/11/2026: lucide-react).
 */
export function Notice({ tone, message, action }: { tone: keyof typeof TONES; message: string; action?: ReactNode }) {
  const { label, Icon, className, iconClassName } = TONES[tone];
  return (
    <div
      role={tone === "danger" || tone === "warning" ? "alert" : "status"}
      className={cn("flex items-start gap-2 rounded-md p-4", className)}
    >
      <Icon role="img" aria-label={label} aria-hidden={false} className={cn("mt-0.5 size-5 shrink-0", iconClassName)} />
      <div className="flex flex-col gap-2">
        <p>{message}</p>
        {action}
      </div>
    </div>
  );
}
