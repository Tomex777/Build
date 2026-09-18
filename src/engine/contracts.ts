export type EngineRuntimeState = "stopped" | "installing" | "starting" | "running" | "error";
export type WhatsAppConnectionState = "not-connected" | "connecting" | "paired" | "connected" | "disconnected";

export interface EngineManifest {
  provider: "lia";
  packageName: "@itsliaaa/baileys";
  apiVersion: 1;
  activeVersion?: string;
  previousVersion?: string;
  installedVersions: string[];
  autoUpdate: boolean;
  channel: "stable";
}

export interface EngineStatus {
  provider: "lia";
  packageName: "@itsliaaa/baileys";
  apiVersion: 1;
  activeVersion?: string;
  previousVersion?: string;
  installedVersions: string[];
  latestVersion?: string;
  updateAvailable: boolean;
  runtime: EngineRuntimeState;
  whatsapp: WhatsAppConnectionState;
  pairingCode?: string;
  lastError?: string;
}

export interface IncomingEngineMedia {
  kind: "image" | "video" | "audio" | "document" | "sticker";
  mimetype?: string;
  fileName?: string;
  caption?: string;
  seconds?: number;
  viewOnce?: boolean;
}

export interface IncomingEngineMessage {
  id?: string;
  remoteJid: string;
  fromMe: boolean;
  participant?: string;
  text?: string;
  key: unknown;
  pushName?: string;
  timestamp?: number;
  media?: IncomingEngineMedia;
}

export interface EngineSendMedia {
  kind: "image" | "video" | "audio" | "document" | "sticker";
  source: { path?: string; url?: string };
  mimetype?: string;
  fileName?: string;
  caption?: string;
  ptt?: boolean;
}

export interface EngineHostEvent {
  event: "message.updated" | "reaction.received" | "group.participant" | "call.received";
  context: Record<string, unknown>;
}

export type EngineWorkerEvent =
  | { type: "ready"; version: string }
  | { type: "connection"; state: WhatsAppConnectionState; detail?: string }
  | { type: "pairing-code"; code: string }
  | { type: "message"; message: IncomingEngineMessage }
  | { type: "host-event"; event: EngineHostEvent["event"]; context: Record<string, unknown> }
  | { type: "media-downloaded"; requestId: string; ok: boolean; path?: string; size?: number; error?: string }
  | { type: "error"; message: string };

export type EngineWorkerCommand =
  | { type: "pair"; phoneNumber: string }
  | { type: "send-text"; remoteJid: string; text: string }
  | { type: "send-media"; remoteJid: string; media: EngineSendMedia }
  | { type: "download-media"; requestId: string; messageId: string; destinationPath: string }
  | { type: "react"; remoteJid: string; key: unknown; emoji: string }
  | { type: "shutdown" };
