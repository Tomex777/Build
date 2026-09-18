import { mkdir } from "node:fs/promises";
import { basename, join } from "node:path";
import type { EngineManager } from "../engine/engine-manager";
import type { ExternalModuleManager } from "../external/external-module-manager";

function params(raw: unknown): Record<string, unknown> {
  return raw && typeof raw === "object" && !Array.isArray(raw) ? raw as Record<string, unknown> : {};
}

function safeFileName(value: unknown): string {
  const candidate = basename(String(value ?? "media.bin")).replace(/[<>:"/\\|?*\x00-\x1F]/g, "_").trim();
  return candidate || "media.bin";
}

export function registerMediaServices(manager: ExternalModuleManager, engine: EngineManager): void {
  manager.registerService("media", "download", async (raw, context) => {
    if (!context.dataDirectory) throw new Error("media.download requires the module storage capability.");
    const input = params(raw);
    const messageId = String(input.messageId ?? "").trim();
    if (!messageId) throw new Error("media.download requires messageId.");
    const directory = join(context.dataDirectory, "media", "incoming");
    await mkdir(directory, { recursive: true });
    const destinationPath = join(directory, `${Date.now()}-${safeFileName(input.fileName)}`);
    const result = await engine.downloadMedia(messageId, destinationPath);
    return { messageId, path: result.path, size: result.size };
  }, "media.download");
}
