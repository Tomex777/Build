import { createRequire } from "node:module";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import type { EngineWorkerCommand, EngineWorkerEvent } from "./contracts";

function arg(name: string): string {
  const index = process.argv.indexOf(name);
  const value = index >= 0 ? process.argv[index + 1] : undefined;
  if (!value) throw new Error(`Missing ${name}`);
  return value;
}

const engineDir = arg("--engine-dir");
const sessionDir = arg("--session-dir");
const engineVersion = arg("--engine-version");

function emit(event: EngineWorkerEvent): void {
  if (process.send) process.send(event);
}

const silentLogger: any = {
  level: "silent",
  trace() {},
  debug() {},
  info() {},
  warn() {},
  error() {},
  fatal() {},
  child() { return silentLogger; },
};

let socket: any;
let shuttingDown = false;
let pendingPairPhone: string | undefined;
let pairingRequested = false;

function messageText(message: any): string | undefined {
  const content = message?.message;
  return content?.conversation
    ?? content?.extendedTextMessage?.text
    ?? content?.imageMessage?.caption
    ?? content?.videoMessage?.caption
    ?? content?.documentMessage?.caption
    ?? undefined;
}

function messageTimestamp(value: any): number {
  if (typeof value === "number") return value > 10_000_000_000 ? value : value * 1000;
  if (typeof value === "bigint") return Number(value) * 1000;
  if (value && typeof value.toNumber === "function") return Number(value.toNumber()) * 1000;
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric > 0 ? (numeric > 10_000_000_000 ? numeric : numeric * 1000) : Date.now();
}

async function connect(): Promise<void> {
  try {
    const modulePath = pathToFileURL(join(engineDir, "node_modules", "@itsliaaa", "baileys", "lib", "index.js")).href;
    const baileys: any = await import(modulePath);
    const { state, saveCreds } = await baileys.useMultiFileAuthState(sessionDir);

    socket = baileys.makeWASocket({
      auth: state,
      logger: silentLogger,
      printQRInTerminal: false,
      markOnlineOnConnect: false,
      syncFullHistory: false,
      generateHighQualityLinkPreview: false,
      browser: ["Bailey Host", "Desktop", engineVersion],
    });

    socket.ev.on("creds.update", saveCreds);
    socket.ev.on("connection.update", async (update: any) => {
      const { connection } = update;
      if (connection === "connecting") {
        emit({ type: "connection", state: "connecting" });
        if (pendingPairPhone && !socket.authState.creds.registered && !pairingRequested) {
          pairingRequested = true;
          try {
            await baileys.delay(1200);
            const code = await socket.requestPairingCode(pendingPairPhone);
            emit({ type: "pairing-code", code });
          } catch (error) {
            pairingRequested = false;
            emit({ type: "error", message: error instanceof Error ? error.message : String(error) });
          }
        }
      } else if (connection === "open") {
        pairingRequested = false;
        emit({ type: "connection", state: "connected" });
      } else if (connection === "close") {
        const statusCode = update?.lastDisconnect?.error?.output?.statusCode
          ?? update?.lastDisconnect?.error?.statusCode;
        const loggedOut = statusCode === baileys.DisconnectReason?.loggedOut;
        emit({ type: "connection", state: "disconnected", detail: statusCode ? String(statusCode) : undefined });
        if (!shuttingDown && !loggedOut) {
          pairingRequested = false;
          setTimeout(() => void connect(), 1800);
        }
      }
    });

    socket.ev.on("messages.upsert", ({ messages }: any) => {
      for (const message of messages ?? []) {
        if (!message?.message || !message?.key?.remoteJid) continue;
        emit({
          type: "message",
          message: {
            id: message.key.id ?? undefined,
            remoteJid: message.key.remoteJid,
            fromMe: Boolean(message.key.fromMe),
            participant: message.key.participant ?? undefined,
            text: messageText(message),
            key: message.key,
            pushName: message.pushName ?? undefined,
            timestamp: messageTimestamp(message.messageTimestamp),
          },
        });
      }
    });

    emit({ type: "ready", version: engineVersion });
  } catch (error) {
    emit({ type: "error", message: error instanceof Error ? error.stack ?? error.message : String(error) });
    process.exitCode = 1;
  }
}

process.on("message", async (raw: EngineWorkerCommand) => {
  try {
    if (!raw || typeof raw !== "object") return;
    if (raw.type === "pair") {
      pendingPairPhone = raw.phoneNumber.replace(/\D/g, "");
      pairingRequested = false;
      if (socket && !socket.authState?.creds?.registered) {
        const localRequire = createRequire(join(engineDir, "package.json"));
        try {
          const code = await socket.requestPairingCode(pendingPairPhone);
          emit({ type: "pairing-code", code });
          pairingRequested = true;
        } catch {
          pairingRequested = false;
        }
        void localRequire;
      }
    } else if (raw.type === "send-text") {
      if (!socket) throw new Error("WhatsApp socket is not ready.");
      await socket.sendMessage(raw.remoteJid, { text: raw.text });
    } else if (raw.type === "react") {
      if (!socket) throw new Error("WhatsApp socket is not ready.");
      await socket.sendMessage(raw.remoteJid, { react: { text: raw.emoji, key: raw.key } });
    } else if (raw.type === "shutdown") {
      shuttingDown = true;
      try { await socket?.end?.(undefined); } catch {}
      process.exit(0);
    }
  } catch (error) {
    emit({ type: "error", message: error instanceof Error ? error.message : String(error) });
  }
});

process.on("SIGTERM", () => {
  shuttingDown = true;
  process.exit(0);
});

void connect();
