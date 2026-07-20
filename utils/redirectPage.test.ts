import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const redirectHtml = readFileSync(resolve(process.cwd(), "docs/redirect.html"), "utf8");

describe("docs/redirect.html", () => {
  it("includes a decorative floating background layer behind the sheet", () => {
    expect(redirectHtml).toContain('<div class="bg-shapes" aria-hidden="true">');
    expect(redirectHtml).toContain('class="bg-shape bg-shape--one"');
    expect(redirectHtml).toContain('class="bg-shape bg-shape--four"');
  });

  it("replaces the old single dot brand mark with a compact android face", () => {
    expect(redirectHtml).toContain('<span class="brand__mark" aria-hidden="true">');
    expect(redirectHtml).toContain('<span class="brand__android-head"></span>');
    expect(redirectHtml).toContain('<span class="brand__android-eyes"></span>');
  });

  it("keeps reduced motion support for the new decorative animations", () => {
    expect(redirectHtml).toContain("@media (prefers-reduced-motion: reduce)");
    expect(redirectHtml).toContain(".bg-shape");
  });

  it("uses clearly animated floating shapes instead of static background blobs", () => {
    expect(redirectHtml).toContain("animation-play-state: running;");
    expect(redirectHtml).toContain("animation: float-one 11s ease-in-out infinite alternate");
    expect(redirectHtml).toContain("transform: translate3d(88px, 42px, 0) scale(1.08);");
  });

  it("does not include the old Intent Handoff text", () => {
    expect(redirectHtml).not.toContain("Intent Handoff");
  });

  it("verifies the page is scrollable on overflow", () => {
    expect(redirectHtml).toContain("overflow-y: auto");
  });
});
