import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import type { IncomingEngineMessage } from "../engine/contracts";

export interface StoredChatMessage {
  id: string;
  remoteJid: string;
  participant?: string;
  pushName?: string;
  text: string;
  fromMe: boolean;
  timestamp: number;
}

export interface ChatSummary {
  remoteJid: string;
  title: string;
  lastMessage: string;
  lastTimestamp: number;
  lastFromMe: boolean;
  messageCount: number;
}

interface ChatDocument {
  version: 1;
  messages: Record<string, StoredChatMessage[]>;
}

const MAX_MESSAGES_PER_CHAT = 2_000;

function fallbackTitle(jid: string): string {
  return jid.replace(/@.+$/, "").replace(/-/g, " ");
}

export class JsonChatStore {
  private document: ChatDocument = { version: 1, messages: {} };

  constructor(private readonly filePath: string) {}

  async load(): Promise<void> {
    try {
      const raw = await readFile(this.filePath, "utf8");
      const parsed = JSON.parse(raw) as ChatDocument;
      if (parsed.version !== 1 || !parsed.messages || typeof parsed.messages !== "object") {
        throw new Error("Unsupported Bailey chat history format");
      }
      this.document = parsed;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
    }
  }

  async ingest(message: IncomingEngineMessage): Promise<StoredChatMessage | undefined> {
    const text = message.text?.trim();
    if (!text) return undefined;

    const id = message.id || `${message.remoteJid}:${message.timestamp ?? Date.now()}:${message.fromMe ? "1" : "0"}:${text}`;
    const list = this.document.messages[message.remoteJid] ?? [];
    if (list.some((entry) => entry.id === id)) return undefined;

    const stored: StoredChatMessage = {
      id,
      remoteJid: message.remoteJid,
      participant: message.participant,
      pushName: message.pushName,
      text,
      fromMe: message.fromMe,
      timestamp: message.timestamp ?? Date.now(),
    };

    list.push(stored);
    list.sort((a, b) => a.timestamp - b.timestamp);
    if (list.length > MAX_MESSAGES_PER_CHAT) list.splice(0, list.length - MAX_MESSAGES_PER_CHAT);
    this.document.messages[message.remoteJid] = list;
    await this.flush();
    return stored;
  }

  listChats(query = ""): ChatSummary[] {
    const needle = query.trim().toLowerCase();
    const chats = Object.entries(this.document.messages).flatMap(([remoteJid, messages]) => {
      const last = messages[messages.length - 1];
      if (!last) return [];
      const named = [...messages].reverse().find((entry) => entry.pushName)?.pushName;
      const title = named || fallbackTitle(remoteJid);
      if (needle && !title.toLowerCase().includes(needle) && !remoteJid.toLowerCase().includes(needle) && !messages.some((entry) => entry.text.toLowerCase().includes(needle))) {
        return [];
      }
      return [{
        remoteJid,
        title,
        lastMessage: last.text,
        lastTimestamp: last.timestamp,
        lastFromMe: last.fromMe,
        messageCount: messages.length,
      } satisfies ChatSummary];
    });
    return chats.sort((a, b) => b.lastTimestamp - a.lastTimestamp);
  }

  messages(remoteJid: string, limit = 200): StoredChatMessage[] {
    const safeLimit = Math.min(Math.max(Math.floor(limit), 1), 500);
    return (this.document.messages[remoteJid] ?? []).slice(-safeLimit);
  }

  private async flush(): Promise<void> {
    await mkdir(dirname(this.filePath), { recursive: true });
    await writeFile(this.filePath, `${JSON.stringify(this.document, null, 2)}\n`, "utf8");
  }
}
