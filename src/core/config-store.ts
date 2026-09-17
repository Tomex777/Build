import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import type { ConfigDefinition, ConfigValue, UiConfigValue } from "../shared/config-schema";
import { normalizeValue, toEnvironmentValue } from "../shared/config-schema";

interface StoredEntry {
  secret: boolean;
  value: ConfigValue | string;
}

interface StoredDocument {
  version: 1;
  values: Record<string, StoredEntry>;
}

export interface SecretCodec {
  encode(value: string): string;
  decode(value: string): string;
}

export class JsonConfigStore {
  private document: StoredDocument = { version: 1, values: {} };

  constructor(
    private readonly filePath: string,
    private readonly secretCodec: SecretCodec,
  ) {}

  async load(): Promise<void> {
    try {
      const raw = await readFile(this.filePath, "utf8");
      const parsed = JSON.parse(raw) as StoredDocument;
      if (parsed.version !== 1 || typeof parsed.values !== "object") throw new Error("Unsupported Bailey config format");
      this.document = parsed;
    } catch (error) {
      const code = (error as NodeJS.ErrnoException).code;
      if (code !== "ENOENT") throw error;
    }
  }

  get(definition: ConfigDefinition): ConfigValue {
    const entry = this.document.values[definition.key];
    if (!entry) return definition.defaultValue;

    if (definition.type === "secret") {
      if (!entry.secret || typeof entry.value !== "string") return "";
      return this.secretCodec.decode(entry.value);
    }

    return normalizeValue(definition, entry.value);
  }

  async set(definition: ConfigDefinition, rawValue: unknown): Promise<void> {
    const value = normalizeValue(definition, rawValue);

    if (definition.type === "secret") {
      this.document.values[definition.key] = {
        secret: true,
        value: this.secretCodec.encode(String(value)),
      };
    } else {
      this.document.values[definition.key] = { secret: false, value };
    }

    await this.flush();
  }

  listForUi(definitions: ConfigDefinition[]): UiConfigValue[] {
    return definitions.map((definition) => {
      if (definition.type === "secret") {
        const entry = this.document.values[definition.key];
        return {
          key: definition.key,
          value: "",
          secretConfigured: Boolean(entry?.secret && entry.value),
        };
      }

      return { key: definition.key, value: this.get(definition) };
    });
  }

  toEnvironment(definitions: ConfigDefinition[]): Record<string, string> {
    const environment: Record<string, string> = {};
    for (const definition of definitions) {
      if (!definition.env) continue;
      environment[definition.env] = toEnvironmentValue(this.get(definition));
    }
    return environment;
  }

  private async flush(): Promise<void> {
    await mkdir(dirname(this.filePath), { recursive: true });
    await writeFile(this.filePath, `${JSON.stringify(this.document, null, 2)}\n`, "utf8");
  }
}
