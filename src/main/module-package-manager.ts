import { dialog } from "electron";
import { mkdir, readFile, rm } from "node:fs/promises";
import { basename, join } from "node:path";
import { collectArchiveFiles, decodePortableArchive, encodePortableArchive, writeArchiveFiles, type PortableArchive } from "../core/portable-archive";
import { parseExternalModuleManifest } from "../external/protocol";

const PACKAGE_EXCLUDES = new Set(["node_modules", ".venv", "venv", "__pycache__", ".git", ".data"]);

function safeModuleId(value: unknown): string {
  const id = String(value ?? "").trim().toLowerCase();
  if (!/^[a-z0-9][a-z0-9.-]{0,63}$/.test(id)) throw new Error("Invalid module id.");
  return id;
}

export class ModulePackageManager {
  constructor(private readonly modulesRoot: string) {}

  async exportModule(moduleIdValue: unknown): Promise<{ ok: boolean; path?: string; canceled?: boolean }> {
    const moduleId = safeModuleId(moduleIdValue);
    const directory = join(this.modulesRoot, moduleId);
    const manifestRaw = await readFile(join(directory, "bailey.module.json"), "utf8");
    const manifest = parseExternalModuleManifest(JSON.parse(manifestRaw));
    if (manifest.id !== moduleId) throw new Error("Module folder and manifest id do not match.");

    const result = await dialog.showSaveDialog({
      title: "Export Bailey module",
      defaultPath: `${moduleId}-${manifest.version}.baileypkg`,
      filters: [{ name: "Bailey module package", extensions: ["baileypkg"] }],
    });
    if (result.canceled || !result.filePath) return { ok: false, canceled: true };

    const archive: PortableArchive = {
      format: "bailey-portable-archive",
      version: 1,
      kind: "module",
      createdAt: Date.now(),
      metadata: { moduleId, name: manifest.name, version: manifest.version },
      files: await collectArchiveFiles(directory, { excludeNames: PACKAGE_EXCLUDES }),
    };
    await import("node:fs/promises").then(({ writeFile }) => writeFile(result.filePath!, encodePortableArchive(archive)));
    return { ok: true, path: result.filePath };
  }

  async installModule(): Promise<{ ok: boolean; id?: string; name?: string; canceled?: boolean }> {
    const result = await dialog.showOpenDialog({
      title: "Install Bailey module",
      properties: ["openFile"],
      filters: [{ name: "Bailey module package", extensions: ["baileypkg"] }],
    });
    if (result.canceled || !result.filePaths[0]) return { ok: false, canceled: true };

    const archive = decodePortableArchive(await readFile(result.filePaths[0]), "module");
    const manifestEntry = archive.files.find((entry) => entry.path === "bailey.module.json");
    if (!manifestEntry) throw new Error("Module package is missing bailey.module.json.");
    const manifest = parseExternalModuleManifest(JSON.parse(Buffer.from(manifestEntry.data, "base64").toString("utf8")));
    const directory = join(this.modulesRoot, manifest.id);

    try {
      await mkdir(this.modulesRoot, { recursive: true });
      await mkdir(directory, { recursive: false });
      await writeArchiveFiles(directory, archive.files);
    } catch (error) {
      await rm(directory, { recursive: true, force: true });
      if ((error as NodeJS.ErrnoException).code === "EEXIST") throw new Error(`Module ${manifest.id} is already installed.`);
      throw error;
    }

    return { ok: true, id: manifest.id, name: manifest.name };
  }

  packageName(path: string): string {
    return basename(path);
  }
}
