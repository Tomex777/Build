import { app } from "electron";
import { mkdir, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { createModuleScaffold } from "../core/module-scaffold";
import { ExternalModuleManager } from "../external/external-module-manager";
import "./main";
import { ModuleStudioController } from "./module-studio-controller";

async function runModuleSmoke(): Promise<void> {
  const root = join(app.getPath("temp"), `bailey-module-smoke-${process.pid}`);
  const scaffold = createModuleScaffold({
    id: "packaged-smoke",
    name: "Packaged Smoke",
    runtime: "javascript",
    firstCommand: "hello",
    firstSection: "CI",
  });
  const directory = join(root, scaffold.manifest.id);
  const manager = new ExternalModuleManager(root, () => ({}));

  try {
    await rm(root, { recursive: true, force: true });
    await mkdir(directory, { recursive: true });
    for (const [name, content] of Object.entries(scaffold.files)) {
      await writeFile(join(directory, name), content, "utf8");
    }

    const loaded = await manager.load();
    if (loaded.errors.length) throw new Error(loaded.errors.map((item) => `${item.folder}: ${item.error}`).join("; "));
    const command = loaded.definitions[0]?.commands?.find((item) => item.name === "hello");
    if (!command?.execute) throw new Error("Generated JavaScript command did not load.");

    const replies: string[] = [];
    await command.execute({
      remoteJid: "ci@s.whatsapp.net",
      text: ".hello",
      args: [],
      reply: async (text) => { replies.push(text); },
      react: async () => {},
      showMenu: async () => {},
    });
    if (replies[0] !== "Hello from Packaged Smoke!") {
      throw new Error(`Unexpected module reply: ${replies[0] ?? "none"}`);
    }
    console.log("MODULE_SMOKE_OK:embedded-node");
  } finally {
    await manager.stopAll();
    await rm(root, { recursive: true, force: true });
  }
}

app.whenReady().then(() => {
  const modulesRoot = join(app.getPath("userData"), "modules");
  new ModuleStudioController(modulesRoot).registerIpc();

  if (process.argv.includes("--ci-module-smoke")) {
    setTimeout(() => {
      void runModuleSmoke().then(() => {
        app.quit();
      }).catch((error) => {
        console.error(error);
        process.exitCode = 1;
        app.quit();
      });
    }, 500);
  }
});
