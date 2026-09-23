import { mkdir, readFile, readdir, rm, stat, writeFile } from "node:fs/promises";
import { dirname, relative, resolve, sep } from "node:path";
import type { ExternalModuleManager } from "../external/external-module-manager";

export interface StorageObjectInfo {
  key: string;
  size: number;
  updatedAt?: number;
}

export interface StorageProvider {
  readonly kind: string;
  put(key: string, data: Buffer, metadata?: Record<string, string>): Promise<StorageObjectInfo>;
  get(key: string): Promise<Buffer>;
  delete(key: string): Promise<void>;
  exists(key: string): Promise<boolean>;
  list(prefix?: string): Promise<StorageObjectInfo[]>;
  createTemporaryLink?(key: string, expiresSeconds: number): Promise<string>;
}

function safeKey(value: unknown): string {
  const key = String(value ?? "").replace(/\\/g, "/").replace(/^\/+/, "").trim();
  if (!key || key.includes("../") || key === ".." || key.includes("\0")) throw new Error("Storage key is invalid.");
  return key;
}

function objectParams(raw: unknown): Record<string, unknown> {
  return raw && typeof raw === "object" && !Array.isArray(raw) ? raw as Record<string, unknown> : {};
}

export class LocalStorageProvider implements StorageProvider {
  readonly kind = "local";
  constructor(private readonly root: string) {}

  private pathFor(keyValue: unknown): { key: string; path: string } {
    const key = safeKey(keyValue);
    const path = resolve(this.root, key);
    const root = resolve(this.root);
    if (path !== root && !path.startsWith(root + sep)) throw new Error("Storage key escapes the profile root.");
    return { key, path };
  }

  async put(keyValue: string, data: Buffer): Promise<StorageObjectInfo> {
    const { key, path } = this.pathFor(keyValue);
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, data);
    const info = await stat(path);
    return { key, size: info.size, updatedAt: info.mtimeMs };
  }

  async get(keyValue: string): Promise<Buffer> {
    return readFile(this.pathFor(keyValue).path);
  }

  async delete(keyValue: string): Promise<void> {
    await rm(this.pathFor(keyValue).path, { force: true });
  }

  async exists(keyValue: string): Promise<boolean> {
    try {
      return (await stat(this.pathFor(keyValue).path)).isFile();
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === "ENOENT") return false;
      throw error;
    }
  }

  async list(prefixValue = ""): Promise<StorageObjectInfo[]> {
    await mkdir(this.root, { recursive: true });
    const prefix = prefixValue ? safeKey(prefixValue) : "";
    const base = prefix ? this.pathFor(prefix).path : resolve(this.root);
    const root = resolve(this.root);
    const output: StorageObjectInfo[] = [];

    const walk = async (directory: string): Promise<void> => {
      let entries;
      try {
        entries = await readdir(directory, { withFileTypes: true });
      } catch (error) {
        if ((error as NodeJS.ErrnoException).code === "ENOENT") return;
        throw error;
      }
      for (const entry of entries) {
        const path = resolve(directory, entry.name);
        if (entry.isDirectory()) await walk(path);
        else if (entry.isFile()) {
          const info = await stat(path);
          output.push({ key: relative(root, path).split(sep).join("/"), size: info.size, updatedAt: info.mtimeMs });
        }
      }
    };
    await walk(base);
    return output.sort((a, b) => a.key.localeCompare(b.key));
  }
}

export class StorageHostService {
  private readonly profiles = new Map<string, StorageProvider>();
  private defaultProfile = "default";

  constructor(private readonly onPayloadBytes: (bytes: number) => void = () => {}) {}

  addProfile(name: string, provider: StorageProvider, makeDefault = false): void {
    const normalized = String(name ?? "").trim().toLowerCase();
    if (!/^[a-z0-9][a-z0-9._-]{0,63}$/.test(normalized)) throw new Error("Storage profile name is invalid.");
    this.profiles.set(normalized, provider);
    if (makeDefault) this.defaultProfile = normalized;
  }

  private provider(raw: unknown): { name: string; provider: StorageProvider } {
    const params = objectParams(raw);
    const name = String(params.profile ?? this.defaultProfile).trim().toLowerCase();
    const provider = this.profiles.get(name);
    if (!provider) throw new Error(`Unknown storage profile: ${name}`);
    return { name, provider };
  }

  register(manager: ExternalModuleManager): void {
    manager.registerService("storage", "info", async (raw) => {
      const { name, provider } = this.provider(raw);
      return { profile: name, provider: provider.kind, temporaryLinks: typeof provider.createTemporaryLink === "function" };
    }, "storage.read", "storage");

    manager.registerService("storage", "put", async (raw) => {
      const params = objectParams(raw);
      const { name, provider } = this.provider(params);
      const key = safeKey(params.key);
      const hasText = typeof params.text === "string";
      const hasBase64 = typeof params.base64 === "string";
      if (hasText === hasBase64) throw new Error("storage.put requires exactly one of text or base64.");
      const data = hasText ? Buffer.from(String(params.text), "utf8") : Buffer.from(String(params.base64), "base64");
      const result = await provider.put(key, data);
      this.onPayloadBytes(data.byteLength);
      return { profile: name, ...result };
    }, "storage.write", "storage");

    manager.registerService("storage", "get", async (raw) => {
      const params = objectParams(raw);
      const { name, provider } = this.provider(params);
      const key = safeKey(params.key);
      const data = await provider.get(key);
      this.onPayloadBytes(data.byteLength);
      if (params.encoding === "text") return { profile: name, key, encoding: "text", text: data.toString("utf8"), size: data.length };
      return { profile: name, key, encoding: "base64", base64: data.toString("base64"), size: data.length };
    }, "storage.read", "storage");

    manager.registerService("storage", "delete", async (raw) => {
      const params = objectParams(raw);
      const { name, provider } = this.provider(params);
      const key = safeKey(params.key);
      await provider.delete(key);
      return { profile: name, key, deleted: true };
    }, "storage.delete", "storage");

    manager.registerService("storage", "exists", async (raw) => {
      const params = objectParams(raw);
      const { name, provider } = this.provider(params);
      const key = safeKey(params.key);
      return { profile: name, key, exists: await provider.exists(key) };
    }, "storage.read", "storage");

    manager.registerService("storage", "list", async (raw) => {
      const params = objectParams(raw);
      const { name, provider } = this.provider(params);
      const prefix = typeof params.prefix === "string" && params.prefix.trim() ? safeKey(params.prefix) : "";
      return { profile: name, objects: await provider.list(prefix) };
    }, "storage.list", "storage");

    manager.registerService("storage", "temporary-link", async (raw) => {
      const params = objectParams(raw);
      const { name, provider } = this.provider(params);
      if (!provider.createTemporaryLink) throw new Error(`Storage provider ${provider.kind} does not support temporary links.`);
      const key = safeKey(params.key);
      const expiresSeconds = Number(params.expiresSeconds ?? 900);
      if (!Number.isInteger(expiresSeconds) || expiresSeconds < 30 || expiresSeconds > 604800) throw new Error("expiresSeconds must be between 30 seconds and 7 days.");
      return { profile: name, key, url: await provider.createTemporaryLink(key, expiresSeconds) };
    }, "storage.link", "storage");
  }
}
