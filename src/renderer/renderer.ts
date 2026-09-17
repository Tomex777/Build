import { BAILEY_MARK_SVG } from "../brand/logo";

interface ConfigDefinition {
  key: string;
  moduleId: string;
  section: string;
  label: string;
  type: "toggle" | "text" | "secret" | "number" | "select";
  defaultValue: boolean | string | number;
  description?: string;
  env?: string;
  placeholder?: string;
  options?: Array<{ label: string; value: string }>;
  min?: number;
  max?: number;
  step?: number;
}

interface UiConfigValue {
  key: string;
  value: boolean | string | number;
  secretConfigured?: boolean;
}

interface EffectiveCommand {
  id: string;
  name: string;
  section: string;
  aliases: string[];
  description: string;
  editable: boolean;
  origin: "shipped" | "custom";
  replyText?: string;
}

interface ModuleView {
  id: string;
  name: string;
  version: string;
  description?: string;
  commands: EffectiveCommand[];
}

interface ModulesPayload {
  prefix: string;
  modules: ModuleView[];
}

interface VisualCommandPatch {
  name: string;
  section: string;
  aliases: string[];
  description: string;
  replyText: string;
}

interface StudioFile {
  path: string;
  name: string;
  language: string;
  content: string;
}

interface EngineStatus {
  provider: "lia";
  packageName: string;
  apiVersion: number;
  activeVersion?: string;
  previousVersion?: string;
  installedVersions: string[];
  latestVersion?: string;
  updateAvailable: boolean;
  runtime: "stopped" | "installing" | "starting" | "running" | "error";
  whatsapp: "not-connected" | "connecting" | "paired" | "connected" | "disconnected";
  pairingCode?: string;
  lastError?: string;
}

interface BaileyApi {
  getState(): Promise<{ runtime: string; whatsapp: string; moduleCount: number; commandCount: number; version: string }>;
  getModules(): Promise<ModulesPayload>;
  getCommand(moduleId: string, commandId: string): Promise<EffectiveCommand>;
  createCommand(patch: VisualCommandPatch): Promise<EffectiveCommand>;
  updateCommand(moduleId: string, commandId: string, patch: VisualCommandPatch): Promise<EffectiveCommand>;
  resetCommand(moduleId: string, commandId: string): Promise<EffectiveCommand>;
  deleteCommand(moduleId: string, commandId: string): Promise<{ ok: boolean }>;
  getConfig(): Promise<{ definitions: ConfigDefinition[]; values: UiConfigValue[] }>;
  setConfig(key: string, value: unknown): Promise<{ ok: boolean }>;
  openStudioFile(): Promise<StudioFile | null>;
  saveStudioFile(path: string, content: string): Promise<{ ok: boolean }>;
  getEngineStatus(): Promise<EngineStatus>;
  checkEngineLatest(): Promise<EngineStatus>;
  installDefaultEngine(): Promise<EngineStatus>;
  installEngineVersion(version: string): Promise<EngineStatus>;
  updateEngine(): Promise<EngineStatus>;
  rollbackEngine(): Promise<EngineStatus>;
  startEngine(): Promise<EngineStatus>;
  stopEngine(): Promise<EngineStatus>;
  pairEngine(phoneNumber: string): Promise<EngineStatus>;
  onEngineStatus(listener: (status: EngineStatus) => void): () => void;
}

interface Window {
  bailey: BaileyApi;
}

declare const window: Window & typeof globalThis;

for (const element of document.querySelectorAll<HTMLElement>("[data-bailey-mark]")) {
  element.innerHTML = BAILEY_MARK_SVG;
}

const navButtons = [...document.querySelectorAll<HTMLButtonElement>(".nav-item")];
const views = [...document.querySelectorAll<HTMLElement>(".view")];
const saveStatus = document.querySelector<HTMLElement>("#save-status")!;
const commandDialog = document.querySelector<HTMLDialogElement>("#command-editor")!;
const commandForm = document.querySelector<HTMLFormElement>("#command-editor-form")!;
const fileDialog = document.querySelector<HTMLDialogElement>("#file-editor")!;
const fileContent = document.querySelector<HTMLTextAreaElement>("#file-content")!;
let activeCommand: { moduleId: string; commandId: string; origin: "shipped" | "custom" } | null = null;
let creatingCommand = false;
let activeFile: StudioFile | null = null;
let fileDirty = false;
let currentPrefix = ".";

navButtons.forEach((button) => {
  button.addEventListener("click", () => {
    const target = button.dataset.view;
    navButtons.forEach((item) => item.classList.toggle("active", item === button));
    views.forEach((view) => view.classList.toggle("active", view.id === `view-${target}`));
  });
});

async function save(definition: ConfigDefinition, value: unknown): Promise<void> {
  saveStatus.textContent = "Saving…";
  saveStatus.className = "save-status saving";
  try {
    await window.bailey.setConfig(definition.key, value);
    saveStatus.textContent = "All changes saved";
    saveStatus.className = "save-status";
    if (definition.key === "modules.core.settings.prefix") {
      await Promise.all([renderModules(), renderConfiguration()]);
    }
  } catch (error) {
    saveStatus.textContent = error instanceof Error ? `Couldn’t save: ${error.message}` : "Couldn’t save";
    saveStatus.className = "save-status error";
  }
}

function createControl(definition: ConfigDefinition, current: UiConfigValue): HTMLElement {
  const wrap = document.createElement("div");
  wrap.className = "control";

  if (definition.type === "toggle") {
    const label = document.createElement("label");
    label.className = "toggle";
    const input = document.createElement("input");
    input.type = "checkbox";
    input.checked = Boolean(current.value);
    input.addEventListener("change", () => void save(definition, input.checked));
    const track = document.createElement("span");
    label.append(input, track);
    wrap.append(label);
    return wrap;
  }

  if (definition.type === "select") {
    const select = document.createElement("select");
    for (const option of definition.options ?? []) {
      const element = document.createElement("option");
      element.value = option.value;
      element.textContent = option.label;
      select.append(element);
    }
    select.value = String(current.value);
    select.addEventListener("change", () => void save(definition, select.value));
    wrap.append(select);
    return wrap;
  }

  const input = document.createElement("input");
  input.type = definition.type === "secret" ? "password" : definition.type;
  if (definition.type === "number") {
    input.value = String(current.value);
    if (definition.min !== undefined) input.min = String(definition.min);
    if (definition.max !== undefined) input.max = String(definition.max);
    if (definition.step !== undefined) input.step = String(definition.step);
  } else {
    input.value = definition.type === "secret" ? "" : String(current.value);
    input.placeholder = definition.type === "secret" && current.secretConfigured
      ? "Saved securely — type to replace"
      : definition.placeholder ?? "";
  }

  input.addEventListener("change", () => {
    const next = definition.type === "number" ? Number(input.value) : input.value;
    void save(definition, next);
    if (definition.type === "secret" && input.value) {
      input.value = "";
      input.placeholder = "Saved securely — type to replace";
    }
  });

  wrap.append(input);
  if (definition.type === "secret") {
    const note = document.createElement("small");
    note.className = "secret-note";
    note.textContent = current.secretConfigured ? "Stored with OS-backed encryption" : "Not configured";
    wrap.append(note);
  }
  return wrap;
}

async function renderConfiguration(): Promise<void> {
  const { definitions, values } = await window.bailey.getConfig();
  const valuesByKey = new Map<string, UiConfigValue>(values.map((value) => [value.key, value]));
  const sections = new Map<string, ConfigDefinition[]>();
  for (const definition of definitions) {
    const group = sections.get(definition.section) ?? [];
    group.push(definition);
    sections.set(definition.section, group);
  }

  const root = document.querySelector<HTMLElement>("#config-sections")!;
  root.replaceChildren();

  for (const [sectionName, sectionDefinitions] of sections) {
    const section = document.createElement("article");
    section.className = "config-section";
    const header = document.createElement("div");
    header.className = "config-section-header";
    const heading = document.createElement("h2");
    heading.textContent = sectionName;
    const subtitle = document.createElement("p");
    subtitle.textContent = "Generated from the module schema. Changes save automatically.";
    header.append(heading, subtitle);
    section.append(header);

    for (const definition of sectionDefinitions) {
      const row = document.createElement("div");
      row.className = "setting-row";
      const copy = document.createElement("div");
      copy.className = "setting-copy";
      const title = document.createElement("div");
      title.className = "setting-title";
      const strong = document.createElement("strong");
      strong.textContent = definition.label;
      title.append(strong);
      if (definition.env) {
        const env = document.createElement("span");
        env.className = "env-chip";
        env.textContent = definition.env;
        title.append(env);
      }
      const description = document.createElement("p");
      description.textContent = definition.description ?? definition.key;
      copy.append(title, description);
      const current = valuesByKey.get(definition.key) ?? { key: definition.key, value: definition.defaultValue };
      row.append(copy, createControl(definition, current));
      section.append(row);
    }
    root.append(section);
  }
}

function makePill(text: string): HTMLElement {
  const pill = document.createElement("span");
  pill.className = "pill";
  pill.textContent = text;
  return pill;
}

function setCommandEditorMode(mode: "create" | "edit", command?: EffectiveCommand): void {
  const reset = document.querySelector<HTMLButtonElement>("#command-reset")!;
  const remove = document.querySelector<HTMLButtonElement>("#command-delete")!;
  const saveButton = document.querySelector<HTMLButtonElement>("#command-save")!;
  if (mode === "create") {
    reset.hidden = true;
    remove.hidden = true;
    saveButton.textContent = "Create command";
  } else {
    reset.hidden = command?.origin !== "shipped";
    remove.hidden = command?.origin !== "custom";
    saveButton.textContent = "Save command";
  }
}

async function openCommandEditor(moduleId: string, commandId: string): Promise<void> {
  const command = await window.bailey.getCommand(moduleId, commandId);
  if (!command.editable) return;
  creatingCommand = false;
  activeCommand = { moduleId, commandId, origin: command.origin };
  setCommandEditorMode("edit", command);
  document.querySelector<HTMLElement>("#command-prefix-preview")!.textContent = currentPrefix;
  document.querySelector<HTMLElement>("#command-editor-title")!.textContent = `Edit ${currentPrefix}${command.name}`;
  document.querySelector<HTMLElement>("#command-editor-copy")!.textContent = command.origin === "custom"
    ? "Edit this command with the same visual builder used to create it."
    : "Edit this shipped command visually. Reset restores Bailey’s original default.";
  document.querySelector<HTMLInputElement>("#command-name")!.value = command.name;
  document.querySelector<HTMLInputElement>("#command-section")!.value = command.section;
  document.querySelector<HTMLInputElement>("#command-aliases")!.value = command.aliases.join(", ");
  document.querySelector<HTMLInputElement>("#command-description")!.value = command.description;
  document.querySelector<HTMLTextAreaElement>("#command-reply")!.value = command.replyText ?? "";
  document.querySelector<HTMLElement>("#command-editor-error")!.textContent = "";
  commandDialog.showModal();
  document.querySelector<HTMLInputElement>("#command-name")!.focus();
}

function openCreateCommandEditor(): void {
  creatingCommand = true;
  activeCommand = null;
  setCommandEditorMode("create");
  document.querySelector<HTMLElement>("#command-prefix-preview")!.textContent = currentPrefix;
  document.querySelector<HTMLElement>("#command-editor-title")!.textContent = "Create command";
  document.querySelector<HTMLElement>("#command-editor-copy")!.textContent = "Create a command without code. You can reopen it later in this same visual editor.";
  document.querySelector<HTMLInputElement>("#command-name")!.value = "";
  document.querySelector<HTMLInputElement>("#command-section")!.value = "General";
  document.querySelector<HTMLInputElement>("#command-aliases")!.value = "";
  document.querySelector<HTMLInputElement>("#command-description")!.value = "";
  document.querySelector<HTMLTextAreaElement>("#command-reply")!.value = "";
  document.querySelector<HTMLElement>("#command-editor-error")!.textContent = "";
  commandDialog.showModal();
  document.querySelector<HTMLInputElement>("#command-name")!.focus();
}

function closeCommandEditor(): void {
  activeCommand = null;
  creatingCommand = false;
  commandDialog.close();
}

async function refreshCommandSurfaces(): Promise<void> {
  await Promise.all([renderModules(), renderConfiguration(), renderDashboard()]);
}

function commandPatchFromForm(): VisualCommandPatch {
  return {
    name: document.querySelector<HTMLInputElement>("#command-name")!.value,
    section: document.querySelector<HTMLInputElement>("#command-section")!.value,
    aliases: document.querySelector<HTMLInputElement>("#command-aliases")!.value.split(",").map((value) => value.trim()).filter(Boolean),
    description: document.querySelector<HTMLInputElement>("#command-description")!.value,
    replyText: document.querySelector<HTMLTextAreaElement>("#command-reply")!.value,
  };
}

commandForm.addEventListener("submit", (event) => {
  event.preventDefault();
  const error = document.querySelector<HTMLElement>("#command-editor-error")!;
  error.textContent = "";
  const patch = commandPatchFromForm();

  const work = creatingCommand
    ? window.bailey.createCommand(patch)
    : activeCommand
      ? window.bailey.updateCommand(activeCommand.moduleId, activeCommand.commandId, patch)
      : Promise.reject(new Error("No command selected."));

  void work.then(async () => {
    closeCommandEditor();
    await refreshCommandSurfaces();
  }).catch((reason) => {
    error.textContent = reason instanceof Error ? reason.message : String(reason);
  });
});

document.querySelector<HTMLButtonElement>("#command-reset")!.addEventListener("click", () => {
  if (!activeCommand || activeCommand.origin !== "shipped") return;
  const ref = activeCommand;
  void window.bailey.resetCommand(ref.moduleId, ref.commandId).then(async () => {
    closeCommandEditor();
    await refreshCommandSurfaces();
  }).catch((reason) => {
    document.querySelector<HTMLElement>("#command-editor-error")!.textContent = reason instanceof Error ? reason.message : String(reason);
  });
});

document.querySelector<HTMLButtonElement>("#command-delete")!.addEventListener("click", () => {
  if (!activeCommand || activeCommand.origin !== "custom") return;
  const ref = activeCommand;
  void window.bailey.deleteCommand(ref.moduleId, ref.commandId).then(async () => {
    closeCommandEditor();
    await refreshCommandSurfaces();
  }).catch((reason) => {
    document.querySelector<HTMLElement>("#command-editor-error")!.textContent = reason instanceof Error ? reason.message : String(reason);
  });
});

document.querySelector<HTMLButtonElement>("#command-editor-close")!.addEventListener("click", closeCommandEditor);
document.querySelector<HTMLButtonElement>("#command-cancel")!.addEventListener("click", closeCommandEditor);
document.querySelector<HTMLButtonElement>("#studio-create-command")!.addEventListener("click", openCreateCommandEditor);

async function renderModules(): Promise<void> {
  const payload = await window.bailey.getModules();
  currentPrefix = payload.prefix;
  const root = document.querySelector<HTMLElement>("#module-list")!;
  root.replaceChildren();

  for (const module of payload.modules) {
    const card = document.createElement("article");
    card.className = "module-card";

    const heading = document.createElement("div");
    heading.className = "module-heading";
    const headingCopy = document.createElement("div");
    const title = document.createElement("h2");
    title.textContent = module.name;
    const description = document.createElement("p");
    description.textContent = module.description ?? "";
    headingCopy.append(title, description);
    const meta = document.createElement("div");
    meta.className = "module-meta";
    meta.append(makePill(module.id), makePill(`v${module.version}`));
    heading.append(headingCopy, meta);
    card.append(heading);

    if (!module.commands.length) {
      const empty = document.createElement("p");
      empty.className = "muted command-empty";
      empty.textContent = module.id === "my-commands" ? "No commands yet. Create one from Studio." : "No commands registered";
      card.append(empty);
      root.append(card);
      continue;
    }

    const sections = new Map<string, EffectiveCommand[]>();
    for (const command of module.commands) {
      const group = sections.get(command.section) ?? [];
      group.push(command);
      sections.set(command.section, group);
    }

    const groupsWrap = document.createElement("div");
    groupsWrap.className = "command-groups";
    for (const [sectionName, commands] of sections) {
      const group = document.createElement("section");
      group.className = "command-group";
      const groupHeader = document.createElement("div");
      groupHeader.className = "command-group-header";
      const groupTitle = document.createElement("strong");
      groupTitle.textContent = sectionName;
      const groupCount = document.createElement("span");
      groupCount.textContent = `${commands.length} command${commands.length === 1 ? "" : "s"}`;
      groupHeader.append(groupTitle, groupCount);
      group.append(groupHeader);

      for (const command of commands) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `command-row${command.editable ? " editable" : ""}`;
        button.disabled = !command.editable;
        const commandCopy = document.createElement("span");
        commandCopy.className = "command-row-copy";
        const trigger = document.createElement("strong");
        trigger.textContent = `${payload.prefix}${command.name}`;
        const desc = document.createElement("small");
        desc.textContent = command.description;
        commandCopy.append(trigger, desc);
        const commandMeta = document.createElement("span");
        commandMeta.className = "command-row-meta";
        if (command.aliases.length) {
          const aliases = document.createElement("span");
          aliases.textContent = command.aliases.map((alias) => `${payload.prefix}${alias}`).join(" · ");
          commandMeta.append(aliases);
        }
        const edit = document.createElement("span");
        edit.className = "edit-label";
        edit.textContent = command.editable ? "Edit visually" : "Code-backed";
        commandMeta.append(edit);
        button.append(commandCopy, commandMeta);
        if (command.editable) button.addEventListener("click", () => void openCommandEditor(module.id, command.id));
        group.append(button);
      }
      groupsWrap.append(group);
    }
    card.append(groupsWrap);
    root.append(card);
  }
}

function closeFileEditor(): void {
  activeFile = null;
  fileDirty = false;
  fileDialog.close();
}

async function openStudioFile(): Promise<void> {
  const status = document.querySelector<HTMLElement>("#file-save-status")!;
  try {
    const file = await window.bailey.openStudioFile();
    if (!file) return;
    activeFile = file;
    fileDirty = false;
    document.querySelector<HTMLElement>("#file-language")!.textContent = file.language.toUpperCase();
    document.querySelector<HTMLElement>("#file-name")!.textContent = file.name;
    document.querySelector<HTMLElement>("#file-path")!.textContent = file.path;
    fileContent.value = file.content;
    status.textContent = "No unsaved changes";
    status.className = "save-status";
    fileDialog.showModal();
    fileContent.focus();
  } catch (error) {
    status.textContent = error instanceof Error ? error.message : String(error);
    status.className = "save-status error";
  }
}

fileContent.addEventListener("input", () => {
  fileDirty = true;
  const status = document.querySelector<HTMLElement>("#file-save-status")!;
  status.textContent = "Unsaved changes";
  status.className = "save-status saving";
});

document.querySelector<HTMLButtonElement>("#studio-open-file")!.addEventListener("click", () => void openStudioFile());
document.querySelector<HTMLButtonElement>("#file-save")!.addEventListener("click", () => {
  if (!activeFile) return;
  const status = document.querySelector<HTMLElement>("#file-save-status")!;
  status.textContent = "Saving…";
  status.className = "save-status saving";
  void window.bailey.saveStudioFile(activeFile.path, fileContent.value).then(() => {
    fileDirty = false;
    status.textContent = "Saved";
    status.className = "save-status";
  }).catch((error) => {
    status.textContent = error instanceof Error ? `Couldn’t save: ${error.message}` : "Couldn’t save";
    status.className = "save-status error";
  });
});
document.querySelector<HTMLButtonElement>("#file-editor-close")!.addEventListener("click", closeFileEditor);
document.querySelector<HTMLButtonElement>("#file-cancel")!.addEventListener("click", closeFileEditor);

function prettyState(value: string): string {
  return value.split("-").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
}

function renderEngineStatus(status: EngineStatus): void {
  document.querySelector<HTMLElement>("#engine-package")!.textContent = status.packageName;
  document.querySelector<HTMLElement>("#engine-api")!.textContent = "Managed engine";
  document.querySelector<HTMLElement>("#engine-current")!.textContent = status.activeVersion ?? "Not installed";
  document.querySelector<HTMLElement>("#engine-latest")!.textContent = status.latestVersion ?? "Unknown";
  document.querySelector<HTMLElement>("#engine-previous")!.textContent = status.previousVersion ?? "None";
  document.querySelector<HTMLElement>("#engine-runtime-badge")!.textContent = prettyState(status.runtime);
  document.querySelector<HTMLElement>("#engine-wa-badge")!.textContent = prettyState(status.whatsapp);
  document.querySelector<HTMLElement>("#runtime-state")!.textContent = prettyState(status.runtime);
  document.querySelector<HTMLElement>("#whatsapp-state")!.textContent = prettyState(status.whatsapp);

  const installed = Boolean(status.activeVersion);
  const busy = status.runtime === "installing" || status.runtime === "starting";
  const running = status.runtime === "running" || status.runtime === "starting";
  const installButton = document.querySelector<HTMLButtonElement>("#engine-install")!;
  installButton.textContent = installed ? "Reinstall current" : "Install engine";
  installButton.disabled = busy;
  document.querySelector<HTMLButtonElement>("#engine-update")!.disabled = busy || !status.updateAvailable;
  document.querySelector<HTMLButtonElement>("#engine-rollback")!.disabled = busy || !status.previousVersion;
  document.querySelector<HTMLButtonElement>("#engine-start")!.disabled = busy || running || !installed;
  document.querySelector<HTMLButtonElement>("#engine-stop")!.disabled = !running;
  document.querySelector<HTMLButtonElement>("#pair-button")!.disabled = !running || status.whatsapp === "connected";

  const pairWrap = document.querySelector<HTMLElement>("#pairing-code-wrap")!;
  if (status.pairingCode) {
    pairWrap.hidden = false;
    document.querySelector<HTMLElement>("#pairing-code")!.textContent = status.pairingCode;
  } else {
    pairWrap.hidden = true;
  }

  const error = document.querySelector<HTMLElement>("#engine-error")!;
  error.textContent = status.lastError ?? "";
  const dot = document.querySelector<HTMLElement>("#sidebar-status-dot")!;
  dot.classList.toggle("connected", status.whatsapp === "connected");
  document.querySelector<HTMLElement>("#sidebar-status-text")!.textContent = status.whatsapp === "connected" ? "WhatsApp connected" : "Local host";
}

async function renderDashboard(): Promise<void> {
  const state = await window.bailey.getState();
  document.querySelector<HTMLElement>("#runtime-state")!.textContent = prettyState(state.runtime);
  document.querySelector<HTMLElement>("#whatsapp-state")!.textContent = prettyState(state.whatsapp);
  document.querySelector<HTMLElement>("#module-count")!.textContent = `${state.moduleCount} module${state.moduleCount === 1 ? "" : "s"}`;
  document.querySelector<HTMLElement>("#command-count-detail")!.textContent = `${state.commandCount} command${state.commandCount === 1 ? "" : "s"} loaded.`;
  document.querySelector<HTMLElement>("#version")!.textContent = `v${state.version}`;
}

async function engineAction(label: string, work: () => Promise<EngineStatus>): Promise<void> {
  const message = document.querySelector<HTMLElement>("#engine-action-message")!;
  message.textContent = label;
  try {
    const status = await work();
    renderEngineStatus(status);
    message.textContent = "Done.";
  } catch (error) {
    message.textContent = error instanceof Error ? error.message : String(error);
  }
}

document.querySelector<HTMLButtonElement>("#engine-check")!.addEventListener("click", () => void engineAction("Checking registry…", () => window.bailey.checkEngineLatest()));
document.querySelector<HTMLButtonElement>("#engine-install")!.addEventListener("click", () => void engineAction("Installing WhatsApp engine…", async () => {
  const status = await window.bailey.getEngineStatus();
  return status.activeVersion ? window.bailey.installEngineVersion(status.activeVersion) : window.bailey.installDefaultEngine();
}));
document.querySelector<HTMLButtonElement>("#engine-update")!.addEventListener("click", () => void engineAction("Updating engine…", () => window.bailey.updateEngine()));
document.querySelector<HTMLButtonElement>("#engine-rollback")!.addEventListener("click", () => void engineAction("Rolling back…", () => window.bailey.rollbackEngine()));
document.querySelector<HTMLButtonElement>("#engine-start")!.addEventListener("click", () => void engineAction("Starting engine…", () => window.bailey.startEngine()));
document.querySelector<HTMLButtonElement>("#engine-stop")!.addEventListener("click", () => void engineAction("Stopping engine…", () => window.bailey.stopEngine()));
document.querySelector<HTMLButtonElement>("#pair-button")!.addEventListener("click", () => {
  const input = document.querySelector<HTMLInputElement>("#pair-phone")!;
  void engineAction("Requesting pairing code…", () => window.bailey.pairEngine(input.value));
});

window.bailey.onEngineStatus((status) => renderEngineStatus(status));

void Promise.all([
  renderDashboard(),
  renderModules(),
  renderConfiguration(),
  window.bailey.getEngineStatus().then(renderEngineStatus),
]);
