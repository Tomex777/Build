import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import { isIP } from "node:net";
import type { ExternalHostServiceContext, ExternalModuleManager } from "../external/external-module-manager";

export interface ModuleNetworkPolicy {
  moduleId: string;
  allow: string[];
  deny: string[];
}

interface PolicyDocument {
  version: 1;
  modules: Record<string, { allow: string[]; deny: string[] }>;
}

function normalizeDomain(value: unknown): string {
  let domain = String(value ?? "").trim().toLowerCase().replace(/\.$/, "");
  if (domain.startsWith("*.")) domain = domain.slice(2);
  if (domain.includes("://") || domain.includes("/") || domain.includes("*") || domain.includes("@")) throw new Error(`Invalid network domain: ${String(value)}`);
  if (isIP(domain) || !/^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/.test(domain)) throw new Error(`Network allowlist entries must be public domain names: ${String(value)}`);
  return domain;
}

function cleanList(value: unknown): string[] {
  if (!Array.isArray(value)) throw new Error("Network domain lists must be arrays.");
  return [...new Set(value.map(normalizeDomain))].sort();
}

export class NetworkAccessPolicyStore {
  private document: PolicyDocument = { version: 1, modules: {} };
  constructor(private readonly filePath: string) {}

  async load(): Promise<void> {
    try {
      const parsed = JSON.parse(await readFile(this.filePath, "utf8")) as PolicyDocument;
      if (parsed.version !== 1 || !parsed.modules || typeof parsed.modules !== "object") throw new Error("Unsupported network policy format.");
      const modules: PolicyDocument["modules"] = {};
      for (const [moduleId, rule] of Object.entries(parsed.modules)) {
        if (!/^[a-z0-9][a-z0-9.-]{0,63}$/.test(moduleId)) continue;
        modules[moduleId] = { allow: cleanList(rule.allow ?? []), deny: cleanList(rule.deny ?? []) };
      }
      this.document = { version: 1, modules };
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
      await this.flush();
    }
  }

  get(moduleId: string): ModuleNetworkPolicy {
    const policy = this.document.modules[moduleId] ?? { allow: [], deny: [] };
    return { moduleId, allow: [...policy.allow], deny: [...policy.deny] };
  }

  list(): ModuleNetworkPolicy[] {
    return Object.keys(this.document.modules).sort().map((moduleId) => this.get(moduleId));
  }

  async set(moduleId: string, allow: unknown, deny: unknown): Promise<ModuleNetworkPolicy> {
    if (!/^[a-z0-9][a-z0-9.-]{0,63}$/.test(moduleId)) throw new Error("Invalid module id.");
    const allowed = cleanList(allow);
    const denied = cleanList(deny);
    this.document.modules[moduleId] = { allow: allowed, deny: denied };
    await this.flush();
    return this.get(moduleId);
  }

  permits(moduleId: string, domain: string): boolean {
    const host = normalizeDomain(domain);
    const policy = this.get(moduleId);
    const matches = (list: string[]) => list.some((root) => host === root || host.endsWith(`.${root}`));
    return !matches(policy.deny) && matches(policy.allow);
  }

  private async flush(): Promise<void> {
    await mkdir(dirname(this.filePath), { recursive: true });
    await writeFile(this.filePath, `${JSON.stringify(this.document, null, 2)}\n`, "utf8");
  }
}

function objectParams(raw: unknown): Record<string, unknown> {
  return raw && typeof raw === "object" && !Array.isArray(raw) ? raw as Record<string, unknown> : {};
}

function safeHeaders(raw: unknown): Headers {
  const result = new Headers();
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return result;
  const forbidden = new Set(["host", "content-length", "connection", "cookie", "set-cookie", "proxy-authorization", "transfer-encoding"]);
  for (const [name, value] of Object.entries(raw as Record<string, unknown>)) {
    const key = name.toLowerCase();
    if (forbidden.has(key)) continue;
    if (!/^[a-z0-9!#$%&'*+.^_`|~-]{1,80}$/i.test(name)) throw new Error(`Invalid HTTP header name: ${name}`);
    if (typeof value !== "string" || value.length > 8192) throw new Error(`HTTP header ${name} must be a string up to 8192 characters.`);
    result.set(name, value);
  }
  return result;
}

async function readCapped(response: Response, maxBytes: number): Promise<Buffer> {
  if (!response.body) return Buffer.alloc(0);
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > maxBytes) {
      await reader.cancel();
      throw new Error(`HTTP response exceeded the ${maxBytes}-byte service limit.`);
    }
    chunks.push(value);
  }
  return Buffer.concat(chunks.map((chunk) => Buffer.from(chunk)));
}

export function registerNetworkHttpService(
  manager: ExternalModuleManager,
  policies: NetworkAccessPolicyStore,
  onBytes: (moduleId: string, bytes: number) => void = () => {},
): void {
  manager.registerService("network", "request", async (raw, context) => {
    const input = objectParams(raw);
    const url = new URL(String(input.url ?? ""));
    if (url.protocol !== "https:" || url.username || url.password || isIP(url.hostname)) throw new Error("Host-mediated HTTP accepts HTTPS public domain URLs only.");
    if (!policies.permits(context.moduleId, url.hostname)) throw new Error(`Domain is not allowed for ${context.moduleId}: ${url.hostname}`);
    const method = String(input.method ?? "GET").toUpperCase();
    if (!["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"].includes(method)) throw new Error("Unsupported HTTP method.");
    const headers = safeHeaders(input.headers);
    let body: string | Blob | undefined;
    let requestBytes = 0;
    if (input.text !== undefined && input.base64 !== undefined) throw new Error("Specify only one of text or base64.");
    if (typeof input.text === "string") {
      requestBytes = Buffer.byteLength(input.text, "utf8");
      body = input.text;
    } else if (typeof input.base64 === "string") {
      const data = Buffer.from(input.base64, "base64");
      requestBytes = data.byteLength;
      body = new Blob([data]);
      headers.set("content-type", headers.get("content-type") ?? "application/octet-stream");
    }
    if (requestBytes > 256 * 1024) throw new Error("HTTP request body exceeds the 256 KB service limit.");
    if (["GET", "HEAD"].includes(method) && body) throw new Error(`${method} requests cannot include a body.`);
    const abort = new AbortController();
    const timeout = setTimeout(() => abort.abort(), 15_000);
    try {
      const response = await fetch(url, { method, headers, body, redirect: "manual", signal: abort.signal });
      const data = method === "HEAD" ? Buffer.alloc(0) : await readCapped(response, 2 * 1024 * 1024);
      onBytes(context.moduleId, requestBytes + data.byteLength);
      const encoding = input.responseEncoding === "base64" ? "base64" : "text";
      return {
        status: response.status,
        headers: Object.fromEntries([...response.headers].filter(([name]) => !["set-cookie", "www-authenticate"].includes(name.toLowerCase()))),
        encoding,
        body: encoding === "base64" ? data.toString("base64") : data.toString("utf8"),
        requestBytes,
        responseBytes: data.byteLength,
      };
    } finally {
      clearTimeout(timeout);
    }
  }, "network.http");
}
