import { dialog } from "electron";
import { readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { collectArchiveFiles, decodePortableArchive, encodePortableArchive, writeArchiveFiles, type PortableArchive, type PortableArchiveEntry } from "../core/portable-archive";
import { BACKUP_EXCLUDES, validateBackupEntries } from "../core/backup-policy";
const ROOT_FILES = ["config.json", "commands.json", "chats.json"];

export class BaileyBackupManager {
  constructor(private readonly userDataRoot: string) {}

  async exportBackup(): Promise<{ ok: boolean; path?: string; canceled?: boolean }> {
    const result = await dialog.showSaveDialog({
      title: "Export Bailey backup",
      defaultPath: `bailey-backup-${new Date().toISOString().slice(0, 10)}.baileybackup`,
      filters: [{ name: "Bailey backup", extensions: ["baileybackup"] }],
    });
    if (result.canceled || !result.filePath) return { ok: false, canceled: true };

    const files: PortableArchiveEntry[] = [];
    for (const name of ROOT_FILES) {
      try {
        const data = await readFile(join(this.userDataRoot, name));
        files.push({ path: name, data: data.toString("base64"), size: data.byteLength });
      } catch (error) {
        if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
      }
    }
    files.push(...await collectArchiveFiles(join(this.userDataRoot, "modules"), { prefix: "modules", excludeNames: BACKUP_EXCLUDES }));
    files.push(...await collectArchiveFiles(join(this.userDataRoot, "storage"), { prefix: "storage", excludeNames: BACKUP_EXCLUDES }));

    const archive: PortableArchive = {
      format: "bailey-portable-archive",
      version: 1,
      kind: "backup",
      createdAt: Date.now(),
      metadata: {
        note: "WhatsApp sessions and installed engine binaries are intentionally excluded.",
        secretPortability: "Secret settings remain protected by the operating system and may need to be entered again on another computer.",
      },
      files,
    };
    await writeFile(result.filePath, encodePortableArchive(archive));
    return { ok: true, path: result.filePath };
  }

  async chooseAndImport(): Promise<{ ok: boolean; canceled?: boolean; fileCount?: number }> {
    const result = await dialog.showOpenDialog({
      title: "Restore Bailey backup",
      properties: ["openFile"],
      filters: [{ name: "Bailey backup", extensions: ["baileybackup"] }],
    });
    if (result.canceled || !result.filePaths[0]) return { ok: false, canceled: true };
    const archive = decodePortableArchive(await readFile(result.filePaths[0]), "backup");
    validateBackupEntries(archive.files);
    await writeArchiveFiles(this.userDataRoot, archive.files);
    return { ok: true, fileCount: archive.files.length };
  }
}
