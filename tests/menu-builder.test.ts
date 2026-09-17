import { describe, expect, it } from "vitest";
import { buildCommandMenu, type MenuCommandEntry } from "../src/core/menu-builder";

const entries: MenuCommandEntry[] = [
  {
    moduleId: "core",
    moduleName: "Core",
    section: "Runtime",
    name: "alive",
    description: "Check whether Bailey is responsive.",
    enabled: true,
  },
  {
    moduleId: "core",
    moduleName: "Core",
    section: "Runtime",
    name: "status",
    description: "Show runtime status.",
    enabled: false,
  },
  {
    moduleId: "ai",
    moduleName: "AI",
    section: "Chat",
    name: "ask",
    description: "Ask the configured AI provider.",
    enabled: true,
  },
];

describe("buildCommandMenu", () => {
  it("uses the current prefix and omits disabled commands", () => {
    const menu = buildCommandMenu({ prefix: "!", entries });
    expect(menu).toContain("!alive — Check whether Bailey is responsive.");
    expect(menu).toContain("!ask — Ask the configured AI provider.");
    expect(menu).not.toContain("!status");
    expect(menu).not.toContain(".alive");
    expect(menu).toContain("Use !menu <section> to show one section.");
  });

  it("filters to a section without leaking other sections", () => {
    const menu = buildCommandMenu({ prefix: ".", entries, sectionFilter: "Runtime" });
    expect(menu).toContain("Commands · Runtime");
    expect(menu).toContain(".alive");
    expect(menu).not.toContain(".ask");
  });

  it("accepts a module-qualified section name", () => {
    const menu = buildCommandMenu({ prefix: ".", entries, sectionFilter: "AI Chat" });
    expect(menu).toContain(".ask");
    expect(menu).not.toContain(".alive");
  });

  it("returns useful section choices for an unknown filter", () => {
    const menu = buildCommandMenu({ prefix: ".", entries, sectionFilter: "Games" });
    expect(menu).toContain("No command section named “Games”.");
    expect(menu).toContain("Available sections: Chat, Runtime");
  });
});
