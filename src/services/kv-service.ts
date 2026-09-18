import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { join } from "node:path";
import type { ExternalHostServiceContext, ExternalModuleManager } from "../external/external-module-manager";

type KvDocument = Record<string, unknown>;
const locks = new Map<string, Promise<void>>();

function params(raw: unknown): Record<string, unknown> {
  return raw && typeof raw === "object" && !Array.isArray(raw) ? raw as Record<string, unknown> : {};
}

function key(raw: unknown): string {
  const value = String(raw ?? "").trim();
  if (!value || value.length > 200) throw new Error("KV key must be 1–200 characters.");
  return value;
}

async function withLock<T>(path: string, fn: () => Promise<T>): Promise<T> {
  const previous = locks.get(path) ?? Promise.resolve();
  let release!: () => void;
  const gate = new Promise<void>((resolve) => { release = resolve; });
  const chain = previous.then(() => gate);
  locks.set(path, chain);
  await previous;
  try {
    return await fn();
  } finally {
    release();
    if (locks.get(path) === chain) locks.delete(path);
  }
}

async function documentPath(context: ExternalHostServiceContext): Promise<string> {
  if (!context.dataDirectory) throw new Error("KV service requires the module storage capability.");
  await mkdir(context.dataDirectory, { recursive: true });
  return join(context.dataDirectory, ".bailey-kv.json");
}

async function readDocument(path: string): Promise<KvDocument> {
  try {
    const parsed = JSON.parse(await readFile(path, "utf8")) as unknown;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed as KvDocument : {};
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") return {};
    throw error;
  }
}

async function writeDocument(path: string, document: KvDocument): Promise<void> {
  const temp = `${path}.tmp`;
  await writeFile(temp, `${JSON.stringify(document, null, 2)}\n`, "utf8");
  await rename(temp, path);
}

export function registerKvServices(manager: ExternalModuleManager): void {
  manager.registerService("kv", "get", async (raw, context) => {
    const path = await documentPath(context);
    const lookup = key(params(raw).key);
    const document = await readDocument(path);
    return { key: lookup, found: Object.prototype.hasOwnProperty.call(document, lookup), value: document[lookup] };
  }, "kv.read");

  manager.registerService("kv", "set", async (raw, context) => {
    const path = await documentPath(context);
    const input = params(raw);
    const lookup = key(input.key);
    await withLock(path, async () => {
      const document = await readDocument(path);
      document[lookup] = input.value;
      await writeDocument(path, document);
    });
    return { key: lookup, saved: true };
  }, "kv.write");

  manager.registerService("kv", "delete", async (raw, context) => {
    const path = await documentPath(context);
    const lookup = key(params(raw).key);
    let deleted = false;
    await withLock(path, async () => {
      const document = await readDocument(path);
      deleted = Object.prototype.hasOwnProperty.call(document, lookup);
      delete document[lookup];
      await writeDocument(path, document);
    });
    return { key: lookup, deleted };
  }, "kv.write");

  manager.registerService("kv", "list", async (_raw, context) => {
    const path = await documentPath(context);
    return { keys: Object.keys(await readDocument(path)).sort() };
  }, "kv.read");
}
