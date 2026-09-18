import { contextBridge, ipcRenderer } from "electron";

contextBridge.exposeInMainWorld("bailey", {
  getState: () => ipcRenderer.invoke("bailey:get-state"),
  getModules: () => ipcRenderer.invoke("bailey:get-modules"),
  getCommand: (moduleId: string, commandId: string) => ipcRenderer.invoke("bailey:get-command", moduleId, commandId),
  createCommand: (patch: unknown) => ipcRenderer.invoke("bailey:create-command", patch),
  updateCommand: (moduleId: string, commandId: string, patch: unknown) => ipcRenderer.invoke("bailey:update-command", moduleId, commandId, patch),
  resetCommand: (moduleId: string, commandId: string) => ipcRenderer.invoke("bailey:reset-command", moduleId, commandId),
  deleteCommand: (moduleId: string, commandId: string) => ipcRenderer.invoke("bailey:delete-command", moduleId, commandId),
  getConfig: () => ipcRenderer.invoke("bailey:get-config"),
  setConfig: (key: string, value: unknown) => ipcRenderer.invoke("bailey:set-config", key, value),
  openStudioFile: () => ipcRenderer.invoke("bailey:studio-open-file"),
  saveStudioFile: (path: string, content: string) => ipcRenderer.invoke("bailey:studio-save-file", path, content),
  openModulesFolder: () => ipcRenderer.invoke("bailey:studio-open-modules-folder"),
  createModule: (input: unknown) => ipcRenderer.invoke("bailey:studio-create-module", input),
  showModule: (moduleId: string) => ipcRenderer.invoke("bailey:studio-show-module", moduleId),
  reloadModules: () => ipcRenderer.invoke("bailey:studio-reload-modules"),
  detectModuleRuntimes: () => ipcRenderer.invoke("bailey:studio-detect-runtimes"),
  exportModulePackage: (moduleId: string) => ipcRenderer.invoke("bailey:studio-export-module", moduleId),
  installModulePackage: () => ipcRenderer.invoke("bailey:studio-install-module"),
  getModuleRuntimeStatus: () => ipcRenderer.invoke("bailey:module-runtime-status"),
  restartModule: (moduleId: string) => ipcRenderer.invoke("bailey:module-restart", moduleId),
  exportBackup: () => ipcRenderer.invoke("bailey:backup-export"),
  importBackup: () => ipcRenderer.invoke("bailey:backup-import"),
  getStorageProfiles: () => ipcRenderer.invoke("bailey:storage-profiles"),
  saveStorageProfile: (input: unknown) => ipcRenderer.invoke("bailey:storage-profile-save", input),
  setDefaultStorageProfile: (name: string) => ipcRenderer.invoke("bailey:storage-profile-default", name),
  deleteStorageProfile: (name: string) => ipcRenderer.invoke("bailey:storage-profile-delete", name),
  listChats: (query = "") => ipcRenderer.invoke("bailey:chats-list", query),
  getChatMessages: (remoteJid: string, limit = 200) => ipcRenderer.invoke("bailey:chat-messages", remoteJid, limit),
  sendChatMessage: (remoteJid: string, text: string) => ipcRenderer.invoke("bailey:chat-send", remoteJid, text),
  onChatMessage: (listener: (message: unknown) => void) => {
    const handler = (_event: Electron.IpcRendererEvent, message: unknown) => listener(message);
    ipcRenderer.on("bailey:chat-message", handler);
    return () => ipcRenderer.removeListener("bailey:chat-message", handler);
  },
  getEngineStatus: () => ipcRenderer.invoke("bailey:engine-status"),
  checkEngineLatest: () => ipcRenderer.invoke("bailey:engine-check-latest"),
  installDefaultEngine: () => ipcRenderer.invoke("bailey:engine-install-default"),
  installEngineVersion: (version: string) => ipcRenderer.invoke("bailey:engine-install-version", version),
  updateEngine: () => ipcRenderer.invoke("bailey:engine-update"),
  rollbackEngine: () => ipcRenderer.invoke("bailey:engine-rollback"),
  startEngine: () => ipcRenderer.invoke("bailey:engine-start"),
  stopEngine: () => ipcRenderer.invoke("bailey:engine-stop"),
  pairEngine: (phoneNumber: string) => ipcRenderer.invoke("bailey:engine-pair", phoneNumber),
  onEngineStatus: (listener: (status: unknown) => void) => {
    const handler = (_event: Electron.IpcRendererEvent, status: unknown) => listener(status);
    ipcRenderer.on("bailey:engine-status", handler);
    return () => ipcRenderer.removeListener("bailey:engine-status", handler);
  },
});
