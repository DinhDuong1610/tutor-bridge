import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "TutorBridge",
  description: "Kết nối phụ huynh với gia sư và quản lý việc học kèm.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
