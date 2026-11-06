import Link from "next/link";
import { Button } from "@/shared/ui/button";

export default function NotFound() {
  return (
    <main className="mx-auto flex max-w-2xl flex-col gap-3 px-4 py-12">
      <h1 className="text-2xl font-semibold">Không tìm thấy trang</h1>
      <p className="text-muted-foreground">Đường dẫn không đúng hoặc trang đã được chuyển.</p>
      <Button asChild className="self-start">
        <Link href="/">Về trang chủ</Link>
      </Button>
    </main>
  );
}
