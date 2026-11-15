import type { Metadata, Viewport } from "next";
import { Be_Vietnam_Pro } from "next/font/google";
import "./globals.css";

// The three approved weights (02-nen-thiet-ke.md, #95), served from the app by next/font; the
// vietnamese subset carries the stacked marks. globals.css reads the variable as --font-sans.
const beVietnamPro = Be_Vietnam_Pro({
  weight: ["400", "500", "600"],
  subsets: ["latin", "vietnamese"],
  variable: "--font-be-vietnam-pro",
});

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
    <html lang="vi" className={beVietnamPro.variable} suppressHydrationWarning>
      <body>{children}</body>
    </html>
  );
}
