import { app, BrowserWindow, ipcMain, Menu, nativeImage, safeStorage, Tray } from "electron";
import { join } from "node:path";
import { baileyMarkDataUrl } from "../brand/logo";
import { JsonConfigStore, type SecretCodec } from "../core/config-store";
import { ModuleRegistry } from "../core/registry";
import type { IncomingEngineMessage } from "../engine/contracts";
import { EngineManager } from "../engine/engine-manager";
import { coreModule } from "../modules/core";

const registry = new ModuleRegistry();
registry.register(coreModule);

let mainWindow: BrowserWindow | null = null;
let tray: Tray | null = null;
let isQuitting = false;
let configStore: JsonConfigStore;
let engineManager: EngineManager;

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

function logoImage() {
  return nativeImage.createFromDataURL(baileyMarkDataUrl());
}

function createWindow(show = true): BrowserWindow {
  const window = new BrowserWindow({
    width: 1160,
    height: 780,
    minWidth: 940,
    minHeight: 640,
    show: false,
    backgroundColor: "#111315",
    title: "Bailey Host",
    icon: logoImage(),
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
  tray = new Tray(logoImage().resize({ width: 16, height: 16 }));
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

function getSetting(key: string): string | number | boolean | undefined {
  const definition = registry.findConfigDefinition(key);
  return definition ? configStore.get(definition) : undefined;
}

function moduleEnabled(moduleId: string): boolean {
  return Boolean(getSetting(`modules.${moduleId}.enabled`) ?? true);
}

function commandEnabled(moduleId: string, commandName: string): boolean {
  return Boolean(getSetting(`modules.${moduleId}.commands.${commandName}.enabled`) ?? true);
}

async function dispatchIncomingMessage(message: IncomingEngineMessage): Promise<void> {
  if (message.fromMe || !message.text?.trim()) return;
  const prefix = String(getSetting("modules.core.settings.prefix") ?? ".");
  if (!message.text.startsWith(prefix)) return;

  const body = message.text.slice(prefix.length).trim();
  if (!body) return;
  const [commandName, ...args] = body.split(/\s+/);
  const resolved = registry.resolveCommand(commandName);
  if (!resolved?.command.execute) return;
  if (!moduleEnabled(resolved.module.id) || !commandEnabled(resolved.module.id, resolved.command.name)) return;

  await resolved.command.execute({
    remoteJid: message.remoteJid,
    senderJid: message.participant ?? message.remoteJid,
    text: message.text,
    args,
    reply: async (text) => engineManager.sendText(message.remoteJid, text),
    react: async (emoji) => engineManager.react(message.remoteJid, message.key, emoji),
  });
}

function workerPath(): string {
  if (app.isPackaged) {
    return join(process.resourcesPath, "app.asar.unpacked", "dist", "engine", "worker.cjs");
  }
  return join(__dirname, "../engine/worker.cjs");
}

function broadcastEngineStatus(): void {
  const window = mainWindow;
  if (window && !window.isDestroyed()) {
    window.webContents.send("bailey:engine-status", engineManager.status());
  }
}

function registerIpc(): void {
  ipcMain.handle("bailey:get-state", () => ({
    runtime: engineManager.status().runtime,
    whatsapp: engineManager.status().whatsapp,
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

  ipcMain.handle("bailey:engine-status", () => engineManager.status());
  ipcMain.handle("bailey:engine-check-latest", () => engineManager.checkLatest());
  ipcMain.handle("bailey:engine-install-default", () => engineManager.installDefault());
  ipcMain.handle("bailey:engine-install-version", (_event, version: string) => engineManager.installVersion(version));
  ipcMain.handle("bailey:engine-update", () => engineManager.update());
  ipcMain.handle("bailey:engine-rollback", () => engineManager.rollback());
  ipcMain.handle("bailey:engine-start", () => engineManager.start());
  ipcMain.handle("bailey:engine-stop", () => engineManager.stop());
  ipcMain.handle("bailey:engine-pair", (_event, phoneNumber: string) => {
    engineManager.requestPairingCode(phoneNumber);
    return engineManager.status();
  });
}

app.on("before-quit", () => {
  isQuitting = true;
  void engineManager?.stop();
});

app.whenReady().then(async () => {
  app.setAppUserModelId("dev.bailey.host");

  configStore = new JsonConfigStore(
    join(app.getPath("userData"), "config.json"),
    createSecretCodec(),
  );
  await configStore.load();

  engineManager = new EngineManager(
    join(app.getPath("userData"), "engines"),
    workerPath(),
    process.execPath,
  );
  await engineManager.initialize();
  engineManager.on("status", broadcastEngineStatus);
  engineManager.on("message", (message: IncomingEngineMessage) => {
    void dispatchIncomingMessage(message).catch((error) => console.error("Command dispatch failed", error));
  });

  const autoStart = registry.findConfigDefinition("modules.core.settings.autoStart");
  if (autoStart) app.setLoginItemSettings({ openAtLogin: Boolean(configStore.get(autoStart)) });

  registerIpc();
  createTray();

  const ciSmoke = process.argv.includes("--ci-smoke");
  const ciEngineSmoke = process.argv.includes("--ci-engine-smoke");
  mainWindow = createWindow(!ciSmoke && !ciEngineSmoke);

  if (ciEngineSmoke) {
    try {
      await engineManager.installDefault();
      console.log(`ENGINE_SMOKE_OK:${engineManager.status().activeVersion}`);
      isQuitting = true;
      app.quit();
    } catch (error) {
      console.error(error);
      process.exitCode = 1;
      isQuitting = true;
      app.quit();
    }
  } else if (ciSmoke) {
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
