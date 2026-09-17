import { contextBridge, ipcRenderer } from "electron";

contextBridge.exposeInMainWorld("bailey", {
  getState: () => ipcRenderer.invoke("bailey:get-state"),
  getModules: () => ipcRenderer.invoke("bailey:get-modules"),
  getCommand: (moduleId: string, commandId: string) => ipcRenderer.invoke("bailey:get-command", moduleId, commandId),
  updateCommand: (moduleId: string, commandId: string, patch: unknown) => ipcRenderer.invoke("bailey:update-command", moduleId, commandId, patch),
  resetCommand: (moduleId: string, commandId: string) => ipcRenderer.invoke("bailey:reset-command", moduleId, commandId),
  getConfig: () => ipcRenderer.invoke("bailey:get-config"),
  setConfig: (key: string, value: unknown) => ipcRenderer.invoke("bailey:set-config", key, value),
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
