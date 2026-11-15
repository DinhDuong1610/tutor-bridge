import { colors, fonts, radius, spacing, typography } from "./theme";

// The mobile tsconfig has no Node types on purpose (app code runs on Hermes, not Node), so this
// test declares only the two calls it makes. Jest itself runs in Node.
declare const __dirname: string;
const { readFileSync } = jest.requireActual<{ readFileSync(path: string, encoding: "utf8"): string }>("fs");
const { join } = jest.requireActual<{ join(...parts: string[]): string }>("path");

// The token table in docs/design/ui/02-nen-thiet-ke.md is the shared source of Figma, mobile and
// web (#95). These tests fail when theme.ts drifts from it.
const doc = readFileSync(join(__dirname, "../../../../docs/design/ui/02-nen-thiet-ke.md"), "utf8");

/** Rows of the color table: | `figma` | `mobile` | `web` | `#value` | use | */
function documentedColors(): Record<string, string> {
  const rows = [...doc.matchAll(/^\| `[^`]+`[^|]*\| `(\w+)` \| [^|]+\| `(#[0-9A-F]{6})` \|/gm)];
  return Object.fromEntries(rows.map(([, name, value]) => [name, value]));
}

describe("theme tokens", () => {
  it("has exactly the colors of the design table", () => {
    const documented = documentedColors();
    expect(Object.keys(documented).length).toBeGreaterThan(10);
    expect(colors).toEqual(documented);
  });

  it("uses the approved radius and spacing scale", () => {
    expect(radius).toEqual({ sm: 8, md: 12, full: 999 });
    expect(spacing).toEqual({ xs: 4, sm: 8, md: 16, lg: 24, xl: 32, xxl: 48 });
  });

  it("has the sizes and line heights of the type table", () => {
    // | `title` | `title` | 24 / 32 | 600 | ...
    const rows = [...doc.matchAll(/^\| `[^`]+`[^|]*\| `(\w+)` \| (\d+) \/ (\d+)[^|]*\| (\d{3}) \|/gm)];
    const documented = Object.fromEntries(rows.map(([, name, size, line, weight]) => [name, { size: +size, line: +line, weight }]));
    const weightOf = { [fonts.regular]: "400", [fonts.medium]: "500", [fonts.semiBold]: "600" };
    const actual = Object.fromEntries(
      Object.entries(typography).map(([name, style]) => [name, { size: style.fontSize, line: style.lineHeight, weight: weightOf[style.fontFamily] }]),
    );
    expect(Object.keys(documented)).toHaveLength(7);
    expect(actual).toEqual(documented);
  });

  it("sets each weight as its own Be Vietnam Pro family, never fontWeight", () => {
    const families: string[] = Object.values(fonts);
    for (const style of Object.values(typography)) {
      expect(families).toContain(style.fontFamily);
      expect(style).not.toHaveProperty("fontWeight");
    }
    expect(families).toEqual(["BeVietnamPro_400Regular", "BeVietnamPro_500Medium", "BeVietnamPro_600SemiBold"]);
  });
});
