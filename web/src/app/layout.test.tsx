import { isValidElement, type ReactElement } from "react";
import { describe, expect, it, vi } from "vitest";

vi.mock("./globals.css", () => ({}));
const fontOptions = vi.hoisted(() => [] as unknown[]);
vi.mock("next/font/google", () => ({
  Be_Vietnam_Pro: (options: unknown) => {
    fontOptions.push(options);
    return { variable: "font-variable-class", className: "font-class", style: { fontFamily: "Be Vietnam Pro" } };
  },
}));

const { default: RootLayout, viewport } = await import("./layout");

describe("viewport", () => {
  it("colors the browser bar with the page background token (#95)", async () => {
    const { readFileSync } = await import("node:fs");
    const { resolve } = await import("node:path");
    const css = readFileSync(resolve(__dirname, "globals.css"), "utf8");
    const background = /--background:\s*(#[0-9a-f]{6});/i.exec(css)?.[1];

    expect(background).toBeDefined();
    expect(viewport.themeColor).toBe(background);
  });
});

describe("font", () => {
  it("loads only the approved Be Vietnam Pro weights with Vietnamese marks (#95)", () => {
    expect(fontOptions).toEqual([
      { weight: ["400", "500", "600"], subsets: ["latin", "vietnamese"], variable: "--font-be-vietnam-pro" },
    ]);
  });

  it("puts the font variable on <html> and uses it first in --font-sans", async () => {
    const { readFileSync } = await import("node:fs");
    const { resolve } = await import("node:path");
    const css = readFileSync(resolve(__dirname, "globals.css"), "utf8");
    const html = RootLayout({ children: null, params: Promise.resolve({}) } as never) as ReactElement<Props>;

    expect(html.props.className).toBe("font-variable-class");
    expect(css).toMatch(/--font-sans:\s*var\(--font-be-vietnam-pro\),/);
  });
});

type Props = { lang?: string; className?: string; suppressHydrationWarning?: boolean; children?: unknown };

describe("RootLayout", () => {
  it("ignores attributes that browser extensions add to <html>, and nothing deeper", () => {
    const html = RootLayout({ children: "nội dung", params: Promise.resolve({}) } as never) as ReactElement<Props>;

    expect(html.type).toBe("html");
    expect(html.props.lang).toBe("vi");
    expect(html.props.suppressHydrationWarning).toBe(true);

    const body = html.props.children as ReactElement<Props>;
    expect(isValidElement(body) && body.type).toBe("body");
    expect(body.props.suppressHydrationWarning).toBeUndefined();
  });
});
