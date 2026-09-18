import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import type { SecretCodec } from "../core/config-store";

export type StorageProviderKind = "local" | "s3" | "azure" | "gcs" | "supabase";

export interface StorageProfileInput {
  name: string;
  provider: StorageProviderKind;
  config?: Record<string, string | boolean>;
  secrets?: Record<string, string>;
}

interface StoredStorageProfile {
  name: string;
  provider: StorageProviderKind;
  config: Record<string, string | boolean>;
  secrets: Record<string, string>;
}

interface StorageProfileDocument {
  version: 1;
  defaultProfile: string;
  profiles: StoredStorageProfile[];
}

export interface UiStorageProfile {
  name: string;
  provider: StorageProviderKind;
  isDefault: boolean;
  config: Record<string, string | boolean>;
  secretFields: string[];
}

export interface ResolvedStorageProfile {
  name: string;
  provider: StorageProviderKind;
  config: Record<string, string | boolean>;
  secrets: Record<string, string>;
  isDefault: boolean;
}

const PROFILE_NAME = /^[a-z0-9][a-z0-9._-]{0,63}$/;
const PROVIDERS = new Set<StorageProviderKind>(["local", "s3", "azure", "gcs", "supabase"]);

const REQUIRED_CONFIG: Record<StorageProviderKind, string[]> = {
  local: [],
  s3: ["bucket", "region"],
  azure: ["accountName", "container"],
  gcs: ["projectId", "clientEmail", "bucket"],
  supabase: ["url", "bucket"],
};

const REQUIRED_SECRETS: Record<StorageProviderKind, string[]> = {
  local: [],
  s3: ["accessKeyId", "secretAccessKey"],
  azure: ["accountKey"],
  gcs: ["privateKey"],
  supabase: ["serviceKey"],
};

function normalizeName(value: unknown): string {
  const name = String(value ?? "").trim().toLowerCase();
  if (!PROFILE_NAME.test(name)) throw new Error("Storage profile name must use lowercase letters, numbers, dots, _ or -.");
  return name;
}

function normalizeProvider(value: unknown): StorageProviderKind {
  if (typeof value !== "string" || !PROVIDERS.has(value as StorageProviderKind)) throw new Error("Unsupported storage provider.");
  return value as StorageProviderKind;
}

function cleanConfig(raw: unknown): Record<string, string | boolean> {
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return {};
  const output: Record<string, string | boolean> = {};
  for (const [key, value] of Object.entries(raw as Record<string, unknown>)) {
    if (!/^[A-Za-z0-9._-]{1,80}$/.test(key)) continue;
    if (typeof value === "boolean") output[key] = value;
    else if (typeof value === "string") output[key] = value.trim();
  }
  return output;
}

export class StorageProfileStore {
  private document: StorageProfileDocument = {
    version: 1,
    defaultProfile: "default",
    profiles: [{ name: "default", provider: "local", config: {}, secrets: {} }],
  };

  constructor(
    private readonly filePath: string,
    private readonly secretCodec: SecretCodec,
  ) {}

  async load(): Promise<void> {
    try {
      const parsed = JSON.parse(await readFile(this.filePath, "utf8")) as StorageProfileDocument;
      if (parsed.version !== 1 || !Array.isArray(parsed.profiles)) throw new Error("Unsupported Bailey storage-profile format.");
      const seen = new Set<string>();
      const profiles: StoredStorageProfile[] = [];
      for (const raw of parsed.profiles) {
        const name = normalizeName(raw.name);
        if (seen.has(name)) throw new Error(`Duplicate storage profile: ${name}`);
        seen.add(name);
        profiles.push({
          name,
          provider: normalizeProvider(raw.provider),
          config: cleanConfig(raw.config),
          secrets: raw.secrets && typeof raw.secrets === "object" ? { ...raw.secrets } : {},
        });
      }
      if (!profiles.length) profiles.push({ name: "default", provider: "local", config: {}, secrets: {} });
      const defaultProfile = profiles.some((profile) => profile.name === parsed.defaultProfile)
        ? parsed.defaultProfile
        : profiles[0].name;
      this.document = { version: 1, defaultProfile, profiles };
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
      await this.flush();
    }
  }

  listForUi(): UiStorageProfile[] {
    return this.document.profiles.map((profile) => ({
      name: profile.name,
      provider: profile.provider,
      isDefault: profile.name === this.document.defaultProfile,
      config: { ...profile.config },
      secretFields: Object.entries(profile.secrets).filter(([, value]) => Boolean(value)).map(([key]) => key),
    }));
  }

  resolved(): ResolvedStorageProfile[] {
    return this.document.profiles.map((profile) => ({
      name: profile.name,
      provider: profile.provider,
      isDefault: profile.name === this.document.defaultProfile,
      config: { ...profile.config },
      secrets: Object.fromEntries(Object.entries(profile.secrets).map(([key, value]) => [key, value ? this.secretCodec.decode(value) : ""])),
    }));
  }

  async upsert(raw: StorageProfileInput): Promise<UiStorageProfile> {
    const name = normalizeName(raw.name);
    const provider = normalizeProvider(raw.provider);
    const config = cleanConfig(raw.config);
    const existing = this.document.profiles.find((profile) => profile.name === name);

    for (const field of REQUIRED_CONFIG[provider]) {
      if (!String(config[field] ?? "").trim()) throw new Error(`${provider} storage requires ${field}.`);
    }

    const secrets: Record<string, string> = existing?.provider === provider ? { ...existing.secrets } : {};
    for (const [key, value] of Object.entries(raw.secrets ?? {})) {
      const trimmed = String(value ?? "").trim();
      if (trimmed) secrets[key] = this.secretCodec.encode(trimmed);
    }
    for (const field of REQUIRED_SECRETS[provider]) {
      if (!secrets[field]) throw new Error(`${provider} storage requires ${field}.`);
    }

    const stored: StoredStorageProfile = { name, provider, config, secrets };
    const index = this.document.profiles.findIndex((profile) => profile.name === name);
    if (index >= 0) this.document.profiles[index] = stored;
    else this.document.profiles.push(stored);
    await this.flush();
    return this.listForUi().find((profile) => profile.name === name)!;
  }

  async setDefault(nameValue: unknown): Promise<void> {
    const name = normalizeName(nameValue);
    if (!this.document.profiles.some((profile) => profile.name === name)) throw new Error("Storage profile not found.");
    this.document.defaultProfile = name;
    await this.flush();
  }

  async delete(nameValue: unknown): Promise<void> {
    const name = normalizeName(nameValue);
    if (this.document.profiles.length <= 1) throw new Error("Bailey must keep at least one storage profile.");
    this.document.profiles = this.document.profiles.filter((profile) => profile.name !== name);
    if (this.document.defaultProfile === name) this.document.defaultProfile = this.document.profiles[0].name;
    await this.flush();
  }

  private async flush(): Promise<void> {
    await mkdir(dirname(this.filePath), { recursive: true });
    await writeFile(this.filePath, `${JSON.stringify(this.document, null, 2)}\n`, "utf8");
  }
}
