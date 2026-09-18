import { app, BrowserWindow, dialog, ipcMain, Menu, nativeImage, type OpenDialogOptions, safeStorage, shell, Tray } from "electron";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { basename, extname, join } from "node:path";
import { baileyMarkDataUrl } from "../brand/logo";
import { JsonChatStore } from "../core/chat-store";
import { JsonCommandStore, type VisualCommandPatch } from "../core/command-store";
import { JsonConfigStore, type SecretCodec } from "../core/config-store";
import { buildCommandMenu } from "../core/menu-builder";
import type { BaileyCommandAction, CommandContext, MessageEventContext } from "../core/module";
import { ModuleRegistry } from "../core/registry";
import type { EngineHostEvent, IncomingEngineMessage } from "../engine/contracts";
import { EngineManager } from "../engine/engine-manager";
import { ExternalModuleManager } from "../external/external-module-manager";
import { registerKvServices } from "../services/kv-service";
import { registerMediaServices } from "../services/media-service";
import { LocalStorageProvider, StorageHostService } from "../services/storage-service";
import { coreModule } from "../modules/core";
import { myCommandsModule } from "../modules/my-commands";
import type { ConfigDefinition } from "../shared/config-schema";
import { ChatController } from "./chat-controller";

const registry = new ModuleRegistry();
registry.register(coreModule);
registry.register(myCommandsModule);

let mainWindow: BrowserWindow | null = null;
let tray: Tray | null = null;
let isQuitting = false;
let configStore: JsonConfigStore;
let commandStore: JsonCommandStore;
let chatStore: JsonChatStore;
let chatController: ChatController;
let engineManager: EngineManager;
let externalModuleManager: ExternalModuleManager | undefined;
let externalModuleErrors: Array<{ folder: string; error: string }> = [];
const externalModuleIds = new Set<string>();
const openedEditorFiles = new Set<string>();
const EDITABLE_EXTENSIONS = new Set([".ts", ".tsx", ".js", ".mjs", ".cjs", ".json", ".py", ".md", ".txt", ".yaml", ".yml", ".toml"]);
const MAX_EDITOR_BYTES = 2 * 1024 * 1024;

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
  window.webContents.on("before-input-event", (_event, input) => {
    if (input.key === "Escape" && window.isFullScreen()) window.setFullScreen(false);
  });

  return window;
}

function showMainWindow(): void {
  if (!mainWindow) mainWindow = createWindow();
  mainWindow.show();
  mainWindow.focus();
}

function createApplicationMenu(): void {
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    {
      label: "Bailey",
      submenu: [
        { label: "Open Bailey Host", click: showMainWindow },
        { label: "Hide to tray", click: () => mainWindow?.hide() },
        { type: "separator" },
        {
          label: "Quit Bailey Host",
          accelerator: "Alt+F4",
          click: () => {
            isQuitting = true;
            app.quit();
          },
        },
      ],
    },
    {
      label: "View",
      submenu: [
        { role: "reload" },
        { type: "separator" },
        { role: "resetZoom" },
        { role: "zoomIn" },
        { role: "zoomOut" },
        { type: "separator" },
        { role: "togglefullscreen", accelerator: "F11" },
      ],
    },
  ]));
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

function commandPrefix(): string {
  const definition = registry.findConfigDefinition("modules.core.settings.prefix");
  return definition ? String(configStore.get(definition) ?? ".") : ".";
}

function effectiveConfigDefinitions(): ConfigDefinition[] {
  const prefix = commandPrefix();
  const definitions = registry.getConfigDefinitions().map((definition) => {
    const match = definition.key.match(/^modules\.([^.]+)\.commands\.([^.]+)\.enabled$/);
    if (!match) return definition;
    const effective = commandStore.getById(registry.list(), match[1], match[2]);
    if (!effective) return definition;
    return {
      ...definition,
      label: `${prefix}${effective.name}`,
      description: effective.description,
    };
  });

  const knownKeys = new Set(definitions.map((definition) => definition.key));
  for (const { module, command } of commandStore.list(registry.list())) {
    const key = `modules.${module.id}.commands.${command.id}.enabled`;
    if (knownKeys.has(key)) continue;
    definitions.push({
      key,
      moduleId: module.id,
      section: `${module.name} · Commands`,
      label: `${prefix}${command.name}`,
      type: "toggle",
      defaultValue: true,
      description: command.description,
    });
    knownKeys.add(key);
  }
  return definitions;
}

function findEffectiveConfigDefinition(key: string): ConfigDefinition | undefined {
  return effectiveConfigDefinitions().find((definition) => definition.key === key);
}

function getSetting(key: string): string | number | boolean | undefined {
  const definition = findEffectiveConfigDefinition(key);
  return definition ? configStore.get(definition) : undefined;
}

function moduleEnabled(moduleId: string): boolean {
  return Boolean(getSetting(`modules.${moduleId}.enabled`) ?? true);
}

function commandEnabled(moduleId: string, id: string): boolean {
  return Boolean(getSetting(`modules.${moduleId}.commands.${id}.enabled`) ?? true);
}

function currentMenu(sectionFilter?: string): string {
  const entries = commandStore.list(registry.list()).map(({ module, command }) => ({
    moduleId: module.id,
    moduleName: module.name,
    section: command.section,
    name: command.name,
    description: command.description,
    enabled: moduleEnabled(module.id) && commandEnabled(module.id, command.id),
  }));
  return buildCommandMenu({
    prefix: commandPrefix(),
    entries,
    sectionFilter,
  });
}

async function runDeclarativeActions(actions: BaileyCommandAction[], context: Pick<CommandContext, "reply" | "react">): Promise<void> {
  for (const action of actions) {
    if (action.type === "reply") await context.reply(action.text);
    if (action.type === "react") await context.react(action.emoji);
  }
}

function messageEventContext(message: IncomingEngineMessage): MessageEventContext & { id?: string; media?: IncomingEngineMessage["media"] } {
  return {
    id: message.id,
    remoteJid: message.remoteJid,
    senderJid: message.participant ?? message.remoteJid,
    text: message.text,
    pushName: message.pushName,
    timestamp: message.timestamp,
    media: message.media,
    reply: async (text: string) => engineManager.sendText(message.remoteJid, text),
    react: async (emoji: string) => engineManager.react(message.remoteJid, message.key, emoji),
  };
}

async function dispatchModuleMessageEvents(message: IncomingEngineMessage): Promise<void> {
  if (message.fromMe) return;
  const listeners = registry.list().filter((module) => module.onMessage && moduleEnabled(module.id));
  if (!listeners.length) return;

  const context = messageEventContext(message);
  const results = await Promise.allSettled(listeners.map((module) => module.onMessage!(context)));
  results.forEach((result, index) => {
    if (result.status === "rejected") {
      const module = listeners[index];
      console.error(`[module:${module.id}] message event failed`, result.reason);
    }
  });
}

async function dispatchIncomingMessage(message: IncomingEngineMessage): Promise<void> {
  if (message.fromMe || !message.text?.trim()) return;
  const prefix = commandPrefix();
  if (!message.text.startsWith(prefix)) return;

  const body = message.text.slice(prefix.length).trim();
  if (!body) return;
  const [commandName, ...args] = body.split(/\s+/);
  const resolved = commandStore.resolve(registry.list(), commandName);
  if (!resolved) return;
  if (!moduleEnabled(resolved.module.id) || !commandEnabled(resolved.module.id, resolved.command.id)) return;

  const context: CommandContext = {
    remoteJid: message.remoteJid,
    senderJid: message.participant ?? message.remoteJid,
    text: message.text,
    args,
    reply: async (text: string) => engineManager.sendText(message.remoteJid, text),
    react: async (emoji: string) => engineManager.react(message.remoteJid, message.key, emoji),
    showMenu: async (sectionFilter?: string) => engineManager.sendText(message.remoteJid, currentMenu(sectionFilter)),
  };

  if (resolved.command.actions?.length) {
    await runDeclarativeActions(resolved.command.actions, context);
  } else if (resolved.command.execute) {
    await resolved.command.execute(context);
  }
}

function workerPath(): string {
  if (app.isPackaged) {
    return join(process.resourcesPath, "app.asar.unpacked", "dist", "engine", "worker.cjs");
  }
  return join(__dirname, "../engine/worker.cjs");
}

function modulesRoot(): string {
  return join(app.getPath("userData"), "modules");
}

async function reloadExternalModules() {
  await externalModuleManager?.stopAll();
  for (const moduleId of externalModuleIds) registry.unregister(moduleId);
  externalModuleIds.clear();
  externalModuleErrors = [];

  const manager = new ExternalModuleManager(
    modulesRoot(),
    (moduleId) => {
      const definitions = effectiveConfigDefinitions().filter((definition) => definition.moduleId === moduleId);
      return configStore.toEnvironment(definitions);
    },
    moduleEnabled,
  );
  const storageService = new StorageHostService();
  storageService.addProfile(
    "default",
    new LocalStorageProvider(join(app.getPath("userData"), "storage", "default")),
    true,
  );
  storageService.register(manager);
  registerKvServices(manager);

  const external = await manager.load();
  externalModuleErrors = [...external.errors];

  for (const definition of external.definitions) {
    try {
      registry.register(definition);
      externalModuleIds.add(definition.id);
    } catch (error) {
      externalModuleErrors.push({
        folder: definition.id,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  externalModuleManager = manager;
  if (engineManager) {
    manager.setHostSendText((remoteJid, text) => engineManager.sendText(remoteJid, text));
    manager.setHostSendMedia((remoteJid, media) => engineManager.sendMedia(remoteJid, media));
    registerMediaServices(manager, engineManager);
    manager.startJobs();
  }
  if (externalModuleErrors.length) {
    for (const failure of externalModuleErrors) console.warn(`[module:${failure.folder}] ${failure.error}`);
  }

  return {
    loaded: [...externalModuleIds],
    errors: externalModuleErrors,
  };
}

function broadcastEngineStatus(): void {
  const window = mainWindow;
  if (window && !window.isDestroyed()) {
    window.webContents.send("bailey:engine-status", engineManager.status());
  }
}

function effectiveModules() {
  const commands = commandStore.list(registry.list());
  return registry.list().map((module) => ({
    id: module.id,
    name: module.name,
    version: module.version,
    description: module.description,
    commands: commands.filter((entry) => entry.module.id === module.id).map(({ command }) => ({
      id: command.id,
      name: command.name,
      section: command.section,
      aliases: command.aliases,
      description: command.description,
      editable: command.editable,
      origin: command.origin,
      replyText: command.replyText,
      reactionEmoji: command.reactionEmoji,
    })),
  }));
}

function assertUniqueCommand(patch: VisualCommandPatch, ignore?: { moduleId: string; id: string }): void {
  const requested = [patch.name, ...(patch.aliases ?? [])].map((value) => String(value).trim().toLowerCase()).filter(Boolean);
  const duplicate = commandStore.list(registry.list()).find(({ module, command }) => {
    if (ignore && module.id === ignore.moduleId && command.id === ignore.id) return false;
    const names = [command.name, ...command.aliases].map((value) => value.toLowerCase());
    return requested.some((value) => names.includes(value));
  });
  if (duplicate) throw new Error(`That trigger or alias is already used by ${commandPrefix()}${duplicate.command.name}.`);
}

function editorLanguage(path: string): string {
  const extension = extname(path).toLowerCase();
  return ({
    ".ts": "TypeScript", ".tsx": "TypeScript", ".js": "JavaScript", ".mjs": "JavaScript", ".cjs": "JavaScript",
    ".py": "Python", ".json": "JSON", ".yaml": "YAML", ".yml": "YAML", ".toml": "TOML", ".md": "Markdown", ".txt": "Text",
  } as Record<string, string>)[extension] ?? "Text";
}

async function openEditorFile() {
  const options: OpenDialogOptions = {
    title: "Open code or module file",
    properties: ["openFile"],
    filters: [
      { name: "Bailey editable files", extensions: [...EDITABLE_EXTENSIONS].map((value) => value.slice(1)) },
      { name: "All files", extensions: ["*"] },
    ],
  };
  const result = mainWindow
    ? await dialog.showOpenDialog(mainWindow, options)
    : await dialog.showOpenDialog(options);
  if (result.canceled || !result.filePaths[0]) return null;
  const path = result.filePaths[0];
  const extension = extname(path).toLowerCase();
  if (!EDITABLE_EXTENSIONS.has(extension)) throw new Error("Bailey Studio cannot safely edit that file type yet.");
  const raw = await readFile(path);
  if (raw.byteLength > MAX_EDITOR_BYTES) throw new Error("That file is larger than Bailey Studio's 2 MB editor limit.");
  openedEditorFiles.add(path);
  return { path, name: basename(path), language: editorLanguage(path), content: raw.toString("utf8") };
}

async function saveEditorFile(path: string, content: string) {
  if (!openedEditorFiles.has(path)) throw new Error("Reopen this file through Bailey Studio before saving it.");
  if (!EDITABLE_EXTENSIONS.has(extname(path).toLowerCase())) throw new Error("Bailey Studio cannot save that file type.");
  if (Buffer.byteLength(content, "utf8") > MAX_EDITOR_BYTES) throw new Error("The edited file is larger than Bailey Studio's 2 MB editor limit.");
  await writeFile(path, content, "utf8");
  return { ok: true };
}

async function openModulesFolder() {
  const path = modulesRoot();
  await mkdir(path, { recursive: true });
  const error = await shell.openPath(path);
  if (error) throw new Error(error);
  return { ok: true, path };
}

function registerIpc(): void {
  ipcMain.handle("bailey:get-state", () => ({
    runtime: engineManager.status().runtime,
    whatsapp: engineManager.status().whatsapp,
    moduleCount: registry.list().length,
    commandCount: commandStore.list(registry.list()).length,
    moduleLoadErrors: externalModuleErrors.length,
    version: app.getVersion(),
  }));

  ipcMain.handle("bailey:get-modules", () => ({
    prefix: commandPrefix(),
    modules: effectiveModules(),
  }));

  ipcMain.handle("bailey:get-command", (_event, moduleId: string, id: string) => {
    const command = commandStore.getById(registry.list(), moduleId, id);
    if (!command) throw new Error("Command not found.");
    return command;
  });

  ipcMain.handle("bailey:create-command", async (_event, patch: VisualCommandPatch) => {
    assertUniqueCommand(patch);
    return commandStore.create(registry.list(), "my-commands", patch);
  });

  ipcMain.handle("bailey:update-command", async (_event, moduleId: string, id: string, patch: VisualCommandPatch) => {
    assertUniqueCommand(patch, { moduleId, id });
    return commandStore.updateById(registry.list(), moduleId, id, patch);
  });

  ipcMain.handle("bailey:reset-command", (_event, moduleId: string, id: string) => commandStore.resetById(registry.list(), moduleId, id));
  ipcMain.handle("bailey:delete-command", async (_event, moduleId: string, id: string) => {
    await commandStore.deleteById(moduleId, id);
    return { ok: true };
  });

  ipcMain.handle("bailey:get-config", () => {
    const definitions = effectiveConfigDefinitions();
    return {
      definitions,
      values: configStore.listForUi(definitions),
    };
  });

  ipcMain.handle("bailey:set-config", async (_event, key: string, value: unknown) => {
    const definition = findEffectiveConfigDefinition(key);
    if (!definition) throw new Error(`Unknown Bailey setting: ${key}`);
    if (definition.type === "secret" && value === "") return { ok: true, unchanged: true };

    await configStore.set(definition, value);

    if (key === "modules.core.settings.autoStart") {
      app.setLoginItemSettings({ openAtLogin: Boolean(configStore.get(definition)) });
    }

    return { ok: true };
  });

  ipcMain.handle("bailey:studio-open-file", () => openEditorFile());
  ipcMain.handle("bailey:studio-save-file", (_event, path: string, content: string) => saveEditorFile(path, content));
  ipcMain.handle("bailey:studio-open-modules-folder", () => openModulesFolder());
  ipcMain.handle("bailey:studio-reload-modules", () => reloadExternalModules());
  ipcMain.handle("bailey:module-runtime-status", () => externalModuleManager?.statuses() ?? []);
  ipcMain.handle("bailey:module-restart", (_event, moduleId: string) => {
    if (!externalModuleManager) throw new Error("External module manager is not ready.");
    return externalModuleManager.restartModule(String(moduleId ?? ""));
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
  void externalModuleManager?.stopAll();
});

app.whenReady().then(async () => {
  app.setAppUserModelId("dev.bailey.host");

  configStore = new JsonConfigStore(
    join(app.getPath("userData"), "config.json"),
    createSecretCodec(),
  );
  commandStore = new JsonCommandStore(join(app.getPath("userData"), "commands.json"));
  chatStore = new JsonChatStore(join(app.getPath("userData"), "chats.json"));
  await Promise.all([configStore.load(), commandStore.load(), chatStore.load()]);
  await reloadExternalModules();

  engineManager = new EngineManager(
    join(app.getPath("userData"), "engines"),
    workerPath(),
    process.execPath,
  );
  await engineManager.initialize();
  externalModuleManager?.setHostSendText((remoteJid, text) => engineManager.sendText(remoteJid, text));
  externalModuleManager?.setHostSendMedia((remoteJid, media) => engineManager.sendMedia(remoteJid, media));
  if (externalModuleManager) registerMediaServices(externalModuleManager, engineManager);
  externalModuleManager?.startJobs();
  chatController = new ChatController(chatStore, engineManager, () => mainWindow);
  chatController.registerIpc();
  engineManager.on("status", broadcastEngineStatus);
  engineManager.on("message", (message: IncomingEngineMessage) => {
    void chatController.ingest(message).catch((error) => console.error("Chat persistence failed", error));
    void dispatchModuleMessageEvents(message).catch((error) => console.error("Module event dispatch failed", error));
    void dispatchIncomingMessage(message).catch((error) => console.error("Command dispatch failed", error));
  });
  engineManager.on("host-event", (event: EngineHostEvent) => {
    void externalModuleManager?.dispatchEventToModules(event.event, event.context)
      .catch((error) => console.error("External module host event dispatch failed", error));
  });

  const autoStart = registry.findConfigDefinition("modules.core.settings.autoStart");
  if (autoStart) app.setLoginItemSettings({ openAtLogin: Boolean(configStore.get(autoStart)) });

  registerIpc();
  createApplicationMenu();
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
