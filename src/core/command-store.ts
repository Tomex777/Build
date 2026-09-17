import { randomUUID } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import type { BaileyCommandAction, BaileyCommandDefinition, BaileyModuleDefinition } from "./module";
import { commandId } from "./module";

export interface VisualCommandPatch {
  name: string;
  section: string;
  description: string;
  aliases: string[];
  replyText?: string;
  reactionEmoji?: string;
}

export interface EffectiveCommand extends BaileyCommandDefinition {
  id: string;
  section: string;
  aliases: string[];
  editable: boolean;
  origin: "shipped" | "custom";
  replyText?: string;
  reactionEmoji?: string;
}

interface StoredCustomCommand extends VisualCommandPatch {
  id: string;
  moduleId: string;
}

interface StoredDocumentV1 {
  version: 1;
  commands: Record<string, VisualCommandPatch>;
}

interface StoredDocumentV2 {
  version: 2;
  commands: Record<string, VisualCommandPatch>;
  customCommands: Record<string, StoredCustomCommand>;
}

type StoredDocument = StoredDocumentV2;

function commandKey(moduleId: string, id: string): string {
  return `${moduleId}.${id}`;
}

function normalizeName(value: unknown): string {
  const name = String(value ?? "").trim().toLowerCase();
  if (!/^[a-z0-9][a-z0-9_-]{0,63}$/.test(name)) {
    throw new Error("Command trigger must be 1–64 characters using letters, numbers, _ or -.");
  }
  return name;
}

function normalizeSection(value: unknown): string {
  const section = String(value ?? "General").trim();
  if (!section || section.length > 80) throw new Error("Command section must be 1–80 characters.");
  return section;
}

function normalizeDescription(value: unknown): string {
  const description = String(value ?? "").trim();
  if (!description || description.length > 240) throw new Error("Command description must be 1–240 characters.");
  return description;
}

function normalizeAliases(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  const output: string[] = [];
  const seen = new Set<string>();
  for (const item of value) {
    const alias = normalizeName(item);
    if (seen.has(alias)) continue;
    seen.add(alias);
    output.push(alias);
  }
  return output;
}

function normalizeReaction(value: unknown): string | undefined {
  const reaction = String(value ?? "").trim();
  if (!reaction) return undefined;
  if (reaction.length > 32) throw new Error("Reaction must be a short emoji or emoji sequence.");
  return reaction;
}

function normalizePatch(raw: VisualCommandPatch): VisualCommandPatch {
  const name = normalizeName(raw.name);
  const aliases = normalizeAliases(raw.aliases).filter((alias) => alias !== name);
  const section = normalizeSection(raw.section);
  const description = normalizeDescription(raw.description);
  const replyText = String(raw.replyText ?? "").trim();
  if (!replyText || replyText.length > 4096) throw new Error("Reply text must be 1–4096 characters.");
  const reactionEmoji = normalizeReaction(raw.reactionEmoji);
  return { name, aliases, section, description, replyText, reactionEmoji };
}

function visualActionState(actions?: BaileyCommandAction[]): { replyText: string; reactionEmoji?: string } | undefined {
  if (!actions?.length) return undefined;
  let replyText: string | undefined;
  let reactionEmoji: string | undefined;
  for (const action of actions) {
    if (action.type === "reply") {
      if (replyText !== undefined) return undefined;
      replyText = action.text;
    } else if (action.type === "react") {
      if (reactionEmoji !== undefined) return undefined;
      reactionEmoji = action.emoji;
    } else {
      return undefined;
    }
  }
  if (replyText === undefined) return undefined;
  return { replyText, reactionEmoji };
}

function actionsFromVisual(replyText: string, reactionEmoji?: string): BaileyCommandAction[] {
  const actions: BaileyCommandAction[] = [];
  if (reactionEmoji) actions.push({ type: "react", emoji: reactionEmoji });
  actions.push({ type: "reply", text: replyText });
  return actions;
}

function customKey(moduleId: string, id: string): string {
  return `${moduleId}:${id}`;
}

export class JsonCommandStore {
  private document: StoredDocument = { version: 2, commands: {}, customCommands: {} };

  constructor(private readonly filePath: string) {}

  async load(): Promise<void> {
    try {
      const raw = await readFile(this.filePath, "utf8");
      const parsed = JSON.parse(raw) as StoredDocumentV1 | StoredDocumentV2;
      if (parsed.version === 1 && typeof parsed.commands === "object") {
        this.document = { version: 2, commands: parsed.commands ?? {}, customCommands: {} };
        await this.flush();
        return;
      }
      if (parsed.version !== 2 || typeof parsed.commands !== "object" || typeof parsed.customCommands !== "object") {
        throw new Error("Unsupported Bailey command format");
      }
      this.document = parsed;
    } catch (error) {
      const code = (error as NodeJS.ErrnoException).code;
      if (code !== "ENOENT") throw error;
    }
  }

  effective(module: BaileyModuleDefinition, command: BaileyCommandDefinition): EffectiveCommand {
    const id = commandId(command);
    const override = this.document.commands[commandKey(module.id, id)];
    const defaultVisual = visualActionState(command.actions);
    const editable = defaultVisual !== undefined && !command.execute;
    const replyText = override?.replyText ?? defaultVisual?.replyText;
    const reactionEmoji = override ? normalizeReaction(override.reactionEmoji) : defaultVisual?.reactionEmoji;

    return {
      ...command,
      id,
      name: override?.name ?? command.name,
      section: override?.section ?? command.section ?? "General",
      description: override?.description ?? command.description,
      aliases: override?.aliases ?? command.aliases ?? [],
      editable,
      origin: "shipped",
      replyText,
      reactionEmoji,
      actions: editable && replyText !== undefined ? actionsFromVisual(replyText, reactionEmoji) : command.actions,
    };
  }

  private effectiveCustom(record: StoredCustomCommand): EffectiveCommand {
    const replyText = record.replyText ?? "";
    const reactionEmoji = normalizeReaction(record.reactionEmoji);
    return {
      id: record.id,
      name: record.name,
      section: record.section,
      description: record.description,
      aliases: record.aliases,
      editable: true,
      origin: "custom",
      replyText,
      reactionEmoji,
      actions: actionsFromVisual(replyText, reactionEmoji),
    };
  }

  list(modules: BaileyModuleDefinition[]): Array<{ module: BaileyModuleDefinition; command: EffectiveCommand }> {
    const shipped = modules.flatMap((module) => (module.commands ?? []).map((command) => ({
      module,
      command: this.effective(module, command),
    })));
    const byId = new Map(modules.map((module) => [module.id, module]));
    const custom = Object.values(this.document.customCommands).flatMap((record) => {
      const module = byId.get(record.moduleId);
      return module ? [{ module, command: this.effectiveCustom(record) }] : [];
    });
    return [...shipped, ...custom];
  }

  resolve(modules: BaileyModuleDefinition[], trigger: string): { module: BaileyModuleDefinition; command: EffectiveCommand } | undefined {
    const target = trigger.toLowerCase();
    return this.list(modules).find(({ command }) => command.name.toLowerCase() === target || command.aliases.some((alias) => alias.toLowerCase() === target));
  }

  get(module: BaileyModuleDefinition, command: BaileyCommandDefinition): EffectiveCommand {
    return this.effective(module, command);
  }

  getById(modules: BaileyModuleDefinition[], moduleId: string, id: string): EffectiveCommand | undefined {
    return this.list(modules).find(({ module, command }) => module.id === moduleId && command.id === id)?.command;
  }

  async create(modules: BaileyModuleDefinition[], moduleId: string, raw: VisualCommandPatch): Promise<EffectiveCommand> {
    if (!modules.some((module) => module.id === moduleId)) throw new Error("Module not found.");
    const patch = normalizePatch(raw);
    const id = `user-${randomUUID()}`;
    this.document.customCommands[customKey(moduleId, id)] = { id, moduleId, ...patch };
    await this.flush();
    return this.effectiveCustom(this.document.customCommands[customKey(moduleId, id)]);
  }

  async updateById(modules: BaileyModuleDefinition[], moduleId: string, id: string, raw: VisualCommandPatch): Promise<EffectiveCommand> {
    const custom = this.document.customCommands[customKey(moduleId, id)];
    if (custom) {
      const patch = normalizePatch(raw);
      this.document.customCommands[customKey(moduleId, id)] = { ...custom, ...patch };
      await this.flush();
      return this.effectiveCustom(this.document.customCommands[customKey(moduleId, id)]);
    }

    const module = modules.find((candidate) => candidate.id === moduleId);
    const command = module?.commands?.find((candidate) => commandId(candidate) === id);
    if (!module || !command) throw new Error("Command not found.");
    return this.update(module, command, raw);
  }

  async update(module: BaileyModuleDefinition, command: BaileyCommandDefinition, raw: VisualCommandPatch): Promise<EffectiveCommand> {
    const current = this.effective(module, command);
    if (!current.editable) throw new Error("This command is code-backed and cannot be edited in the visual editor yet.");
    const patch = normalizePatch(raw);
    this.document.commands[commandKey(module.id, current.id)] = patch;
    await this.flush();
    return this.effective(module, command);
  }

  async resetById(modules: BaileyModuleDefinition[], moduleId: string, id: string): Promise<EffectiveCommand> {
    if (this.document.customCommands[customKey(moduleId, id)]) {
      throw new Error("Commands you created do not have shipped defaults. Delete the command instead.");
    }
    const module = modules.find((candidate) => candidate.id === moduleId);
    const command = module?.commands?.find((candidate) => commandId(candidate) === id);
    if (!module || !command) throw new Error("Command not found.");
    return this.reset(module, command);
  }

  async reset(module: BaileyModuleDefinition, command: BaileyCommandDefinition): Promise<EffectiveCommand> {
    delete this.document.commands[commandKey(module.id, commandId(command))];
    await this.flush();
    return this.effective(module, command);
  }

  async deleteById(moduleId: string, id: string): Promise<void> {
    const key = customKey(moduleId, id);
    if (!this.document.customCommands[key]) throw new Error("Only commands created in Studio can be deleted.");
    delete this.document.customCommands[key];
    await this.flush();
  }

  private async flush(): Promise<void> {
    await mkdir(dirname(this.filePath), { recursive: true });
    await writeFile(this.filePath, `${JSON.stringify(this.document, null, 2)}\n`, "utf8");
  }
}
