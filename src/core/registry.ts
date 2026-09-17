import type { ConfigDefinition } from "../shared/config-schema";
import type { BaileyCommandDefinition, BaileyModuleDefinition } from "./module";
import { commandId } from "./module";

export interface ResolvedCommand {
  module: BaileyModuleDefinition;
  command: BaileyCommandDefinition;
}

export class ModuleRegistry {
  private readonly modules = new Map<string, BaileyModuleDefinition>();

  register(module: BaileyModuleDefinition): void {
    if (!/^[a-z0-9][a-z0-9.-]*$/.test(module.id)) {
      throw new Error(`Invalid module id: ${module.id}`);
    }
    if (this.modules.has(module.id)) {
      throw new Error(`Module already registered: ${module.id}`);
    }

    const commandNames = new Set<string>();
    const commandIds = new Set<string>();
    const existingTriggers = new Map<string, string>();
    for (const existingModule of this.modules.values()) {
      for (const existing of existingModule.commands ?? []) {
        existingTriggers.set(existing.name.toLowerCase(), `${existingModule.name}:${existing.name}`);
        for (const alias of existing.aliases ?? []) {
          existingTriggers.set(alias.toLowerCase(), `${existingModule.name}:${existing.name}`);
        }
      }
    }

    for (const command of module.commands ?? []) {
      if (!command.actions?.length && !command.execute) {
        throw new Error(`Command ${command.name} in ${module.id} must define actions or execute()`);
      }

      const id = commandId(command).toLowerCase();
      if (commandIds.has(id)) throw new Error(`Duplicate command id ${id} in ${module.id}`);
      commandIds.add(id);

      const canonical = command.name.toLowerCase();
      if (commandNames.has(canonical)) throw new Error(`Duplicate command ${command.name} in ${module.id}`);
      const existing = existingTriggers.get(canonical);
      if (existing) throw new Error(`Command trigger ${command.name} in ${module.id} is already used by ${existing}`);
      commandNames.add(canonical);

      for (const alias of command.aliases ?? []) {
        const aliasKey = alias.toLowerCase();
        if (commandNames.has(aliasKey)) throw new Error(`Duplicate command alias ${alias} in ${module.id}`);
        const existingAlias = existingTriggers.get(aliasKey);
        if (existingAlias) throw new Error(`Command alias ${alias} in ${module.id} is already used by ${existingAlias}`);
        commandNames.add(aliasKey);
      }
    }

    this.modules.set(module.id, module);
  }

  unregister(moduleId: string): boolean {
    return this.modules.delete(moduleId);
  }

  list(): BaileyModuleDefinition[] {
    return [...this.modules.values()];
  }

  findModule(moduleId: string): BaileyModuleDefinition | undefined {
    return this.modules.get(moduleId);
  }

  findCommand(moduleId: string, id: string): BaileyCommandDefinition | undefined {
    return this.modules.get(moduleId)?.commands?.find((command) => commandId(command) === id);
  }

  resolveCommand(name: string): ResolvedCommand | undefined {
    const target = name.toLowerCase();
    for (const module of this.modules.values()) {
      for (const command of module.commands ?? []) {
        if (command.name.toLowerCase() === target || (command.aliases ?? []).some((alias) => alias.toLowerCase() === target)) {
          return { module, command };
        }
      }
    }
    return undefined;
  }

  getConfigDefinitions(): ConfigDefinition[] {
    const definitions: ConfigDefinition[] = [];

    for (const module of this.modules.values()) {
      definitions.push({
        key: `modules.${module.id}.enabled`,
        moduleId: module.id,
        section: module.name,
        label: "Module enabled",
        type: "toggle",
        defaultValue: module.enabledByDefault ?? true,
        description: `Turn the entire ${module.name} module on or off.`,
      });

      for (const setting of module.settings ?? []) {
        definitions.push({
          ...setting,
          key: `modules.${module.id}.settings.${setting.key}`,
          moduleId: module.id,
          section: module.name,
        });
      }

      for (const command of module.commands ?? []) {
        const id = commandId(command);
        definitions.push({
          key: `modules.${module.id}.commands.${id}.enabled`,
          moduleId: module.id,
          section: `${module.name} · Commands`,
          label: command.name,
          type: "toggle",
          defaultValue: command.enabledByDefault ?? true,
          description: command.description,
        });
      }
    }

    return definitions;
  }

  findConfigDefinition(key: string): ConfigDefinition | undefined {
    return this.getConfigDefinitions().find((definition) => definition.key === key);
  }
}
