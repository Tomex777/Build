import { createRequire } from "node:module";
import { writeFile } from "node:fs/promises";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import type { EngineSendMedia, EngineWorkerCommand, EngineWorkerEvent, IncomingEngineMedia } from "./contracts";

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
let baileysApi: any;
let shuttingDown = false;
let pendingPairPhone: string | undefined;
let pairingRequested = false;
const recentMessages = new Map<string, any>();

function unwrapMessageContent(content: any): { content: any; viewOnce: boolean } {
  let current = content;
  let viewOnce = false;
  for (let i = 0; i < 6 && current; i += 1) {
    if (current.ephemeralMessage?.message) {
      current = current.ephemeralMessage.message;
      continue;
    }
    if (current.viewOnceMessage?.message) {
      viewOnce = true;
      current = current.viewOnceMessage.message;
      continue;
    }
    if (current.viewOnceMessageV2?.message) {
      viewOnce = true;
      current = current.viewOnceMessageV2.message;
      continue;
    }
    if (current.viewOnceMessageV2Extension?.message) {
      viewOnce = true;
      current = current.viewOnceMessageV2Extension.message;
      continue;
    }
    if (current.documentWithCaptionMessage?.message) {
      current = current.documentWithCaptionMessage.message;
      continue;
    }
    break;
  }
  return { content: current, viewOnce };
}

function messageText(message: any): string | undefined {
  const { content } = unwrapMessageContent(message?.message);
  return content?.conversation
    ?? content?.extendedTextMessage?.text
    ?? content?.imageMessage?.caption
    ?? content?.videoMessage?.caption
    ?? content?.documentMessage?.caption
    ?? undefined;
}

function mediaInfo(message: any): IncomingEngineMedia | undefined {
  const { content, viewOnce } = unwrapMessageContent(message?.message);
  if (!content) return undefined;
  const candidates: Array<[IncomingEngineMedia["kind"], any]> = [
    ["image", content.imageMessage],
    ["video", content.videoMessage],
    ["audio", content.audioMessage],
    ["document", content.documentMessage],
    ["sticker", content.stickerMessage],
  ];
  for (const [kind, media] of candidates) {
    if (!media) continue;
    const numericSeconds = Number(media.seconds);
    return {
      kind,
      mimetype: media.mimetype ?? undefined,
      fileName: media.fileName ?? undefined,
      caption: media.caption ?? undefined,
      seconds: Number.isFinite(numericSeconds) ? numericSeconds : undefined,
      viewOnce,
    };
  }
  return undefined;
}

function messageTimestamp(value: any): number {
  if (typeof value === "number") return value > 10_000_000_000 ? value : value * 1000;
  if (typeof value === "bigint") return Number(value) * 1000;
  if (value && typeof value.toNumber === "function") return Number(value.toNumber()) * 1000;
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric > 0 ? (numeric > 10_000_000_000 ? numeric : numeric * 1000) : Date.now();
}

function rememberMessage(message: any): void {
  const id = message?.key?.id;
  if (!id) return;
  recentMessages.set(String(id), message);
  while (recentMessages.size > 250) {
    const first = recentMessages.keys().next().value as string | undefined;
    if (!first) break;
    recentMessages.delete(first);
  }
}

function mediaSource(media: EngineSendMedia): { url: string } {
  const value = media.source.path ?? media.source.url;
  if (!value) throw new Error("Media source requires path or url.");
  return { url: value };
}

async function sendMedia(remoteJid: string, media: EngineSendMedia): Promise<void> {
  if (!socket) throw new Error("WhatsApp socket is not ready.");
  const source = mediaSource(media);
  if (media.kind === "image") {
    await socket.sendMessage(remoteJid, { image: source, caption: media.caption, mimetype: media.mimetype });
  } else if (media.kind === "video") {
    await socket.sendMessage(remoteJid, { video: source, caption: media.caption, mimetype: media.mimetype });
  } else if (media.kind === "audio") {
    await socket.sendMessage(remoteJid, { audio: source, mimetype: media.mimetype ?? "audio/mpeg", ptt: Boolean(media.ptt) });
  } else if (media.kind === "document") {
    await socket.sendMessage(remoteJid, {
      document: source,
      mimetype: media.mimetype ?? "application/octet-stream",
      fileName: media.fileName ?? "file",
      caption: media.caption,
    });
  } else if (media.kind === "sticker") {
    await socket.sendMessage(remoteJid, { sticker: source });
  }
}

async function downloadMedia(requestId: string, messageId: string, destinationPath: string): Promise<void> {
  try {
    const message = recentMessages.get(messageId);
    if (!message) throw new Error("Message is no longer in Bailey's recent-media cache.");
    if (!baileysApi?.downloadMediaMessage) throw new Error("Installed Lia engine does not expose downloadMediaMessage.");
    const buffer = await baileysApi.downloadMediaMessage(
      message,
      "buffer",
      {},
      { logger: silentLogger, reuploadRequest: socket?.updateMediaMessage },
    );
    if (!Buffer.isBuffer(buffer)) throw new Error("Media download did not return a buffer.");
    await writeFile(destinationPath, buffer);
    emit({ type: "media-downloaded", requestId, ok: true, path: destinationPath, size: buffer.length });
  } catch (error) {
    emit({ type: "media-downloaded", requestId, ok: false, error: error instanceof Error ? error.message : String(error) });
  }
}

async function connect(): Promise<void> {
  try {
    const modulePath = pathToFileURL(join(engineDir, "node_modules", "@itsliaaa", "baileys", "lib", "index.js")).href;
    const baileys: any = await import(modulePath);
    baileysApi = baileys;
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
        rememberMessage(message);
        const { content } = unwrapMessageContent(message.message);
        if (content?.reactionMessage) {
          emit({
            type: "host-event",
            event: "reaction.received",
            context: {
              remoteJid: message.key.remoteJid,
              participant: message.key.participant ?? undefined,
              emoji: content.reactionMessage.text ?? "",
              targetKey: content.reactionMessage.key ?? undefined,
              messageId: message.key.id ?? undefined,
            },
          });
          continue;
        }

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
            media: mediaInfo(message),
          },
        });
      }
    });

    socket.ev.on("messages.update", (updates: any[]) => {
      emit({
        type: "host-event",
        event: "message.updated",
        context: {
          updates: (updates ?? []).map((item) => ({
            id: item?.key?.id ?? undefined,
            remoteJid: item?.key?.remoteJid ?? undefined,
            participant: item?.key?.participant ?? undefined,
          })),
        },
      });
    });

    socket.ev.on("group-participants.update", (update: any) => {
      emit({
        type: "host-event",
        event: "group.participant",
        context: {
          id: update?.id ?? undefined,
          participants: Array.isArray(update?.participants) ? update.participants : [],
          action: update?.action ?? undefined,
          author: update?.author ?? undefined,
        },
      });
    });

    socket.ev.on("call", (calls: any[]) => {
      for (const call of calls ?? []) {
        emit({
          type: "host-event",
          event: "call.received",
          context: {
            id: call?.id ?? undefined,
            from: call?.from ?? undefined,
            status: call?.status ?? undefined,
            isVideo: Boolean(call?.isVideo),
            isGroup: Boolean(call?.isGroup),
            date: call?.date ? String(call.date) : undefined,
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
    } else if (raw.type === "send-media") {
      await sendMedia(raw.remoteJid, raw.media);
    } else if (raw.type === "download-media") {
      await downloadMedia(raw.requestId, raw.messageId, raw.destinationPath);
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
