import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

interface PermissionDocument {
  version: 1;
  grants: Record<string, string[]>;
}

const PERMISSION = /^[a-z0-9*][a-z0-9._*-]{0,95}$/;
const MODULE_ID = /^[a-z0-9][a-z0-9.-]{0,63}$/;

function cleanPermissions(values: unknown): string[] {
  if (!Array.isArray(values)) throw new Error("Permissions must be an array.");
  const output = new Set<string>();
  for (const value of values) {
    if (typeof value !== "string" || !PERMISSION.test(value)) throw new Error(`Invalid module permission: ${String(value)}`);
    output.add(value);
  }
  return [...output].sort();
}

export class JsonModulePermissionStore {
  private document: PermissionDocument = { version: 1, grants: {} };

  constructor(private readonly filePath: string) {}

  async load(): Promise<void> {
    try {
      const parsed = JSON.parse(await readFile(this.filePath, "utf8")) as PermissionDocument;
      if (parsed.version !== 1 || !parsed.grants || typeof parsed.grants !== "object") throw new Error("Unsupported Bailey permission format.");
      const grants: Record<string, string[]> = {};
      for (const [moduleId, values] of Object.entries(parsed.grants)) {
        if (!MODULE_ID.test(moduleId)) continue;
        grants[moduleId] = cleanPermissions(values);
      }
      this.document = { version: 1, grants };
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
      await this.flush();
    }
  }

  get(moduleId: string): readonly string[] {
    return [...(this.document.grants[moduleId] ?? [])];
  }

  async set(moduleId: string, permissions: unknown): Promise<readonly string[]> {
    if (!MODULE_ID.test(moduleId)) throw new Error("Invalid module id.");
    const grants = cleanPermissions(permissions);
    this.document.grants[moduleId] = grants;
    await this.flush();
    return grants;
  }

  async clear(moduleId: string): Promise<void> {
    delete this.document.grants[moduleId];
    await this.flush();
  }

  private async flush(): Promise<void> {
    await mkdir(dirname(this.filePath), { recursive: true });
    await writeFile(this.filePath, `${JSON.stringify(this.document, null, 2)}\n`, "utf8");
  }
}
