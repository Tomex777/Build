export interface MenuCommandEntry {
  moduleId: string;
  moduleName: string;
  section: string;
  name: string;
  description: string;
  enabled: boolean;
}

export interface MenuBuildOptions {
  prefix: string;
  entries: MenuCommandEntry[];
  sectionFilter?: string;
}

function normalize(value: string): string {
  return value.trim().toLowerCase();
}

export function buildCommandMenu({ prefix, entries, sectionFilter }: MenuBuildOptions): string {
  const enabled = entries.filter((entry) => entry.enabled);
  const filter = sectionFilter ? normalize(sectionFilter) : "";
  const filtered = filter
    ? enabled.filter((entry) => normalize(entry.section) === filter || normalize(`${entry.moduleName} ${entry.section}`) === filter)
    : enabled;

  if (filter && !filtered.length) {
    const sections = [...new Set(enabled.map((entry) => entry.section))].sort((a, b) => a.localeCompare(b));
    return `No command section named “${sectionFilter}”.\n\nAvailable sections: ${sections.join(", ") || "None"}`;
  }

  const modules = new Map<string, Map<string, MenuCommandEntry[]>>();
  for (const entry of filtered) {
    const moduleKey = `${entry.moduleId}\u0000${entry.moduleName}`;
    const sections = modules.get(moduleKey) ?? new Map<string, MenuCommandEntry[]>();
    const commands = sections.get(entry.section) ?? [];
    commands.push(entry);
    sections.set(entry.section, commands);
    modules.set(moduleKey, sections);
  }

  const lines: string[] = [filter ? `Commands · ${filtered[0]?.section ?? sectionFilter}` : "Bailey commands"];
  for (const [moduleKey, sections] of modules) {
    const moduleName = moduleKey.split("\u0000")[1];
    lines.push("", `【 ${moduleName} 】`);
    for (const [section, commands] of sections) {
      lines.push(section);
      for (const command of commands.sort((a, b) => a.name.localeCompare(b.name))) {
        lines.push(`${prefix}${command.name} — ${command.description}`);
      }
    }
  }

  if (!filter) {
    lines.push("", `Use ${prefix}menu <section> to show one section.`);
  }
  return lines.join("\n");
}
