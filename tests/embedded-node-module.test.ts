import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { createModuleScaffold } from "../src/core/module-scaffold";
import { ExternalModuleManager } from "../src/external/external-module-manager";

const tempDirs: string[] = [];

afterEach(async () => {
  await Promise.all(tempDirs.splice(0).map((dir) => rm(dir, { recursive: true, force: true })));
});

describe("Bailey embedded Node runtime", () => {
  it("runs a generated JavaScript module without a system node command", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-embedded-node-"));
    tempDirs.push(root);
    const scaffold = createModuleScaffold({
      id: "hello-js",
      name: "Hello JS",
      runtime: "javascript",
      firstCommand: "hello",
      firstSection: "General",
    });
    const directory = join(root, scaffold.manifest.id);
    await mkdir(directory, { recursive: true });
    for (const [name, content] of Object.entries(scaffold.files)) {
      await writeFile(join(directory, name), content, "utf8");
    }
    const manifestPath = join(directory, "bailey.module.json");
    const manifest = JSON.parse(await readFile(manifestPath, "utf8")) as Record<string, unknown>;
    manifest.permissions = ["whatsapp.send"];
    await writeFile(manifestPath, JSON.stringify(manifest), "utf8");

    const manager = new ExternalModuleManager(root, () => ({}), () => true, join(root, ".data"), () => ["whatsapp.send"]);
    const loaded = await manager.load();
    expect(loaded.errors).toEqual([]);
    const command = loaded.definitions[0]?.commands?.[0];
    expect(command?.name).toBe("hello");

    const replies: string[] = [];
    await command!.execute!({
      remoteJid: "123@s.whatsapp.net",
      text: ".hello",
      args: [],
      reply: async (text) => { replies.push(text); },
      react: async () => {},
      showMenu: async () => {},
    });

    expect(replies).toEqual(["Hello from Hello JS!"]);
    await manager.stopAll();
  });
});
