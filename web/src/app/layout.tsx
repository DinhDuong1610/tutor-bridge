import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "TutorBridge",
  description: "Kết nối phụ huynh với gia sư và quản lý việc học kèm.",
};

/**
 * The browser bar matches the page instead of the browser default (02-nen-thiet-ke.md). A meta tag
 * needs a literal color: keep it equal to --background in globals.css.
 */
export const viewport: Viewport = {
  themeColor: "#ffffff",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    // Browser extensions add attributes such as class="mdl-js" to <html> before React hydrates.
    // suppressHydrationWarning only ignores attribute differences on this one element, not its children.
    <html lang="vi" suppressHydrationWarning>
      <body>{children}</body>
    </html>
  );
}
