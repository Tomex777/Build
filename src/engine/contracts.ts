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

export interface IncomingEngineMessage {
  id?: string;
  remoteJid: string;
  fromMe: boolean;
  participant?: string;
  text?: string;
  key: unknown;
  pushName?: string;
  timestamp?: number;
}

export type EngineWorkerEvent =
  | { type: "ready"; version: string }
  | { type: "connection"; state: WhatsAppConnectionState; detail?: string }
  | { type: "pairing-code"; code: string }
  | { type: "message"; message: IncomingEngineMessage }
  | { type: "error"; message: string };

export type EngineWorkerCommand =
  | { type: "pair"; phoneNumber: string }
  | { type: "send-text"; remoteJid: string; text: string }
  | { type: "react"; remoteJid: string; key: unknown; emoji: string }
  | { type: "shutdown" };
