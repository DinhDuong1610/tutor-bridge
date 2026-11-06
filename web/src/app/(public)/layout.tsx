import Link from "next/link";

// Public site (WEB01, WEB02). No private learning content here (module-map).
export default function PublicLayout({ children }: LayoutProps<"/">) {
  return (
    <>
      <header className="border-b">
        <nav aria-label="Trang công khai" className="mx-auto flex max-w-4xl items-center gap-6 px-4 py-3">
          <Link href="/" className="font-semibold">
            TutorBridge
          </Link>
          <Link href="/huong-dan" className="text-primary underline-offset-4 hover:underline">
            Hướng dẫn và chính sách
          </Link>
        </nav>
      </header>
      <main>{children}</main>
    </>
  );
}
