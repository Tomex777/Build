import { describe, expect, it } from "vitest";
import { MODULE_PACKAGE_EXCLUDES, validateModulePackageEntries } from "../src/core/module-package-policy";

describe("Bailey module package safety policy", () => {
  it("allows source and dependency declarations while rejecting local environments and secret paths", () => {
    const entry = (path: string) => ({ path, data: "", size: 0 });
    expect(() => validateModulePackageEntries([
      entry("bailey.module.json"), entry("main.py"), entry("package.json"), entry("requirements.txt"),
    ])).not.toThrow();
    for (const path of ["node_modules/a.js", ".bailey-venv/Scripts/python.exe", ".bailey-runtime/node.exe", ".data/state.json", ".env.local", "sessions/default/creds.json", "credentials.json"]) {
      expect(() => validateModulePackageEntries([entry(path)])).toThrow("local environment, cache or secret path");
      expect(MODULE_PACKAGE_EXCLUDES.has(path.split("/")[0])).toBe(true);
    }
  });
});
