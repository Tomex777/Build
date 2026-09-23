import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { EngineManager } from "../src/engine/engine-manager";

const roots: string[] = [];
afterEach(async () => { await Promise.all(roots.splice(0).map((root) => rm(root, { recursive: true, force: true }))); });

describe("replaceable WhatsApp engine providers", () => {
  it("migrates existing Lia installs and keeps provider versions separate", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-engine-providers-"));
    roots.push(root);
    await writeFile(join(root, "engine.json"), JSON.stringify({
      provider: "lia",
      packageName: "@itsliaaa/baileys",
      apiVersion: 1,
      activeVersion: "0.3.18-final",
      previousVersion: "0.3.17-final",
      installedVersions: ["0.3.17-final", "0.3.18-final"],
      autoUpdate: false,
      channel: "stable",
    }));
    const manager = new EngineManager(root, "worker.cjs", "Bailey Host.exe");
    await manager.initialize();
    expect(manager.status()).toMatchObject({ provider: "lia", packageName: "@itsliaaa/baileys", activeVersion: "0.3.18-final" });

    await manager.setProvider("baileys");
    expect(manager.status()).toMatchObject({ provider: "baileys", packageName: "@whiskeysockets/baileys", activeVersion: undefined });
    await manager.setProvider("lia");
    expect(manager.status()).toMatchObject({ provider: "lia", activeVersion: "0.3.18-final", previousVersion: "0.3.17-final" });

    const manifest = JSON.parse(await readFile(join(root, "engine.json"), "utf8")) as { providers: Record<string, { activeVersion?: string }> };
    expect(manifest.providers.lia.activeVersion).toBe("0.3.18-final");
    expect(manifest.providers.baileys.activeVersion).toBeUndefined();
  });

  it("rejects unsupported engines without changing the selected provider", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-engine-invalid-"));
    roots.push(root);
    const manager = new EngineManager(root, "worker.cjs", "Bailey Host.exe");
    await manager.initialize();
    await expect(manager.setProvider("unknown")).rejects.toThrow("Unsupported WhatsApp engine provider");
    expect(manager.status().provider).toBe("lia");
  });
});
