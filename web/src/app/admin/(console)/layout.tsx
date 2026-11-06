import Link from "next/link";

// Admin console shell. W2 adds the session guard (proxy.ts and a server check here).
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
      <main className="mx-auto max-w-6xl">{children}</main>
    </div>
  );
}
