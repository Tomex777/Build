export type ConfigValue = boolean | string | number;
export type SettingType = "toggle" | "text" | "secret" | "number" | "select";

export interface SelectOption {
  label: string;
  value: string;
}

export interface ModuleSettingDefinition {
  key: string;
  label: string;
  type: SettingType;
  defaultValue: ConfigValue;
  description?: string;
  env?: string;
  placeholder?: string;
  options?: SelectOption[];
  min?: number;
  max?: number;
  step?: number;
}

export interface ConfigDefinition extends ModuleSettingDefinition {
  key: string;
  moduleId: string;
  section: string;
}

export interface UiConfigValue {
  key: string;
  value: ConfigValue;
  secretConfigured?: boolean;
}

export function normalizeValue(definition: ConfigDefinition, value: unknown): ConfigValue {
  switch (definition.type) {
    case "toggle":
      if (typeof value !== "boolean") throw new Error(`${definition.key} expects a boolean`);
      return value;
    case "number": {
      const number = typeof value === "number" ? value : Number(value);
      if (!Number.isFinite(number)) throw new Error(`${definition.key} expects a number`);
      if (definition.min !== undefined && number < definition.min) throw new Error(`${definition.key} is below its minimum`);
      if (definition.max !== undefined && number > definition.max) throw new Error(`${definition.key} is above its maximum`);
      return number;
    }
    case "select": {
      if (typeof value !== "string") throw new Error(`${definition.key} expects text`);
      const allowed = definition.options?.map((option) => option.value) ?? [];
      if (!allowed.includes(value)) throw new Error(`${definition.key} has an unsupported option`);
      return value;
    }
    case "text":
    case "secret":
      if (typeof value !== "string") throw new Error(`${definition.key} expects text`);
      return value;
  }
}

export function toEnvironmentValue(value: ConfigValue): string {
  if (typeof value === "boolean") return value ? "true" : "false";
  return String(value);
}

export const setting = {
  toggle(key: string, label: string, defaultValue: boolean, extra: Omit<ModuleSettingDefinition, "key" | "label" | "type" | "defaultValue"> = {}): ModuleSettingDefinition {
    return { key, label, type: "toggle", defaultValue, ...extra };
  },
  text(key: string, label: string, defaultValue = "", extra: Omit<ModuleSettingDefinition, "key" | "label" | "type" | "defaultValue"> = {}): ModuleSettingDefinition {
    return { key, label, type: "text", defaultValue, ...extra };
  },
  secret(key: string, label: string, extra: Omit<ModuleSettingDefinition, "key" | "label" | "type" | "defaultValue"> = {}): ModuleSettingDefinition {
    return { key, label, type: "secret", defaultValue: "", ...extra };
  },
  number(key: string, label: string, defaultValue: number, extra: Omit<ModuleSettingDefinition, "key" | "label" | "type" | "defaultValue"> = {}): ModuleSettingDefinition {
    return { key, label, type: "number", defaultValue, ...extra };
  },
  select(key: string, label: string, defaultValue: string, options: SelectOption[], extra: Omit<ModuleSettingDefinition, "key" | "label" | "type" | "defaultValue" | "options"> = {}): ModuleSettingDefinition {
    return { key, label, type: "select", defaultValue, options, ...extra };
  },
};
