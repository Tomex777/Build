import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import { BACKUP_EXCLUDES, validateBackupEntries } from "../src/core/backup-policy";
import { collectArchiveFiles } from "../src/core/portable-archive";

const roots: string[] = [];
afterEach(async () => Promise.all(roots.splice(0).map((root) => rm(root, { recursive: true, force: true }))));

describe("ordinary Bailey backup safety policy", () => {
  it("backs up module data but skips local environments and auth/session secrets", async () => {
    const root = await mkdtemp(join(tmpdir(), "bailey-backup-policy-"));
    roots.push(root);
    const modules = join(root, "modules");
    await mkdir(join(modules, ".data", "economy"), { recursive: true });
    await mkdir(join(modules, "economy", "node_modules", "leftpad"), { recursive: true });
    await mkdir(join(modules, "economy", ".bailey-venv", "Scripts"), { recursive: true });
    await mkdir(join(modules, "economy", "sessions", "default"), { recursive: true });
    await writeFile(join(modules, "economy", "bailey.module.json"), "{}\n");
    await writeFile(join(modules, ".data", "economy", "balance.sqlite"), "account-data");
    await writeFile(join(modules, "economy", "node_modules", "leftpad", "index.js"), "local-runtime");
    await writeFile(join(modules, "economy", ".bailey-venv", "Scripts", "python.exe"), "local-runtime");
    await writeFile(join(modules, "economy", "sessions", "default", "creds.json"), "whatsapp-auth");

    const entries = await collectArchiveFiles(modules, { prefix: "modules", excludeNames: BACKUP_EXCLUDES });
    expect(entries.map((entry) => entry.path).sort()).toEqual(["modules/.data/economy/balance.sqlite", "modules/economy/bailey.module.json"]);
    expect(() => validateBackupEntries(entries)).not.toThrow();
  });

  it("rejects auth, engine, unsupported and unsafe restore paths", () => {
    const entry = (path: string) => ({ path, data: "", size: 0 });
    expect(() => validateBackupEntries([entry("config.json"), entry("modules/.data/economy/state.json"), entry("storage/profiles/local/a.bin")])).not.toThrow();
    for (const path of ["sessions/default/creds.json", "engines/lia/1/package.json", "modules/economy/auth.json", "modules/economy/node_modules/a.js", "other/file.txt"]) {
      expect(() => validateBackupEntries([entry(path)])).toThrow("forbidden");
    }
  });
});
