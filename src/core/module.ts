import type { ModuleSettingDefinition } from "../shared/config-schema";

export interface CommandContext {
  remoteJid: string;
  senderJid?: string;
  text: string;
  args: string[];
  reply(text: string): Promise<void>;
  react(emoji: string): Promise<void>;
  /** Render Bailey's current command menu using effective modules, sections, prefix and enabled state. */
  showMenu(sectionFilter?: string): Promise<void>;
}

export interface MessageEventContext {
  remoteJid: string;
  senderJid?: string;
  text?: string;
  pushName?: string;
  timestamp?: number;
  reply(text: string): Promise<void>;
  react(emoji: string): Promise<void>;
}

export interface ReplyCommandAction {
  type: "reply";
  text: string;
}

export interface ReactCommandAction {
  type: "react";
  emoji: string;
}

export type BaileyCommandAction = ReplyCommandAction | ReactCommandAction;

export interface BaileyCommandDefinition {
  /** Stable identity used for persisted overrides. It must never depend on the visible trigger. */
  id?: string;
  /** Visible command trigger without the configured prefix. */
  name: string;
  /** Human-facing menu/category grouping. */
  section?: string;
  description: string;
  aliases?: string[];
  enabledByDefault?: boolean;
  /** Declarative actions can be edited by the visual command editor. */
  actions?: BaileyCommandAction[];
  /** Code commands remain supported for capabilities that cannot be represented visually yet. */
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
  /** Optional passive hook for ordinary incoming WhatsApp messages. */
  onMessage?: (context: MessageEventContext) => Promise<void>;
}

export function commandId(command: BaileyCommandDefinition): string {
  return command.id ?? command.name;
}

export function defineModule<T extends BaileyModuleDefinition>(definition: T): T {
  return definition;
}

/**
 * Type-friendly authoring helper. Runtime validity is enforced when a module is registered,
 * so tooling can safely construct command metadata before an implementation is attached.
 */
export function defineCommand<T extends BaileyCommandDefinition>(definition: T): T {
  return definition;
}
