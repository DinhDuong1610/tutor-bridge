/** Temporary body for a route whose screen is not built yet. Feature Issues replace it. */
export function ComingSoon({ title }: { title: string }) {
  return (
    <section className="mx-auto flex max-w-2xl flex-col gap-2 px-4 py-12">
      <h1 className="text-2xl font-semibold">{title}</h1>
      <p className="text-muted-foreground">Trang đang được xây dựng.</p>
    </section>
  );
}
