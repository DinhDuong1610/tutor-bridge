import Link from "next/link";
import { Suspense } from "react";
import { verifyAdminSession } from "@/shared/session/verify";

// Admin console shell. Every page under it needs a live admin session, checked against the API.
export default function AdminConsoleLayout({ children }: LayoutProps<"/admin">) {
  return (
    <div className="min-h-dvh">
      <header className="border-b bg-muted">
        <nav aria-label="Quản trị" className="mx-auto flex max-w-6xl items-center gap-6 px-4 py-3">
          <span className="font-semibold">Quản trị TutorBridge</span>
          <Link href="/admin/verifications" className="text-primary underline-offset-4 hover:underline">
            Xác minh gia sư
          </Link>
        </nav>
      </header>
      <main className="mx-auto max-w-6xl">
        <Suspense fallback={<p className="px-4 py-12 text-muted-foreground">Đang kiểm tra phiên đăng nhập…</p>}>
          <RequireAdminSession>{children}</RequireAdminSession>
        </Suspense>
      </main>
    </div>
  );
}

async function RequireAdminSession({ children }: { children: React.ReactNode }) {
  await verifyAdminSession();
  return children;
}
