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
}

export interface EffectiveCommand extends BaileyCommandDefinition {
  id: string;
  section: string;
  aliases: string[];
  editable: boolean;
  replyText?: string;
}

interface StoredDocument {
  version: 1;
  commands: Record<string, VisualCommandPatch>;
}

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

function replyFromActions(actions?: BaileyCommandAction[]): string | undefined {
  if (!actions || actions.length !== 1 || actions[0]?.type !== "reply") return undefined;
  return actions[0].text;
}

export class JsonCommandStore {
  private document: StoredDocument = { version: 1, commands: {} };

  constructor(private readonly filePath: string) {}

  async load(): Promise<void> {
    try {
      const raw = await readFile(this.filePath, "utf8");
      const parsed = JSON.parse(raw) as StoredDocument;
      if (parsed.version !== 1 || typeof parsed.commands !== "object") throw new Error("Unsupported Bailey command format");
      this.document = parsed;
    } catch (error) {
      const code = (error as NodeJS.ErrnoException).code;
      if (code !== "ENOENT") throw error;
    }
  }

  effective(module: BaileyModuleDefinition, command: BaileyCommandDefinition): EffectiveCommand {
    const id = commandId(command);
    const override = this.document.commands[commandKey(module.id, id)];
    const defaultReply = replyFromActions(command.actions);
    const editable = defaultReply !== undefined && !command.execute;
    const replyText = override?.replyText ?? defaultReply;

    return {
      ...command,
      id,
      name: override?.name ?? command.name,
      section: override?.section ?? command.section ?? "General",
      description: override?.description ?? command.description,
      aliases: override?.aliases ?? command.aliases ?? [],
      editable,
      replyText,
      actions: editable && replyText !== undefined ? [{ type: "reply", text: replyText }] : command.actions,
    };
  }

  list(modules: BaileyModuleDefinition[]): Array<{ module: BaileyModuleDefinition; command: EffectiveCommand }> {
    return modules.flatMap((module) => (module.commands ?? []).map((command) => ({
      module,
      command: this.effective(module, command),
    })));
  }

  resolve(modules: BaileyModuleDefinition[], trigger: string): { module: BaileyModuleDefinition; command: EffectiveCommand } | undefined {
    const target = trigger.toLowerCase();
    return this.list(modules).find(({ command }) => command.name.toLowerCase() === target || command.aliases.some((alias) => alias.toLowerCase() === target));
  }

  get(module: BaileyModuleDefinition, command: BaileyCommandDefinition): EffectiveCommand {
    return this.effective(module, command);
  }

  async update(module: BaileyModuleDefinition, command: BaileyCommandDefinition, raw: VisualCommandPatch): Promise<EffectiveCommand> {
    const current = this.effective(module, command);
    if (!current.editable) throw new Error("This command is code-backed and cannot be edited in the visual editor yet.");

    const name = normalizeName(raw.name);
    const aliases = normalizeAliases(raw.aliases).filter((alias) => alias !== name);
    const section = normalizeSection(raw.section);
    const description = normalizeDescription(raw.description);
    const replyText = String(raw.replyText ?? "").trim();
    if (!replyText || replyText.length > 4096) throw new Error("Reply text must be 1–4096 characters.");

    this.document.commands[commandKey(module.id, current.id)] = { name, aliases, section, description, replyText };
    await this.flush();
    return this.effective(module, command);
  }

  async reset(module: BaileyModuleDefinition, command: BaileyCommandDefinition): Promise<EffectiveCommand> {
    delete this.document.commands[commandKey(module.id, commandId(command))];
    await this.flush();
    return this.effective(module, command);
  }

  private async flush(): Promise<void> {
    await mkdir(dirname(this.filePath), { recursive: true });
    await writeFile(this.filePath, `${JSON.stringify(this.document, null, 2)}\n`, "utf8");
  }
}
