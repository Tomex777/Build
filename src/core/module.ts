import type { ModuleSettingDefinition } from "../shared/config-schema";

export interface CommandContext {
  text: string;
  args: string[];
  reply(text: string): Promise<void>;
  react(emoji: string): Promise<void>;
}

export interface BaileyCommandDefinition {
  name: string;
  description: string;
  aliases?: string[];
  enabledByDefault?: boolean;
  execute?: (context: CommandContext) => Promise<void>;
}

export interface BaileyModuleDefinition {
  id: string;
  name: string;
  version: string;
  description?: string;
  enabledByDefault?: boolean;
  settings?: ModuleSettingDefinition[];
  commands?: BaileyCommandDefinition[];
}

export function defineModule<T extends BaileyModuleDefinition>(definition: T): T {
  return definition;
}

export function defineCommand<T extends BaileyCommandDefinition>(definition: T): T {
  return definition;
}
