// @vitest-environment jsdom
// Web parts of #95 (docs/design/ui/02-nen-thiet-ke.md, 10-ra-soat-nen-08-11.md). These rules are easy
// to lose when a shadcn component is added again from the registry, so the tests read the source.
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { buttonVariants } from "./button";
import { Input } from "./input";

// Comments removed: globals.css explains why the faded ring was dropped, and that is not a rule.
const css = readFileSync(resolve(__dirname, "../../app/globals.css"), "utf8").replace(/\/\*[\s\S]*?\*\//g, "");

describe("web foundation (#95)", () => {
  it("draws focus as a solid 2px primary outline 2px away, never the faded ring", () => {
    expect(css).toMatch(/:focus-visible\s*{\s*outline:\s*2px solid var\(--ring\);\s*outline-offset:\s*2px;/);
    expect(css).not.toMatch(/ring\/50/);
    for (const variant of ["default", "destructive", "outline", "secondary", "ghost", "link"] as const) {
      const classes = buttonVariants({ variant });
      expect(classes).toContain("focus-visible:outline-2");
      expect(classes).not.toMatch(/ring-ring\/50|ring-destructive\/20|ring-\[3px\]/);
    }
  });

  it("animates only the properties that change, and not at all with reduced motion", () => {
    const classes = buttonVariants();
    expect(classes).not.toContain("transition-all");
    expect(classes).toContain("motion-reduce:transition-none");
  });

  it("keeps inputs at 16px on every screen size, with the same focus outline", async () => {
    await render(<Input aria-label="Email" />);
    const input = screen.getByLabelText("Email");

    expect(input.className).toContain("text-base");
    expect(input.className).not.toMatch(/md:text-sm/);
    expect(input.className).toContain("focus-visible:outline-2");
    expect(input.className).not.toMatch(/ring-ring\/50/);
  });

  it("styles text selection, the caret and the scrollbar with the tokens", () => {
    expect(css).toMatch(/::selection\s*{\s*background-color:\s*var\(--primary\);\s*color:\s*var\(--primary-foreground\);/);
    expect(css).toMatch(/caret-color:\s*var\(--primary\);/);
    expect(css).toMatch(/scrollbar-color:\s*var\(--muted-foreground\) var\(--muted\);/);
  });
});
