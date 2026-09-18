import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { JsonModulePermissionStore } from "../src/core/module-permission-store";

const roots: string[] = [];
afterEach(async () => {
  await Promise.all(roots.splice(0).map((root) => rm(root, { recursive: true, force: true })));
});

describe("JsonModulePermissionStore", () => {
  it("starts denied and persists explicit grants", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-permissions-"));
    roots.push(root);
    const path = join(root, "module-permissions.json");
    const store = new JsonModulePermissionStore(path);
    await store.load();
    expect(store.get("economy")).toEqual([]);
    await store.set("economy", ["database.read", "database.write"]);
    expect(store.get("economy")).toEqual(["database.read", "database.write"]);

    const restarted = new JsonModulePermissionStore(path);
    await restarted.load();
    expect(restarted.get("economy")).toEqual(["database.read", "database.write"]);
    expect(await readFile(path, "utf8")).toContain("database.read");
  });
});
