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
  getState(): Promise<{ runtime: string; whatsapp: string; moduleCount: number; version: string }>;
  getModules(): Promise<Array<{ id: string; name: string; version: string; description?: string; commands: Array<{ name: string; aliases: string[]; description: string }> }>>;
  getConfig(): Promise<{ definitions: ConfigDefinition[]; values: UiConfigValue[] }>;
  setConfig(key: string, value: unknown): Promise<{ ok: boolean }>;
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
    saveStatus.textContent = "Saved locally";
    saveStatus.className = "save-status";
  } catch (error) {
    saveStatus.textContent = error instanceof Error ? error.message : "Could not save";
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
    header.innerHTML = `<h2>${sectionName}</h2><p>Generated from the module schema.</p>`;
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

async function renderModules(): Promise<void> {
  const modules = await window.bailey.getModules();
  const root = document.querySelector<HTMLElement>("#module-list")!;
  root.replaceChildren();
  for (const module of modules) {
    const card = document.createElement("article");
    card.className = "module-card";
    const commands = module.commands.map((command) => {
      const aliases = command.aliases.length ? ` <small>${command.aliases.map((alias) => `.${alias}`).join(" · ")}</small>` : "";
      return `<span class="command-chip">.${command.name}${aliases}</span>`;
    }).join("");
    card.innerHTML = `<div class="module-heading"><div><h2>${module.name}</h2><p>${module.description ?? ""}</p></div><div class="module-meta"><span class="pill">${module.id}</span><span class="pill">v${module.version}</span></div></div><div class="command-list">${commands || "<span class=\"muted\">No commands registered</span>"}</div>`;
    root.append(card);
  }
}

function prettyState(value: string): string {
  return value.split("-").map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(" ");
}

function renderEngineStatus(status: EngineStatus): void {
  document.querySelector<HTMLElement>("#engine-package")!.textContent = status.packageName;
  document.querySelector<HTMLElement>("#engine-api")!.textContent = `Engine API v${status.apiVersion}`;
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
  installButton.textContent = installed ? "Reinstall current" : "Install Lia engine";
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
  document.querySelector<HTMLElement>("#module-count")!.textContent = String(state.moduleCount);
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
document.querySelector<HTMLButtonElement>("#engine-install")!.addEventListener("click", () => void engineAction("Installing Lia Baileys…", async () => {
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
