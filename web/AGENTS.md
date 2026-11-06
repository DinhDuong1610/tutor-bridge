## TutorBridge rules come first

Follow `../AGENTS.md` and `../docs/context/conventions.md` ("Cấu trúc web"). They override generic Next.js habits.

- Libraries are fixed by `../docs/decisions/adr/0009-nen-giao-dien.md`: Tailwind CSS 4, shadcn/ui, react-hook-form with zod, TanStack Query only for browser-side interaction. Do not add other UI or state libraries without Đính (CR-012).
- Routes: `src/app/(public)` for WEB01–WEB02 (no private learning data), `src/app/admin/(auth)` for sign-in, `src/app/admin/(console)` for AD screens. Feature code goes in `src/features/<feature>`, shared code in `src/shared`.
- shadcn/ui components live in `src/shared/ui` (see `components.json`). Add one with `npx shadcn@4.22.1 add <name> --dry-run` first: the registry imports `cn` from an npm package `cn`; change it to `@/shared/lib/utils` and do not install `cn`.
- Colors come from the CSS variables in `src/app/globals.css`; do not write raw colors in components.
- The API is called on the server only (`server-only`, `src/shared/api/server.ts`). The admin session (cookie, CSRF) is module W2.

<!-- BEGIN:nextjs-agent-rules -->

## This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->
