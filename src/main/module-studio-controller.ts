import { ipcMain, shell } from "electron";
import { mkdir, readFile, readdir, rename, rm, stat, writeFile } from "node:fs/promises";
import { extname, join, relative, resolve, sep } from "node:path";
import { createModuleScaffold, type ModuleScaffoldInput } from "../core/module-scaffold";
import { detectModuleRuntimes } from "../core/runtime-detector";
import { ModulePackageManager } from "./module-package-manager";
import { ModuleRuntimeManager } from "./module-runtime-manager";
import type { ExternalModuleManager } from "../external/external-module-manager";

const WORKSPACE_EXCLUDES = new Set(["node_modules", ".bailey-venv", ".bailey-runtime", ".git", "__pycache__"]);
const EDITABLE_EXTENSIONS = new Set([
  ".ts", ".tsx", ".js", ".mjs", ".cjs", ".json", ".py", ".java", ".go", ".rs",
  ".md", ".txt", ".yaml", ".yml", ".toml", ".xml", ".html", ".css", ".scss", ".sql",
]);
const MAX_WORKSPACE_FILE_BYTES = 2 * 1024 * 1024;

function safeModuleId(value: unknown): string {
  const id = String(value ?? "").trim().toLowerCase();
  if (!/^[a-z0-9][a-z0-9.-]{0,63}$/.test(id)) throw new Error("Invalid module id.");
  return id;
}

function safeRelativePath(value: unknown): string {
  const path = String(value ?? "").replace(/\\/g, "/").replace(/^\/+/, "").trim();
  if (!path || path.includes("\0")) throw new Error("Workspace path is required.");
  const parts = path.split("/");
  if (parts.some((part) => !part || part === "." || part === ".." || part.includes(":"))) {
    throw new Error("Workspace path is unsafe.");
  }
  if (parts.some((part) => WORKSPACE_EXCLUDES.has(part))) throw new Error("That runtime folder is hidden from Studio.");
  return parts.join("/");
}

function modulePath(root: string, moduleIdValue: unknown, relativePathValue?: unknown): { moduleId: string; directory: string; path?: string; relativePath?: string } {
  const moduleId = safeModuleId(moduleIdValue);
  const directory = resolve(root, moduleId);
  if (relativePathValue === undefined) return { moduleId, directory };
  const relativePath = safeRelativePath(relativePathValue);
  const path = resolve(directory, ...relativePath.split("/"));
  if (!path.startsWith(directory + sep)) throw new Error("Workspace path escapes the module folder.");
  return { moduleId, directory, path, relativePath };
}

function editable(path: string): boolean {
  return EDITABLE_EXTENSIONS.has(extname(path).toLowerCase()) || ["Dockerfile", "Makefile", "requirements.txt"].includes(path.split("/").at(-1) ?? "");
}

export class ModuleStudioController {
  private readonly packages: ModulePackageManager;
  private readonly runtimes: ModuleRuntimeManager;

  constructor(
    private readonly modulesRoot: string,
    private readonly getExternalManager: () => ExternalModuleManager | undefined = () => undefined,
    withProgramNetworkAccess: <T>(program: string, operation: () => Promise<T>) => Promise<T> = (_program, operation) => operation(),
  ) {
    this.packages = new ModulePackageManager(modulesRoot);
    this.runtimes = new ModuleRuntimeManager(modulesRoot, process.execPath, withProgramNetworkAccess);
  }

  registerIpc(): void {
    ipcMain.handle("bailey:studio-detect-runtimes", () => detectModuleRuntimes());
    ipcMain.handle("bailey:studio-export-module", (_event, moduleId: string) => this.packages.exportModule(moduleId));
    ipcMain.handle("bailey:studio-install-module", () => this.packages.installModule());
    ipcMain.handle("bailey:studio-install-dependencies", async (_event, moduleIdValue: string) => {
      const moduleId = safeModuleId(moduleIdValue);
      const manager = this.getExternalManager();
      const wasRunning = manager?.isRunning(moduleId) ?? false;
      if (wasRunning) await manager!.stopModule(moduleId);
      try {
        const result = await this.runtimes.installDependencies(moduleId);
        if (wasRunning) await manager!.restartModule(moduleId);
        return { ...result, restarted: wasRunning };
      } catch (error) {
        if (wasRunning) {
          try { await manager!.restartModule(moduleId); }
          catch (restartError) {
            const detail = restartError instanceof Error ? restartError.message : String(restartError);
            throw new Error(`${error instanceof Error ? error.message : String(error)}\nModule restart also failed: ${detail}`);
          }
        }
        throw error;
      }
    });

    ipcMain.handle("bailey:studio-workspace-list", async (_event, moduleIdValue: string) => {
      const { directory } = modulePath(this.modulesRoot, moduleIdValue);
      const output: Array<{ path: string; type: "file" | "directory"; editable: boolean; size?: number }> = [];
      const walk = async (current: string): Promise<void> => {
        const entries = await readdir(current, { withFileTypes: true });
        for (const entry of entries) {
          if (WORKSPACE_EXCLUDES.has(entry.name) || entry.isSymbolicLink()) continue;
          const absolute = resolve(current, entry.name);
          const rel = relative(directory, absolute).split(sep).join("/");
          if (entry.isDirectory()) {
            output.push({ path: rel, type: "directory", editable: false });
            await walk(absolute);
          } else if (entry.isFile()) {
            const info = await stat(absolute);
            output.push({ path: rel, type: "file", editable: editable(rel), size: info.size });
          }
          if (output.length > 2000) throw new Error("Module workspace contains too many files to display.");
        }
      };
      await walk(directory);
      return output.sort((a, b) => a.path.localeCompare(b.path));
    });

    ipcMain.handle("bailey:studio-workspace-read", async (_event, moduleId: string, relativePath: string) => {
      const target = modulePath(this.modulesRoot, moduleId, relativePath);
      if (!editable(target.relativePath!)) throw new Error("That file type is not editable in Bailey Studio.");
      const info = await stat(target.path!);
      if (!info.isFile()) throw new Error("Workspace item is not a file.");
      if (info.size > MAX_WORKSPACE_FILE_BYTES) throw new Error("Workspace editor supports files up to 2 MB.");
      return { moduleId: target.moduleId, path: target.relativePath!, content: await readFile(target.path!, "utf8") };
    });

    ipcMain.handle("bailey:studio-workspace-write", async (_event, moduleId: string, relativePath: string, text: string) => {
      const target = modulePath(this.modulesRoot, moduleId, relativePath);
      if (!editable(target.relativePath!)) throw new Error("That file type is not editable in Bailey Studio.");
      const value = String(text ?? "");
      if (Buffer.byteLength(value, "utf8") > MAX_WORKSPACE_FILE_BYTES) throw new Error("Workspace editor supports files up to 2 MB.");
      await writeFile(target.path!, value, "utf8");
      return { ok: true, path: target.relativePath };
    });

    ipcMain.handle("bailey:studio-workspace-create", async (_event, moduleId: string, relativePath: string, type: "file" | "directory") => {
      const target = modulePath(this.modulesRoot, moduleId, relativePath);
      if (type === "directory") {
        await mkdir(target.path!, { recursive: false });
      } else if (type === "file") {
        if (!editable(target.relativePath!)) throw new Error("Choose a text/code extension Bailey Studio can edit.");
        await writeFile(target.path!, "", { encoding: "utf8", flag: "wx" });
      } else {
        throw new Error("Workspace item type is invalid.");
      }
      return { ok: true, path: target.relativePath };
    });

    ipcMain.handle("bailey:studio-workspace-rename", async (_event, moduleId: string, from: string, to: string) => {
      const source = modulePath(this.modulesRoot, moduleId, from);
      const destination = modulePath(this.modulesRoot, moduleId, to);
      try {
        await stat(destination.path!);
        throw new Error("A workspace item already exists at the destination.");
      } catch (error) {
        if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
      }
      await rename(source.path!, destination.path!);
      return { ok: true, from: source.relativePath, to: destination.relativePath };
    });

    ipcMain.handle("bailey:studio-workspace-delete", async (_event, moduleId: string, relativePath: string) => {
      const target = modulePath(this.modulesRoot, moduleId, relativePath);
      if (target.relativePath === "bailey.module.json") throw new Error("Bailey will not delete a module manifest from Studio.");
      await rm(target.path!, { recursive: true, force: false });
      return { ok: true, path: target.relativePath };
    });

    ipcMain.handle("bailey:studio-create-module", async (_event, input: ModuleScaffoldInput) => {
      const scaffold = createModuleScaffold(input);
      const directory = join(this.modulesRoot, scaffold.manifest.id);

      try {
        await mkdir(this.modulesRoot, { recursive: true });
        await mkdir(directory, { recursive: false });
        for (const [relativePath, content] of Object.entries(scaffold.files)) {
          await writeFile(join(directory, relativePath), content, { encoding: "utf8", flag: "wx" });
        }
      } catch (error) {
        const code = (error as NodeJS.ErrnoException).code;
        if (code !== "EEXIST") await rm(directory, { recursive: true, force: true });
        if (code === "EEXIST") throw new Error(`A module folder named ${scaffold.manifest.id} already exists.`);
        throw error;
      }

      return {
        ok: true,
        id: scaffold.manifest.id,
        name: scaffold.manifest.name,
        directory,
        entryFile: join(directory, scaffold.entryFile),
        runtime: input.runtime,
      };
    });

    ipcMain.handle("bailey:studio-show-module", async (_event, moduleId: string) => {
      const safeId = String(moduleId ?? "").trim().toLowerCase();
      if (!/^[a-z0-9][a-z0-9.-]{0,63}$/.test(safeId)) throw new Error("Invalid module id.");
      const directory = join(this.modulesRoot, safeId);
      const error = await shell.openPath(directory);
      if (error) throw new Error(error);
      return { ok: true, directory };
    });
  }
}
