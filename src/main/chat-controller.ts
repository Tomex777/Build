import { ipcMain, type BrowserWindow } from "electron";
import type { JsonChatStore } from "../core/chat-store";
import type { IncomingEngineMessage } from "../engine/contracts";
import type { EngineManager } from "../engine/engine-manager";

export class ChatController {
  constructor(
    private readonly store: JsonChatStore,
    private readonly engine: EngineManager,
    private readonly getWindow: () => BrowserWindow | null,
  ) {}

  registerIpc(): void {
    ipcMain.handle("bailey:chats-list", (_event, query?: string) => this.store.listChats(query ?? ""));
    ipcMain.handle("bailey:chat-messages", (_event, remoteJid: string, limit?: number) => {
      if (!remoteJid?.trim()) throw new Error("Chat id is required.");
      return this.store.messages(remoteJid, limit ?? 200);
    });
    ipcMain.handle("bailey:chat-send", async (_event, remoteJid: string, text: string) => {
      const jid = remoteJid?.trim();
      const message = text?.trim();
      if (!jid) throw new Error("Choose a chat first.");
      if (!message) throw new Error("Message cannot be empty.");
      if (message.length > 65_536) throw new Error("Message is too long.");
      if (this.engine.status().whatsapp !== "connected") throw new Error("WhatsApp is not connected.");
      await this.engine.sendText(jid, message);
      return { ok: true };
    });
  }

  async ingest(message: IncomingEngineMessage): Promise<void> {
    const stored = await this.store.ingest(message);
    if (!stored) return;
    const window = this.getWindow();
    if (window && !window.isDestroyed()) {
      window.webContents.send("bailey:chat-message", stored);
    }
  }
}
