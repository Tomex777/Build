import type { ConfigDefinition } from "../shared/config-schema";
import type { BaileyModuleDefinition } from "./module";

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
    for (const command of module.commands ?? []) {
      const canonical = command.name.toLowerCase();
      if (commandNames.has(canonical)) throw new Error(`Duplicate command ${command.name} in ${module.id}`);
      commandNames.add(canonical);
    }

    this.modules.set(module.id, module);
  }

  list(): BaileyModuleDefinition[] {
    return [...this.modules.values()];
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
        definitions.push({
          key: `modules.${module.id}.commands.${command.name}.enabled`,
          moduleId: module.id,
          section: `${module.name} · Commands`,
          label: `.${command.name}`,
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
