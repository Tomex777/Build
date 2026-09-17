import { contextBridge, ipcRenderer } from "electron";

contextBridge.exposeInMainWorld("bailey", {
  getState: () => ipcRenderer.invoke("bailey:get-state"),
  getModules: () => ipcRenderer.invoke("bailey:get-modules"),
  getConfig: () => ipcRenderer.invoke("bailey:get-config"),
  setConfig: (key: string, value: unknown) => ipcRenderer.invoke("bailey:set-config", key, value),
});
