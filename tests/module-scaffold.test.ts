import { describe, expect, it } from "vitest";
import { createModuleScaffold } from "../src/core/module-scaffold";
import { parseExternalModuleManifest } from "../src/external/protocol";

describe("createModuleScaffold", () => {
  it("creates a Python module with a valid Bailey manifest", () => {
    const scaffold = createModuleScaffold({
      id: "economy",
      name: "Economy",
      description: "Economy commands.",
      runtime: "python",
      firstCommand: "balance",
      firstSection: "Economy",
    });

    expect(scaffold.entryFile).toBe("main.py");
    expect(scaffold.manifest.runtime).toEqual({ command: "python", args: ["main.py"] });
    expect(scaffold.manifest.commands?.[0]?.name).toBe("balance");
    expect(scaffold.files["main.py"]).toContain("Hello from Economy!");
    expect(() => parseExternalModuleManifest(JSON.parse(scaffold.files["bailey.module.json"]))).not.toThrow();
  });

  it("creates a JavaScript module using Bailey embedded Node", () => {
    const scaffold = createModuleScaffold({
      id: "anime-tools",
      name: "Anime Tools",
      runtime: "javascript",
      firstCommand: "anime",
      firstSection: "Anime",
    });

    expect(scaffold.entryFile).toBe("main.mjs");
    expect(scaffold.manifest.runtime).toEqual({ command: "bailey-node", args: ["main.mjs"] });
    expect(scaffold.files["main.mjs"]).toContain("node:readline");
    expect(scaffold.files["README.md"]).toContain("Settings declared there automatically appear in Bailey Configuration");
  });

  it("rejects unsafe module ids and command names", () => {
    expect(() => createModuleScaffold({
      id: "../escape",
      name: "Bad",
      runtime: "python",
    })).toThrow(/Module id/);

    expect(() => createModuleScaffold({
      id: "safe",
      name: "Safe",
      runtime: "javascript",
      firstCommand: "not valid!",
    })).toThrow(/First command/);
  });
});
