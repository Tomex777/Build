import { app, BrowserWindow, ipcMain, Menu, nativeImage, safeStorage, Tray } from "electron";
import { join } from "node:path";
import { JsonConfigStore, type SecretCodec } from "../core/config-store";
import { ModuleRegistry } from "../core/registry";
import { coreModule } from "../modules/core";

const registry = new ModuleRegistry();
registry.register(coreModule);

let mainWindow: BrowserWindow | null = null;
let tray: Tray | null = null;
let isQuitting = false;
let configStore: JsonConfigStore;

const trayPng = "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAByElEQVR4nMVXv0tCURT+fIibNiRBrqEPhbJCRKKQXIJaC3Fp0Kmp7AdBi3+GQ0tBf4AQgoOUYRHkojb52s0GjbTBBLFB3kt6P7z3+p7vbPdezvm+e75z7g+Lc841gInGmQkOAFYWJ6FWUV3z8H6qWBZSCbRAJyFDJAELOKmfZgZYgZVMLRuqGdATXCueIgG9wbXimt6GMgJG7V4tPqe1OA0SpktAfBJmszkkj89l8zabDU7nLHw+L2KxPWysr1ERkDLAmv5er4d6/R35/B0SiQNkMrdEfiIeswSv1RcItQoK9zl4vbw0f3V9QxVn4hpwueYR2QxL41brc7oEGo0PFB6K0nhlme42ZLqOAWBxKSibC4WCSKUuqOLo2oaC8IZyuTodAmIRlkpFxOP7AIb6HyXP0Gy26AnQvmREm3E4cHpyCKt1qGa3+4PHp+exfiKebhIMBn/Piu9Oh9iPuQhF+2q3kU5fot/vS3M872Ej4OH9xCeiUhcAQCQSRiCwquk7KrcsAzQkAIDjONjtdrjdC9jZ3kI0uksMDqi8CY28lv8TUCxC1o6gBVclYAQJtXhEH5NJJBm3EaJzgDUbJH7EX7NRM+VvaJT9AqAlmD649V6VAAAAAElFTkSuQmCC";

function createSecretCodec(): SecretCodec {
  return {
    encode(value: string): string {
      if (!safeStorage.isEncryptionAvailable()) {
        throw new Error("Secure OS storage is unavailable; Bailey Host will not persist secret values unencrypted.");
      }
      return safeStorage.encryptString(value).toString("base64");
    },
    decode(value: string): string {
      if (!safeStorage.isEncryptionAvailable()) return "";
      return safeStorage.decryptString(Buffer.from(value, "base64"));
    },
  };
}

function createWindow(show = true): BrowserWindow {
  const window = new BrowserWindow({
    width: 1120,
    height: 760,
    minWidth: 900,
    minHeight: 620,
    show: false,
    backgroundColor: "#111315",
    title: "Bailey Host",
    webPreferences: {
      preload: join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  window.loadFile(join(__dirname, "../renderer/index.html"));
  window.once("ready-to-show", () => {
    if (show) window.show();
  });
  window.on("close", (event) => {
    if (!isQuitting) {
      event.preventDefault();
      window.hide();
    }
  });

  return window;
}

function showMainWindow(): void {
  if (!mainWindow) mainWindow = createWindow();
  mainWindow.show();
  mainWindow.focus();
}

function createTray(): void {
  const image = nativeImage.createFromDataURL(`data:image/png;base64,${trayPng}`);
  tray = new Tray(image.resize({ width: 16, height: 16 }));
  tray.setToolTip("Bailey Host");
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: "Open Bailey Host", click: showMainWindow },
    { type: "separator" },
    {
      label: "Quit",
      click: () => {
        isQuitting = true;
        app.quit();
      },
    },
  ]));
  tray.on("double-click", showMainWindow);
}

function registerIpc(): void {
  ipcMain.handle("bailey:get-state", () => ({
    runtime: "stopped",
    whatsapp: "not-connected",
    moduleCount: registry.list().length,
    version: app.getVersion(),
  }));

  ipcMain.handle("bailey:get-modules", () => registry.list().map((module) => ({
    id: module.id,
    name: module.name,
    version: module.version,
    description: module.description,
    commands: (module.commands ?? []).map((command) => ({
      name: command.name,
      aliases: command.aliases ?? [],
      description: command.description,
    })),
  })));

  ipcMain.handle("bailey:get-config", () => {
    const definitions = registry.getConfigDefinitions();
    return {
      definitions,
      values: configStore.listForUi(definitions),
    };
  });

  ipcMain.handle("bailey:set-config", async (_event, key: string, value: unknown) => {
    const definition = registry.findConfigDefinition(key);
    if (!definition) throw new Error(`Unknown Bailey setting: ${key}`);
    if (definition.type === "secret" && value === "") return { ok: true, unchanged: true };

    await configStore.set(definition, value);

    if (key === "modules.core.settings.autoStart") {
      app.setLoginItemSettings({ openAtLogin: Boolean(configStore.get(definition)) });
    }

    return { ok: true };
  });
}

app.on("before-quit", () => {
  isQuitting = true;
});

app.whenReady().then(async () => {
  app.setAppUserModelId("dev.bailey.host");

  configStore = new JsonConfigStore(
    join(app.getPath("userData"), "config.json"),
    createSecretCodec(),
  );
  await configStore.load();

  const autoStart = registry.findConfigDefinition("modules.core.settings.autoStart");
  if (autoStart) app.setLoginItemSettings({ openAtLogin: Boolean(configStore.get(autoStart)) });

  registerIpc();
  createTray();

  const ciSmoke = process.argv.includes("--ci-smoke");
  mainWindow = createWindow(!ciSmoke);

  if (ciSmoke) {
    mainWindow.webContents.once("did-finish-load", () => setTimeout(() => {
      isQuitting = true;
      app.quit();
    }, 300));
  }

  app.on("activate", showMainWindow);
});

app.on("window-all-closed", () => {
  // Bailey Host intentionally keeps running in the tray.
});
