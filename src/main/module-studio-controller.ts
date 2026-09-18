import { ipcMain, shell } from "electron";
import { mkdir, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { createModuleScaffold, type ModuleScaffoldInput } from "../core/module-scaffold";
import { detectModuleRuntimes } from "../core/runtime-detector";
import { ModulePackageManager } from "./module-package-manager";

export class ModuleStudioController {
  private readonly packages: ModulePackageManager;

  constructor(private readonly modulesRoot: string) {
    this.packages = new ModulePackageManager(modulesRoot);
  }

  registerIpc(): void {
    ipcMain.handle("bailey:studio-detect-runtimes", () => detectModuleRuntimes());
    ipcMain.handle("bailey:studio-export-module", (_event, moduleId: string) => this.packages.exportModule(moduleId));
    ipcMain.handle("bailey:studio-install-module", () => this.packages.installModule());

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
