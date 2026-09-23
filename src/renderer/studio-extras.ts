const studioStyles = document.createElement("link");
studioStyles.rel = "stylesheet";
studioStyles.href = "./studio.css";
document.head.append(studioStyles);

interface ModuleCreateResult {
  ok: boolean;
  id: string;
  name: string;
  directory: string;
  entryFile: string;
  runtime: "python" | "javascript";
}

interface ModuleReloadResult {
  loaded: string[];
  errors: Array<{ folder: string; error: string }>;
}

interface NetworkLockStatus {
  mode: "normal" | "metered" | "bailey-only" | "temporary-unlock";
  baileyInternetAvailable: boolean;
  unlockUntil?: number;
  events: Array<{ at: number; action: string; detail?: string }>;
  dataUsage: { note: string; whatsappBytes: null; moduleServicesBytes: number; storageBytes: number; dependencyAndUpdateBytes: null; totalBytes: null };
}

interface NetworkPoliciesPayload {
  modules: Array<{ id: string; name: string }>;
  policies: Array<{ moduleId: string; allow: string[]; deny: string[] }>;
}

const bailey = (window as unknown as {
  bailey: {
    openModulesFolder(): Promise<{ ok: boolean; path: string }>;
    createModule(input: {
      id: string;
      name: string;
      description?: string;
      runtime: "python" | "javascript";
      firstCommand?: string;
      firstSection?: string;
      features?: Array<"events" | "jobs" | "storage" | "services" | "media" | "lifecycle">;
    }): Promise<ModuleCreateResult>;
    showModule(moduleId: string): Promise<{ ok: boolean; directory: string }>;
    reloadModules(): Promise<ModuleReloadResult>;
    detectModuleRuntimes(): Promise<Array<{ id: string; label: string; available: boolean; command: string; version?: string; detail?: string }>>;
    exportModulePackage(moduleId: string): Promise<{ ok: boolean; canceled?: boolean; path?: string }>;
    installModulePackage(): Promise<{ ok: boolean; canceled?: boolean; id?: string; name?: string }>;
    installModuleDependencies(moduleId: string): Promise<{ ok: boolean; installed: boolean; detail: string; output: string[] }>;
    listModuleWorkspace(moduleId: string): Promise<Array<{ path: string; type: "file" | "directory"; editable: boolean; size?: number }>>;
    readModuleWorkspaceFile(moduleId: string, path: string): Promise<{ moduleId: string; path: string; content: string }>;
    writeModuleWorkspaceFile(moduleId: string, path: string, content: string): Promise<{ ok: boolean; path: string }>;
    createModuleWorkspaceItem(moduleId: string, path: string, type: "file" | "directory"): Promise<{ ok: boolean; path: string }>;
    renameModuleWorkspaceItem(moduleId: string, from: string, to: string): Promise<{ ok: boolean; from: string; to: string }>;
    deleteModuleWorkspaceItem(moduleId: string, path: string): Promise<{ ok: boolean; path: string }>;
    getModuleRuntimeStatus(): Promise<Array<{ id: string; name: string; running: boolean; pid?: number; crashCount: number; restartCount: number; lastError?: string; capabilities: string[]; permissions: string[]; grantedPermissions: string[]; logs: string[] }>>;
    restartModule(moduleId: string): Promise<unknown>;
    setModulePermissions(moduleId: string, grants: string[]): Promise<unknown>;
    exportBackup(): Promise<{ ok: boolean; canceled?: boolean; path?: string }>;
    importBackup(): Promise<{ ok: boolean; canceled?: boolean; fileCount?: number }>;
    getStorageProfiles(): Promise<Array<{ name: string; provider: "local" | "s3" | "azure" | "gcs" | "supabase"; isDefault: boolean; config: Record<string, string | boolean>; secretFields: string[] }>>;
    saveStorageProfile(input: { name: string; provider: string; config: Record<string, string | boolean>; secrets: Record<string, string> }): Promise<unknown>;
    setDefaultStorageProfile(name: string): Promise<unknown>;
    deleteStorageProfile(name: string): Promise<unknown>;
    getNetworkLockStatus(): Promise<NetworkLockStatus>;
    setNetworkLockMode(mode: "normal" | "metered" | "bailey-only"): Promise<NetworkLockStatus>;
    temporaryNetworkUnlock(minutes: number): Promise<NetworkLockStatus>;
    getNetworkPolicies(): Promise<NetworkPoliciesPayload>;
    setNetworkPolicy(moduleId: string, allow: string[], deny: string[]): Promise<unknown>;
  };
}).bailey;

const returnView = sessionStorage.getItem("bailey-return-view");
if (returnView) {
  sessionStorage.removeItem("bailey-return-view");
  queueMicrotask(() => document.querySelector<HTMLButtonElement>(`.nav-item[data-view="${returnView}"]`)?.click());
}

const replyField = document.querySelector<HTMLTextAreaElement>("#command-reply")?.closest("label.field");
if (replyField && !document.querySelector("#command-reaction")) {
  const reactionField = document.createElement("label");
  reactionField.className = "field editor-span-2";

  const title = document.createElement("span");
  title.textContent = "Reaction (optional)";

  const input = document.createElement("input");
  input.id = "command-reaction";
  input.autocomplete = "off";
  input.placeholder = "❤️";
  input.maxLength = 32;

  const help = document.createElement("small");
  help.textContent = "If set, Bailey reacts to the triggering WhatsApp message before sending the reply.";

  reactionField.append(title, input, help);
  replyField.after(reactionField);
}

function slugify(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9.-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 64);
}

function reloadRendererTo(view: "studio" | "modules"): void {
  sessionStorage.setItem("bailey-return-view", view);
  window.location.reload();
}

function installModuleWizard(): void {
  const grid = document.querySelector<HTMLElement>("#view-studio .studio-grid");
  if (!grid || document.querySelector("#studio-create-module")) return;

  const card = document.createElement("article");
  card.className = "panel studio-card";
  card.innerHTML = `
    <div>
      <span class="studio-kicker">MODULE</span>
      <h2>Create a module</h2>
      <p>Generate a real Bailey module folder, manifest and starter worker without memorising the protocol first.</p>
    </div>
    <button class="primary-button" id="studio-create-module" type="button">Create module</button>
  `;
  grid.insertBefore(card, grid.children[1] ?? null);

  const dialog = document.createElement("dialog");
  dialog.id = "module-wizard";
  dialog.className = "command-editor";
  dialog.innerHTML = `
    <form id="module-wizard-form">
      <div class="dialog-header">
        <div>
          <p class="eyebrow">MODULE STUDIO</p>
          <h2>Create module</h2>
          <p>Bailey creates the folder, manifest and starter worker, then loads the module without restarting WhatsApp or the host.</p>
        </div>
        <button type="button" class="icon-button" id="module-wizard-close" aria-label="Close module wizard">×</button>
      </div>

      <div class="editor-grid">
        <label class="field">
          <span>Module name</span>
          <input id="module-name" autocomplete="off" required placeholder="Economy" />
          <small>The human-facing name shown in Modules and Configuration.</small>
        </label>

        <label class="field">
          <span>Module ID</span>
          <input id="module-id" autocomplete="off" required placeholder="economy" />
          <small>Lowercase letters, numbers, dots and hyphens only.</small>
        </label>

        <label class="field">
          <span>Runtime</span>
          <select id="module-runtime">
            <option value="javascript">JavaScript · Bailey embedded Node</option>
            <option value="python">Python · installed Python</option>
          </select>
          <small>JavaScript needs no separate Node install. Python uses the computer’s installed Python.</small>
        </label>

        <label class="field">
          <span>First command</span>
          <input id="module-command" autocomplete="off" value="hello" required />
          <small>Starter trigger without the configured command prefix.</small>
        </label>

        <label class="field editor-span-2">
          <span>Section</span>
          <input id="module-section" autocomplete="off" placeholder="General" />
          <small>This becomes the command’s initial menu section.</small>
        </label>

        <label class="field editor-span-2">
          <span>Description (optional)</span>
          <input id="module-description" autocomplete="off" placeholder="Economy commands and storage." />
        </label>

        <label class="field editor-span-2">
          <span>Host features</span>
          <div class="action-row" id="module-features">
            <label><input type="checkbox" value="events" /> Events</label>
            <label><input type="checkbox" value="jobs" /> Jobs</label>
            <label><input type="checkbox" value="storage" /> Local data</label>
            <label><input type="checkbox" value="services" /> Host services</label>
            <label><input type="checkbox" value="media" /> Media</label>
            <label><input type="checkbox" value="lifecycle" /> Lifecycle</label>
          </div>
          <small>These declare capabilities only. Privileged host operations still require explicit permissions in the module manifest and your approval.</small>
        </label>
      </div>

      <p id="module-wizard-status" class="editor-error" role="alert"></p>
      <div class="dialog-actions">
        <div><button type="button" class="secondary-button" id="module-open-created" hidden>Open module folder</button></div>
        <div class="dialog-actions-right">
          <button type="button" class="secondary-button" id="module-wizard-cancel">Cancel</button>
          <button type="submit" class="primary-button" id="module-wizard-create">Create module</button>
        </div>
      </div>
    </form>
  `;
  document.body.append(dialog);

  const form = dialog.querySelector<HTMLFormElement>("#module-wizard-form")!;
  const nameInput = dialog.querySelector<HTMLInputElement>("#module-name")!;
  const idInput = dialog.querySelector<HTMLInputElement>("#module-id")!;
  const sectionInput = dialog.querySelector<HTMLInputElement>("#module-section")!;
  const status = dialog.querySelector<HTMLElement>("#module-wizard-status")!;
  const createButton = dialog.querySelector<HTMLButtonElement>("#module-wizard-create")!;
  const openCreated = dialog.querySelector<HTMLButtonElement>("#module-open-created")!;
  let idTouched = false;
  let createdModuleId = "";

  idInput.addEventListener("input", () => { idTouched = true; });
  nameInput.addEventListener("input", () => {
    if (!idTouched) idInput.value = slugify(nameInput.value);
    if (!sectionInput.value.trim()) sectionInput.placeholder = nameInput.value.trim() || "General";
  });

  const close = () => dialog.close();
  dialog.querySelector<HTMLButtonElement>("#module-wizard-close")!.addEventListener("click", close);
  dialog.querySelector<HTMLButtonElement>("#module-wizard-cancel")!.addEventListener("click", close);

  document.querySelector<HTMLButtonElement>("#studio-create-module")!.addEventListener("click", () => {
    form.reset();
    idTouched = false;
    createdModuleId = "";
    idInput.value = "";
    status.textContent = "";
    status.className = "editor-error";
    openCreated.hidden = true;
    createButton.disabled = false;
    createButton.textContent = "Create module";
    dialog.showModal();
    nameInput.focus();
  });

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    status.textContent = "Creating module…";
    status.className = "save-status saving";
    createButton.disabled = true;

    const runtime = dialog.querySelector<HTMLSelectElement>("#module-runtime")!.value as "python" | "javascript";
    void bailey.createModule({
      id: idInput.value,
      name: nameInput.value,
      description: dialog.querySelector<HTMLInputElement>("#module-description")!.value,
      runtime,
      firstCommand: dialog.querySelector<HTMLInputElement>("#module-command")!.value,
      firstSection: sectionInput.value || nameInput.value,
      features: [...dialog.querySelectorAll<HTMLInputElement>("#module-features input:checked")].map((input) => input.value as "events" | "jobs" | "storage" | "services" | "media" | "lifecycle"),
    }).then(async (result) => {
      createdModuleId = result.id;
      status.textContent = `Created ${result.name}. Loading module…`;
      status.className = "save-status saving";
      openCreated.hidden = false;
      const reload = await bailey.reloadModules();
      const failure = reload.errors.find((item) => item.folder === result.id);
      if (failure) throw new Error(`Module was created but could not load: ${failure.error}`);
      reloadRendererTo("modules");
    }).catch((error) => {
      status.textContent = error instanceof Error ? error.message : String(error);
      status.className = "editor-error";
      createButton.disabled = false;
      createButton.textContent = createdModuleId ? "Created" : "Create module";
    });
  });

  openCreated.addEventListener("click", () => {
    if (!createdModuleId) return;
    void bailey.showModule(createdModuleId).catch((error) => {
      status.textContent = error instanceof Error ? error.message : String(error);
      status.className = "editor-error";
    });
  });
}

installModuleWizard();

const guideNote = document.querySelector<HTMLElement>(".module-guide-actions span");
if (guideNote) guideNote.textContent = "After editing a module, use Reload modules. Bailey Host and WhatsApp stay running.";

const modulesFolderButton = document.querySelector<HTMLButtonElement>("#studio-open-modules-folder");
if (modulesFolderButton && !document.querySelector("#studio-reload-modules")) {
  const reloadButton = document.createElement("button");
  reloadButton.type = "button";
  reloadButton.className = "secondary-button";
  reloadButton.id = "studio-reload-modules";
  reloadButton.textContent = "Reload modules";
  modulesFolderButton.after(reloadButton);

  reloadButton.addEventListener("click", () => {
    reloadButton.disabled = true;
    reloadButton.textContent = "Reloading…";
    void bailey.reloadModules().then((result) => {
      if (result.errors.length) {
        const summary = result.errors.map((item) => `${item.folder}: ${item.error}`).join("\n");
        window.alert(`Some modules could not load:\n${summary}`);
      }
      reloadRendererTo("studio");
    }).catch((error) => {
      window.alert(error instanceof Error ? error.message : String(error));
      reloadButton.disabled = false;
      reloadButton.textContent = "Reload modules";
    });
  });
}

modulesFolderButton?.addEventListener("click", () => {
  modulesFolderButton.disabled = true;
  void bailey.openModulesFolder().catch((error) => {
    window.alert(error instanceof Error ? error.message : String(error));
  }).finally(() => {
    modulesFolderButton.disabled = false;
  });
});

const fileDialog = document.querySelector<HTMLDialogElement>("#file-editor");
const fileStatus = document.querySelector<HTMLElement>("#file-save-status");
const fileSave = document.querySelector<HTMLButtonElement>("#file-save");

function hasUnsavedFileChanges(): boolean {
  return fileDialog?.open === true && fileStatus?.textContent === "Unsaved changes";
}

function confirmDiscard(): boolean {
  return !hasUnsavedFileChanges() || window.confirm("Discard unsaved changes to this file?");
}

for (const selector of ["#file-editor-close", "#file-cancel"]) {
  document.querySelector<HTMLButtonElement>(selector)?.addEventListener("click", (event) => {
    if (confirmDiscard()) return;
    event.preventDefault();
    event.stopImmediatePropagation();
  }, { capture: true });
}

fileDialog?.addEventListener("cancel", (event) => {
  if (!confirmDiscard()) event.preventDefault();
});

document.addEventListener("keydown", (event) => {
  if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== "s") return;
  if (fileDialog?.open !== true) return;
  event.preventDefault();
  fileSave?.click();
});


function installHostTools(): void {
  const studioGrid = document.querySelector<HTMLElement>("#view-studio .studio-grid");
  if (studioGrid && !document.querySelector("#host-runtime-card")) {
    const runtimeCard = document.createElement("article");
    runtimeCard.id = "host-runtime-card";
    runtimeCard.className = "panel studio-card studio-card-wide";
    runtimeCard.innerHTML = `
      <div>
        <span class="studio-kicker">RUNTIMES</span>
        <h2>Module runtimes & health</h2>
        <p>Bailey embedded Node is built in. External runtimes are detected from this computer; module workers expose health, crash and restart information here.</p>
      </div>
      <div class="action-row">
        <button class="secondary-button" id="runtime-refresh" type="button">Check runtimes</button>
        <button class="secondary-button" id="module-install-package" type="button">Install module package…</button>
      </div>
      <div id="runtime-list" class="stack"></div>
      <div id="module-runtime-list" class="stack"></div>
    `;
    studioGrid.append(runtimeCard);

    const runtimeList = runtimeCard.querySelector<HTMLElement>("#runtime-list")!;
    const moduleList = runtimeCard.querySelector<HTMLElement>("#module-runtime-list")!;
    const refreshButton = runtimeCard.querySelector<HTMLButtonElement>("#runtime-refresh")!;
    const installButton = runtimeCard.querySelector<HTMLButtonElement>("#module-install-package")!;

    const refresh = async () => {
      refreshButton.disabled = true;
      refreshButton.textContent = "Checking…";
      try {
        const [runtimes, modules] = await Promise.all([
          bailey.detectModuleRuntimes(),
          bailey.getModuleRuntimeStatus(),
        ]);
        runtimeList.replaceChildren(...runtimes.map((runtime) => {
          const row = document.createElement("div");
          row.className = "module-guide-actions";
          const text = document.createElement("span");
          text.textContent = `${runtime.label}: ${runtime.available ? runtime.version ?? "Available" : runtime.detail ?? "Not found"}`;
          const pill = document.createElement("span");
          pill.className = "pill";
          pill.textContent = runtime.available ? runtime.command : "Missing";
          row.append(text, pill);
          return row;
        }));

        moduleList.replaceChildren(...modules.map((module) => {
          const row = document.createElement("div");
          row.className = "module-guide-actions";
          const copy = document.createElement("div");
          const title = document.createElement("strong");
          title.textContent = module.name;
          const meta = document.createElement("p");
          meta.className = "muted";
          meta.textContent = module.running
            ? `Running · PID ${module.pid ?? "?"} · restarts ${module.restartCount}`
            : `Stopped · crashes ${module.crashCount}${module.lastError ? ` · ${module.lastError}` : ""}`;
          copy.append(title, meta);

          const actions = document.createElement("div");
          actions.className = "action-row";
          const exportButton = document.createElement("button");
          exportButton.type = "button";
          exportButton.className = "secondary-button";
          exportButton.textContent = "Export package";
          exportButton.addEventListener("click", () => {
            exportButton.disabled = true;
            void bailey.exportModulePackage(module.id).finally(() => { exportButton.disabled = false; });
          });
          const restartButton = document.createElement("button");
          restartButton.type = "button";
          restartButton.className = "secondary-button";
          restartButton.textContent = "Restart";
          restartButton.addEventListener("click", () => {
            restartButton.disabled = true;
            void bailey.restartModule(module.id).then(refresh).finally(() => { restartButton.disabled = false; });
          });
          const depsButton = document.createElement("button");
          depsButton.type = "button";
          depsButton.className = "secondary-button";
          depsButton.textContent = "Install deps";
          depsButton.addEventListener("click", () => {
            depsButton.disabled = true;
            depsButton.textContent = "Installing…";
            void bailey.installModuleDependencies(module.id).then((result) => {
              window.alert(result.detail + (result.output.length ? "\n\n" + result.output.slice(-12).join("\n") : ""));
              return refresh();
            }).catch((error) => {
              window.alert(error instanceof Error ? error.message : String(error));
            }).finally(() => {
              depsButton.disabled = false;
              depsButton.textContent = "Install deps";
            });
          });
          actions.append(exportButton, depsButton, restartButton);

          const details = document.createElement("details");
          const summary = document.createElement("summary");
          summary.textContent = "Logs & permissions";
          const pre = document.createElement("pre");
          pre.className = "manifest-example";
          pre.textContent = [
            `Capabilities: ${module.capabilities.join(", ") || "none"}`,
            "",
            ...module.logs.slice(-30),
          ].join("\n");

          const permissionWrap = document.createElement("div");
          permissionWrap.className = "stack";
          if (module.permissions.length) {
            const permissionTitle = document.createElement("strong");
            permissionTitle.textContent = "Requested permissions · checked means granted";
            permissionWrap.append(permissionTitle);
            const permissionNote = document.createElement("p");
            permissionNote.className = "muted";
            permissionNote.textContent = "Manifest entries only ask. Bailey grants only the entries you check here. WhatsApp actions and host services require both a request and your grant.";
            permissionWrap.append(permissionNote);
            for (const permission of module.permissions) {
              const label = document.createElement("label");
              label.className = "field";
              const input = document.createElement("input");
              input.type = "checkbox";
              input.value = permission;
              input.checked = module.grantedPermissions.includes(permission);
              const text = document.createElement("span");
              text.textContent = permission;
              label.append(input, text);
              permissionWrap.append(label);
            }
            const savePermissions = document.createElement("button");
            savePermissions.type = "button";
            savePermissions.className = "secondary-button";
            savePermissions.textContent = "Save permissions";
            savePermissions.addEventListener("click", () => {
              const grants = [...permissionWrap.querySelectorAll<HTMLInputElement>('input[type="checkbox"]:checked')].map((input) => input.value);
              savePermissions.disabled = true;
              void bailey.setModulePermissions(module.id, grants).then(refresh).finally(() => { savePermissions.disabled = false; });
            });
            permissionWrap.append(savePermissions);
          } else {
            const none = document.createElement("span");
            none.className = "muted";
            none.textContent = "This module currently requests no Bailey host permissions. Module workers are trusted local processes; use network.direct only when needed, or keep Bailey Only active to block direct outbound connections.";
            permissionWrap.append(none);
          }
          details.append(summary, permissionWrap, pre);
          row.append(copy, actions, details);
          return row;
        }));
      } catch (error) {
        runtimeList.textContent = error instanceof Error ? error.message : String(error);
      } finally {
        refreshButton.disabled = false;
        refreshButton.textContent = "Check runtimes";
      }
    };

    refreshButton.addEventListener("click", () => void refresh());
    installButton.addEventListener("click", () => {
      installButton.disabled = true;
      void bailey.installModulePackage().then(async (result) => {
        if (result.ok) {
          const reload = await bailey.reloadModules();
          if (reload.errors.length) throw new Error(reload.errors.map((item) => `${item.folder}: ${item.error}`).join("\n"));
          await refresh();
        }
      }).catch((error) => window.alert(error instanceof Error ? error.message : String(error)))
        .finally(() => { installButton.disabled = false; });
    });
    void refresh();
  }

  const configRoot = document.querySelector<HTMLElement>("#view-configuration .stack");
  if (configRoot && !document.querySelector("#backup-card")) {
    const card = document.createElement("article");
    card.id = "backup-card";
    card.className = "panel";
    card.innerHTML = `
      <div class="panel-heading">
        <div>
          <h2>Backup & restore</h2>
          <p>Back up configuration, commands, module code/data and provider-neutral storage. WhatsApp session/auth files and engine binaries are deliberately excluded.</p>
        </div>
        <span class="pill">No auth keys</span>
      </div>
      <div class="action-row">
        <button class="secondary-button" id="backup-export" type="button">Export backup…</button>
        <button class="secondary-button" id="backup-import" type="button">Restore backup…</button>
      </div>
      <p class="muted">Secret settings stay protected by Windows secure storage and may need to be entered again when a backup is moved to another computer.</p>
    `;
    configRoot.append(card);
    card.querySelector<HTMLButtonElement>("#backup-export")!.addEventListener("click", (event) => {
      const button = event.currentTarget as HTMLButtonElement;
      button.disabled = true;
      void bailey.exportBackup().catch((error) => window.alert(error instanceof Error ? error.message : String(error)))
        .finally(() => { button.disabled = false; });
    });
    card.querySelector<HTMLButtonElement>("#backup-import")!.addEventListener("click", (event) => {
      if (!window.confirm("Restore this Bailey backup? Bailey Host will restart after the files are restored.")) return;
      const button = event.currentTarget as HTMLButtonElement;
      button.disabled = true;
      void bailey.importBackup().catch((error) => {
        window.alert(error instanceof Error ? error.message : String(error));
        button.disabled = false;
      });
    });
  }
}

installHostTools();


function installNetworkLockUi(): void {
  const configRoot = document.querySelector<HTMLElement>("#view-configuration .stack");
  if (!configRoot || document.querySelector("#network-lock-card")) return;
  const card = document.createElement("article");
  card.id = "network-lock-card";
  card.className = "panel";
  card.innerHTML = `
    <div class="panel-heading">
      <div>
        <h2>Network / Data Saver</h2>
        <p>Choose how Bailey behaves on a limited hotspot or mobile connection.</p>
      </div>
      <span id="network-lock-mode" class="pill">Checking…</span>
    </div>
    <div id="network-lock-summary" class="stack"></div>
    <div class="action-row" style="flex-wrap:wrap">
      <button class="secondary-button" id="network-mode-normal" type="button">Normal</button>
      <button class="secondary-button" id="network-mode-metered" type="button">Metered / Data Saver</button>
      <button class="primary-button" id="network-mode-bailey" type="button">Bailey Only</button>
      <button class="danger-quiet-button" id="network-emergency-disable" type="button">Emergency Disable</button>
    </div>
    <div class="action-row" style="margin-top:12px">
      <label class="field"><span>Temporary unlock</span><select id="network-unlock-duration"><option value="5">5 minutes</option><option value="15">15 minutes</option><option value="30">30 minutes</option><option value="60">1 hour</option></select></label>
      <button class="secondary-button" id="network-unlock" type="button">Unlock laptop internet</button>
    </div>
    <details style="margin-top:16px">
      <summary>Recent Network Lock actions</summary>
      <pre id="network-lock-events" class="manifest-example"></pre>
    </details>
  `;
  configRoot.prepend(card);
  const modeLabel = card.querySelector<HTMLElement>("#network-lock-mode")!;
  const summary = card.querySelector<HTMLElement>("#network-lock-summary")!;
  const events = card.querySelector<HTMLElement>("#network-lock-events")!;
  const buttons = [...card.querySelectorAll<HTMLButtonElement>("button")];
  const modeText: Record<NetworkLockStatus["mode"], string> = {
    normal: "Normal",
    metered: "Metered / Data Saver",
    "bailey-only": "Bailey Only · laptop internet restricted",
    "temporary-unlock": "Temporary Unlock",
  };
  const dataLabel = (bytes: number | null) => bytes === null ? "Not measured" : `${(bytes / (1024 * 1024)).toFixed(2)} MB`;

  const render = (state: NetworkLockStatus) => {
    modeLabel.textContent = modeText[state.mode];
    const locked = state.mode === "bailey-only";
    card.style.borderColor = locked ? "#92763b" : "";
    card.style.boxShadow = locked ? "0 0 0 1px rgba(235,190,75,.18)" : "";
    const connectivity = state.baileyInternetAvailable
      ? "Bailey internet: available"
      : "Bailey is allowed through Windows Firewall; other apps are restricted.";
    let unlock = "";
    if (state.mode === "temporary-unlock" && state.unlockUntil) {
      const seconds = Math.max(0, Math.ceil((state.unlockUntil - Date.now()) / 1000));
      unlock = `<div class="setting-row"><div class="setting-copy"><div class="setting-title"><strong>Temporary unlock remaining</strong></div><p>${Math.floor(seconds / 60)}m ${seconds % 60}s · will return to Bailey Only automatically</p></div></div>`;
    }
    const meterNote = state.mode === "metered"
      ? "Windows networking is unchanged. For Windows metered behavior, open Settings → Network & Internet and enable Metered connection on the active Wi-Fi or mobile profile. Bailey avoids background update checks unless you start them."
      : locked
        ? "Bailey Host and its WhatsApp engine can connect; Windows DNS and DHCP are allowed. Dependency installation, engine updates and cloud actions run through Bailey Host. Use Temporary Unlock for ordinary laptop browsing."
        : "Windows networking is unchanged in Normal mode.";
    summary.innerHTML = `
      <div class="setting-row"><div class="setting-copy"><div class="setting-title"><strong>Current mode</strong></div><p>${connectivity}</p></div></div>
      ${unlock}
      <div class="setting-row"><div class="setting-copy"><div class="setting-title"><strong>Today · Bailey payloads observed</strong></div><p>WhatsApp: ${dataLabel(state.dataUsage.whatsappBytes)} · Storage: ${dataLabel(state.dataUsage.storageBytes)} · Modules/services: ${dataLabel(state.dataUsage.moduleServicesBytes)} · Dependency/update: ${dataLabel(state.dataUsage.dependencyAndUpdateBytes)} · Total Bailey traffic: Not measured</p><small>${state.dataUsage.note}</small></div></div>
      <p class="muted">${meterNote}</p>
    `;
    events.textContent = state.events.length
      ? state.events.slice().reverse().map((item) => `${new Date(item.at).toLocaleString()}  ${item.action}${item.detail ? ` · ${item.detail}` : ""}`).join("\n")
      : "No recent Network Lock actions.";
  };

  const refresh = async () => {
    try { render(await bailey.getNetworkLockStatus()); }
    catch (error) { modeLabel.textContent = "Status unavailable"; summary.textContent = error instanceof Error ? error.message : String(error); }
  };
  const act = async (button: HTMLButtonElement, work: () => Promise<NetworkLockStatus>) => {
    buttons.forEach((item) => { item.disabled = true; });
    button.textContent = "Applying…";
    try { render(await work()); }
    catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      await refresh();
      summary.insertAdjacentHTML("afterbegin", "<p class='save-status error'></p>");
      summary.querySelector<HTMLElement>(".save-status.error")!.textContent = message;
    } finally {
      button.textContent = button.id === "network-mode-normal" ? "Normal"
        : button.id === "network-mode-metered" ? "Metered / Data Saver"
          : button.id === "network-mode-bailey" ? "Bailey Only"
            : button.id === "network-unlock" ? "Unlock laptop internet" : "Emergency Disable";
      buttons.forEach((item) => { item.disabled = false; });
    }
  };
  card.querySelector<HTMLButtonElement>("#network-mode-normal")!.addEventListener("click", (event) => {
    const button = event.currentTarget as HTMLButtonElement;
    void act(button, () => bailey.setNetworkLockMode("normal"));
  });
  card.querySelector<HTMLButtonElement>("#network-emergency-disable")!.addEventListener("click", (event) => {
    const button = event.currentTarget as HTMLButtonElement;
    void act(button, () => bailey.setNetworkLockMode("normal"));
  });
  card.querySelector<HTMLButtonElement>("#network-mode-metered")!.addEventListener("click", (event) => {
    const button = event.currentTarget as HTMLButtonElement;
    void act(button, () => bailey.setNetworkLockMode("metered"));
  });
  card.querySelector<HTMLButtonElement>("#network-mode-bailey")!.addEventListener("click", (event) => {
    const button = event.currentTarget as HTMLButtonElement;
    void act(button, () => bailey.setNetworkLockMode("bailey-only"));
  });
  card.querySelector<HTMLButtonElement>("#network-unlock")!.addEventListener("click", (event) => {
    const button = event.currentTarget as HTMLButtonElement;
    const minutes = Number(card.querySelector<HTMLSelectElement>("#network-unlock-duration")!.value);
    void act(button, () => bailey.temporaryNetworkUnlock(minutes));
  });
  void refresh();
  window.setInterval(() => { void bailey.getNetworkLockStatus().then(render).catch(() => {}); }, 15_000);
}

installNetworkLockUi();


function installNetworkPoliciesUi(): void {
  const configRoot = document.querySelector<HTMLElement>("#view-configuration .stack");
  if (!configRoot || document.querySelector("#network-policies-card")) return;
  const card = document.createElement("article");
  card.id = "network-policies-card";
  card.className = "panel";
  card.innerHTML = `
    <div class="panel-heading"><div><h2>Module network access</h2><p>Network service calls stay inside Bailey and use this module's HTTPS domain allowlist. Direct module networking is a separate permission request.</p></div></div>
    <div class="editor-grid">
      <label class="field"><span>Module</span><select id="network-policy-module"></select></label>
      <span class="muted">Saving applies immediately; no module restart is needed.</span>
      <label class="field editor-span-2"><span>Allowed domains</span><textarea id="network-policy-allow" rows="3" placeholder="api.example.com, cdn.example.com"></textarea><small>Domain entries also match subdomains. The host network.http service only supports HTTPS public domains.</small></label>
      <label class="field editor-span-2"><span>Denied domains</span><textarea id="network-policy-deny" rows="2" placeholder="private.example.com"></textarea><small>Denies override allowed entries.</small></label>
    </div>
    <p id="network-policy-status" class="save-status"></p>
    <button class="primary-button" id="network-policy-save" type="button">Save domain policy</button>
  `;
  configRoot.append(card);
  const moduleSelect = card.querySelector<HTMLSelectElement>("#network-policy-module")!;
  const allowInput = card.querySelector<HTMLTextAreaElement>("#network-policy-allow")!;
  const denyInput = card.querySelector<HTMLTextAreaElement>("#network-policy-deny")!;
  const status = card.querySelector<HTMLElement>("#network-policy-status")!;
  const split = (value: string) => value.split(/[\s,;]+/).map((item) => item.trim()).filter(Boolean);
  let payload: NetworkPoliciesPayload = { modules: [], policies: [] };
  const render = () => {
    const policy = payload.policies.find((item) => item.moduleId === moduleSelect.value);
    allowInput.value = policy?.allow.join("\n") ?? "";
    denyInput.value = policy?.deny.join("\n") ?? "";
  };
  const refresh = async () => {
    payload = await bailey.getNetworkPolicies();
    const current = moduleSelect.value;
    moduleSelect.replaceChildren(...payload.modules.map((module) => {
      const option = document.createElement("option");
      option.value = module.id;
      option.textContent = module.name;
      return option;
    }));
    if (payload.modules.some((module) => module.id === current)) moduleSelect.value = current;
    render();
  };
  moduleSelect.addEventListener("change", render);
  card.querySelector<HTMLButtonElement>("#network-policy-save")!.addEventListener("click", async (event) => {
    const button = event.currentTarget as HTMLButtonElement;
    if (!moduleSelect.value) { status.textContent = "Load a module first."; status.className = "save-status error"; return; }
    button.disabled = true;
    status.textContent = "Saving…";
    status.className = "save-status saving";
    try {
      const allow = split(allowInput.value);
      const deny = split(denyInput.value);
      await bailey.setNetworkPolicy(moduleSelect.value, allow, deny);
      payload.policies = payload.policies.filter((item) => item.moduleId !== moduleSelect.value);
      payload.policies.push({ moduleId: moduleSelect.value, allow, deny });
      status.textContent = "Saved.";
      status.className = "save-status";
    } catch (error) {
      status.textContent = error instanceof Error ? error.message : String(error);
      status.className = "save-status error";
    } finally { button.disabled = false; }
  });
  void refresh().catch((error) => { status.textContent = error instanceof Error ? error.message : String(error); });
}

installNetworkPoliciesUi();


function installStorageProfilesUi(): void {
  const configRoot = document.querySelector<HTMLElement>("#view-configuration .stack");
  if (!configRoot || document.querySelector("#storage-profiles-card")) return;

  const card = document.createElement("article");
  card.id = "storage-profiles-card";
  card.className = "panel";
  card.innerHTML = `
    <div class="panel-heading">
      <div>
        <h2>Storage profiles</h2>
        <p>Modules call Bailey's provider-neutral storage service. Change providers here without changing module code.</p>
      </div>
      <button class="secondary-button" id="storage-add-profile" type="button">Add profile</button>
    </div>
    <div id="storage-profile-list" class="stack"></div>
  `;
  configRoot.prepend(card);

  const dialog = document.createElement("dialog");
  dialog.className = "command-editor";
  dialog.id = "storage-profile-dialog";
  dialog.innerHTML = `
    <form id="storage-profile-form">
      <div class="dialog-header">
        <div>
          <p class="eyebrow">HOST STORAGE</p>
          <h2>Storage profile</h2>
          <p>Credentials are encrypted with the operating system's secure storage and are never sent to modules.</p>
        </div>
        <button type="button" class="icon-button" id="storage-profile-close" aria-label="Close">×</button>
      </div>
      <div class="editor-grid">
        <label class="field">
          <span>Profile name</span>
          <input id="storage-profile-name" required placeholder="media" />
          <small>Modules can request this profile by name.</small>
        </label>
        <label class="field">
          <span>Provider</span>
          <select id="storage-profile-provider">
            <option value="local">Local disk</option>
            <option value="s3">S3-compatible · AWS / R2 / B2 / MinIO</option>
            <option value="azure">Azure Blob Storage</option>
            <option value="gcs">Google Cloud Storage</option>
            <option value="supabase">Supabase Storage</option>
          </select>
        </label>
        <div id="storage-profile-fields" class="editor-span-2 editor-grid"></div>
      </div>
      <p id="storage-profile-error" class="editor-error"></p>
      <div class="dialog-actions">
        <div></div>
        <div class="dialog-actions-right">
          <button type="button" class="secondary-button" id="storage-profile-cancel">Cancel</button>
          <button type="submit" class="primary-button">Save profile</button>
        </div>
      </div>
    </form>
  `;
  document.body.append(dialog);

  const providerSelect = dialog.querySelector<HTMLSelectElement>("#storage-profile-provider")!;
  const fields = dialog.querySelector<HTMLElement>("#storage-profile-fields")!;
  const nameInput = dialog.querySelector<HTMLInputElement>("#storage-profile-name")!;
  const error = dialog.querySelector<HTMLElement>("#storage-profile-error")!;
  const list = card.querySelector<HTMLElement>("#storage-profile-list")!;

  const definitions: Record<string, Array<{ key: string; label: string; secret?: boolean; toggle?: boolean; placeholder?: string }>> = {
    local: [],
    s3: [
      { key: "bucket", label: "Bucket" },
      { key: "region", label: "Region", placeholder: "auto / us-east-1" },
      { key: "endpoint", label: "Endpoint (optional)", placeholder: "R2, B2 or MinIO endpoint" },
      { key: "forcePathStyle", label: "Force path-style URLs", toggle: true },
      { key: "accessKeyId", label: "Access key ID", secret: true },
      { key: "secretAccessKey", label: "Secret access key", secret: true },
      { key: "sessionToken", label: "Session token (optional)", secret: true },
    ],
    azure: [
      { key: "accountName", label: "Storage account name" },
      { key: "container", label: "Container" },
      { key: "accountKey", label: "Account key", secret: true },
    ],
    gcs: [
      { key: "projectId", label: "Project ID" },
      { key: "clientEmail", label: "Service-account client email" },
      { key: "bucket", label: "Bucket" },
      { key: "privateKey", label: "Service-account private key", secret: true },
    ],
    supabase: [
      { key: "url", label: "Project URL" },
      { key: "bucket", label: "Bucket" },
      { key: "serviceKey", label: "Service role / storage key", secret: true },
    ],
  };

  let editing: Awaited<ReturnType<typeof bailey.getStorageProfiles>>[number] | undefined;

  const renderFields = () => {
    fields.replaceChildren();
    for (const def of definitions[providerSelect.value] ?? []) {
      const label = document.createElement("label");
      label.className = "field";
      const title = document.createElement("span");
      title.textContent = def.label;
      let input: HTMLInputElement;
      input = document.createElement("input");
      input.dataset.storageKey = def.key;
      if (def.toggle) {
        input.type = "checkbox";
        input.checked = Boolean(editing?.config[def.key]);
      } else {
        input.type = def.secret ? "password" : "text";
        input.placeholder = def.secret && editing?.secretFields.includes(def.key) ? "Configured · leave blank to keep" : def.placeholder ?? "";
        if (!def.secret && editing) input.value = String(editing.config[def.key] ?? "");
      }
      label.append(title, input);
      fields.append(label);
    }
  };

  const openEditor = (profile?: typeof editing) => {
    editing = profile;
    nameInput.value = profile?.name ?? "";
    nameInput.disabled = Boolean(profile);
    providerSelect.value = profile?.provider ?? "local";
    error.textContent = "";
    renderFields();
    dialog.showModal();
  };

  const refresh = async () => {
    const profiles = await bailey.getStorageProfiles();
    list.replaceChildren(...profiles.map((profile) => {
      const row = document.createElement("div");
      row.className = "module-guide-actions";
      const copy = document.createElement("div");
      const title = document.createElement("strong");
      title.textContent = profile.name;
      const meta = document.createElement("p");
      meta.className = "muted";
      meta.textContent = `${profile.provider.toUpperCase()}${profile.isDefault ? " · Default" : ""}`;
      copy.append(title, meta);
      const actions = document.createElement("div");
      actions.className = "action-row";
      const edit = document.createElement("button");
      edit.className = "secondary-button";
      edit.type = "button";
      edit.textContent = "Edit";
      edit.addEventListener("click", () => openEditor(profile));
      actions.append(edit);
      if (!profile.isDefault) {
        const use = document.createElement("button");
        use.className = "secondary-button";
        use.type = "button";
        use.textContent = "Make default";
        use.addEventListener("click", () => void bailey.setDefaultStorageProfile(profile.name).then(refresh));
        const remove = document.createElement("button");
        remove.className = "danger-quiet-button";
        remove.type = "button";
        remove.textContent = "Delete";
        remove.addEventListener("click", () => {
          if (window.confirm(`Delete storage profile ${profile.name}? Stored cloud objects are not deleted.`)) {
            void bailey.deleteStorageProfile(profile.name).then(refresh);
          }
        });
        actions.append(use, remove);
      }
      row.append(copy, actions);
      return row;
    }));
  };

  providerSelect.addEventListener("change", () => {
    if (editing && editing.provider !== providerSelect.value) editing = undefined;
    renderFields();
  });
  card.querySelector<HTMLButtonElement>("#storage-add-profile")!.addEventListener("click", () => openEditor());
  dialog.querySelector<HTMLButtonElement>("#storage-profile-close")!.addEventListener("click", () => dialog.close());
  dialog.querySelector<HTMLButtonElement>("#storage-profile-cancel")!.addEventListener("click", () => dialog.close());

  dialog.querySelector<HTMLFormElement>("#storage-profile-form")!.addEventListener("submit", (event) => {
    event.preventDefault();
    const config: Record<string, string | boolean> = {};
    const secrets: Record<string, string> = {};
    for (const input of fields.querySelectorAll<HTMLInputElement>("input[data-storage-key]")) {
      const key = input.dataset.storageKey!;
      const def = (definitions[providerSelect.value] ?? []).find((item) => item.key === key)!;
      if (def.toggle) config[key] = input.checked;
      else if (def.secret) secrets[key] = input.value;
      else config[key] = input.value;
    }
    error.textContent = "Saving…";
    void bailey.saveStorageProfile({
      name: nameInput.value,
      provider: providerSelect.value,
      config,
      secrets,
    }).then(async () => {
      dialog.close();
      await refresh();
    }).catch((reason) => {
      error.textContent = reason instanceof Error ? reason.message : String(reason);
    });
  });
  void refresh();
}

installStorageProfilesUi();


function installWorkspaceUi(): void {
  const studioGrid = document.querySelector<HTMLElement>("#view-studio .studio-grid");
  if (!studioGrid || document.querySelector("#module-workspace-card")) return;

  const card = document.createElement("article");
  card.id = "module-workspace-card";
  card.className = "panel studio-card studio-card-wide";
  card.innerHTML =
    "<div class='panel-heading'>" +
      "<div><span class='studio-kicker'>WORKSPACE</span><h2>Module files</h2><p>Browse and edit module source without leaving Bailey. Runtime folders and module data stay hidden.</p></div>" +
      "<div class='action-row'><select id='workspace-module'></select><button class='secondary-button' id='workspace-refresh' type='button'>Refresh</button></div>" +
    "</div>" +
    "<div class='action-row'>" +
      "<button class='secondary-button' id='workspace-new-file' type='button'>New file</button>" +
      "<button class='secondary-button' id='workspace-new-folder' type='button'>New folder</button>" +
      "<button class='secondary-button' id='workspace-rename' type='button'>Rename</button>" +
      "<button class='danger-quiet-button' id='workspace-delete' type='button'>Delete</button>" +
    "</div>" +
    "<div style='display:grid;grid-template-columns:minmax(180px,0.34fr) minmax(0,1fr);gap:16px;min-height:440px'>" +
      "<div class='stack' style='min-width:0'><div id='workspace-tree' style='overflow:auto;max-height:560px'></div></div>" +
      "<div class='stack' style='min-width:0'>" +
        "<div id='workspace-tabs' class='action-row' style='overflow-x:auto;flex-wrap:nowrap'></div>" +
        "<div class='file-editor-meta'><span class='pill' id='workspace-language'>TEXT</span><span class='muted' id='workspace-path'>Select a file</span></div>" +
        "<textarea id='workspace-editor' class='file-content' spellcheck='false' disabled placeholder='Choose an editable file from the tree.' style='min-height:360px'></textarea>" +
        "<div class='dialog-actions'><span id='workspace-save-status' class='save-status'>No file open</span><button class='primary-button' id='workspace-save' type='button' disabled>Save</button></div>" +
      "</div>" +
    "</div>";
  studioGrid.append(card);

  const moduleSelect = card.querySelector<HTMLSelectElement>("#workspace-module")!;
  const tree = card.querySelector<HTMLElement>("#workspace-tree")!;
  const tabsRoot = card.querySelector<HTMLElement>("#workspace-tabs")!;
  const editor = card.querySelector<HTMLTextAreaElement>("#workspace-editor")!;
  const pathLabel = card.querySelector<HTMLElement>("#workspace-path")!;
  const language = card.querySelector<HTMLElement>("#workspace-language")!;
  const status = card.querySelector<HTMLElement>("#workspace-save-status")!;
  const saveButton = card.querySelector<HTMLButtonElement>("#workspace-save")!;
  let selectedPath: string | undefined;
  let activePath: string | undefined;
  let entries: Array<{ path: string; type: "file" | "directory"; editable: boolean; size?: number }> = [];
  const tabs = new Map<string, { path: string; content: string; saved: string }>();

  const moduleId = () => moduleSelect.value;

  const extensionLabel = (path: string) => {
    const name = path.split("/").pop() ?? path;
    const index = name.lastIndexOf(".");
    return index > 0 ? name.slice(index + 1).toUpperCase() : "TEXT";
  };

  const dirty = (path: string) => {
    const tab = tabs.get(path);
    return Boolean(tab && tab.content !== tab.saved);
  };

  const syncActive = () => {
    if (!activePath) return;
    const tab = tabs.get(activePath);
    if (tab) tab.content = editor.value;
  };

  const renderTabs = () => {
    tabsRoot.replaceChildren();
    for (const [path] of tabs) {
      const wrap = document.createElement("span");
      wrap.className = "action-row";
      wrap.style.flexWrap = "nowrap";
      wrap.style.gap = "2px";

      const button = document.createElement("button");
      button.type = "button";
      button.className = activePath === path ? "secondary-button active" : "secondary-button";
      button.textContent = (path.split("/").pop() ?? path) + (dirty(path) ? " *" : "");
      button.title = path;
      button.addEventListener("click", () => {
        syncActive();
        activePath = path;
        const tab = tabs.get(path)!;
        editor.value = tab.content;
        editor.disabled = false;
        saveButton.disabled = false;
        pathLabel.textContent = path;
        language.textContent = extensionLabel(path);
        status.textContent = dirty(path) ? "Unsaved changes" : "Saved";
        renderTabs();
      });

      const close = document.createElement("button");
      close.type = "button";
      close.className = "icon-button";
      close.textContent = "×";
      close.setAttribute("aria-label", "Close " + path);
      close.addEventListener("click", () => {
        syncActive();
        if (dirty(path) && !window.confirm("Close " + path + " with unsaved changes?")) return;
        tabs.delete(path);
        if (activePath === path) {
          activePath = tabs.keys().next().value as string | undefined;
          if (activePath) {
            const next = tabs.get(activePath)!;
            editor.value = next.content;
            editor.disabled = false;
            saveButton.disabled = false;
            pathLabel.textContent = activePath;
            language.textContent = extensionLabel(activePath);
          } else {
            editor.value = "";
            editor.disabled = true;
            saveButton.disabled = true;
            pathLabel.textContent = "Select a file";
            language.textContent = "TEXT";
            status.textContent = "No file open";
          }
        }
        renderTabs();
      });
      wrap.append(button, close);
      tabsRoot.append(wrap);
    }
  };

  const openFile = async (path: string) => {
    syncActive();
    let tab = tabs.get(path);
    if (!tab) {
      const file = await bailey.readModuleWorkspaceFile(moduleId(), path);
      tab = { path: file.path, content: file.content, saved: file.content };
      tabs.set(path, tab);
    }
    activePath = path;
    selectedPath = path;
    editor.value = tab.content;
    editor.disabled = false;
    saveButton.disabled = false;
    pathLabel.textContent = path;
    language.textContent = extensionLabel(path);
    status.textContent = dirty(path) ? "Unsaved changes" : "Saved";
    renderTabs();
    renderTree();
    editor.focus();
  };

  const renderTree = () => {
    tree.replaceChildren();
    if (!entries.length) {
      const empty = document.createElement("p");
      empty.className = "muted";
      empty.textContent = "No workspace files.";
      tree.append(empty);
      return;
    }
    for (const entry of entries) {
      const row = document.createElement("button");
      row.type = "button";
      row.className = "command-row";
      row.style.width = "100%";
      row.style.paddingLeft = String(12 + Math.max(0, entry.path.split("/").length - 1) * 14) + "px";
      if (selectedPath === entry.path) row.classList.add("editable");
      const copy = document.createElement("span");
      copy.className = "command-row-copy";
      const name = document.createElement("strong");
      name.textContent = entry.path.split("/").pop() ?? entry.path;
      const meta = document.createElement("small");
      meta.textContent = entry.type === "directory"
        ? "Folder"
        : entry.editable
          ? (entry.size ?? 0) + " bytes"
          : "Not editable";
      copy.append(name, meta);
      row.append(copy);
      row.addEventListener("click", () => {
        selectedPath = entry.path;
        if (entry.type === "file" && entry.editable) {
          void openFile(entry.path).catch((error) => window.alert(error instanceof Error ? error.message : String(error)));
        } else {
          renderTree();
        }
      });
      tree.append(row);
    }
  };

  const refreshTree = async () => {
    if (!moduleId()) {
      entries = [];
      renderTree();
      return;
    }
    entries = await bailey.listModuleWorkspace(moduleId());
    renderTree();
  };

  const refreshModules = async () => {
    const modules = await bailey.getModuleRuntimeStatus();
    const previous = moduleSelect.value;
    moduleSelect.replaceChildren(...modules.map((module) => {
      const option = document.createElement("option");
      option.value = module.id;
      option.textContent = module.name;
      return option;
    }));
    if (modules.some((module) => module.id === previous)) moduleSelect.value = previous;
    selectedPath = undefined;
    tabs.clear();
    activePath = undefined;
    editor.value = "";
    editor.disabled = true;
    saveButton.disabled = true;
    renderTabs();
    await refreshTree();
  };

  const createItem = async (type: "file" | "directory") => {
    if (!moduleId()) return;
    const value = window.prompt(type === "file" ? "New file path inside this module:" : "New folder path inside this module:");
    if (!value) return;
    await bailey.createModuleWorkspaceItem(moduleId(), value, type);
    await refreshTree();
    if (type === "file") await openFile(value.replace(/\\/g, "/"));
  };

  card.querySelector<HTMLButtonElement>("#workspace-refresh")!.addEventListener("click", () => void refreshModules());
  moduleSelect.addEventListener("change", () => {
    selectedPath = undefined;
    tabs.clear();
    activePath = undefined;
    editor.value = "";
    editor.disabled = true;
    saveButton.disabled = true;
    renderTabs();
    void refreshTree();
  });
  card.querySelector<HTMLButtonElement>("#workspace-new-file")!.addEventListener("click", () => {
    void createItem("file").catch((error) => window.alert(error instanceof Error ? error.message : String(error)));
  });
  card.querySelector<HTMLButtonElement>("#workspace-new-folder")!.addEventListener("click", () => {
    void createItem("directory").catch((error) => window.alert(error instanceof Error ? error.message : String(error)));
  });
  card.querySelector<HTMLButtonElement>("#workspace-rename")!.addEventListener("click", () => {
    if (!selectedPath) return;
    const destination = window.prompt("Rename/move workspace item to:", selectedPath);
    if (!destination || destination === selectedPath) return;
    void bailey.renameModuleWorkspaceItem(moduleId(), selectedPath, destination).then(async () => {
      tabs.clear();
      activePath = undefined;
      selectedPath = destination.replace(/\\/g, "/");
      editor.value = "";
      editor.disabled = true;
      saveButton.disabled = true;
      renderTabs();
      await refreshTree();
    }).catch((error) => window.alert(error instanceof Error ? error.message : String(error)));
  });
  card.querySelector<HTMLButtonElement>("#workspace-delete")!.addEventListener("click", () => {
    if (!selectedPath) return;
    if (!window.confirm("Delete " + selectedPath + "?")) return;
    const deleting = selectedPath;
    void bailey.deleteModuleWorkspaceItem(moduleId(), deleting).then(async () => {
      tabs.delete(deleting);
      if (activePath === deleting) activePath = undefined;
      selectedPath = undefined;
      editor.value = "";
      editor.disabled = true;
      saveButton.disabled = true;
      renderTabs();
      await refreshTree();
    }).catch((error) => window.alert(error instanceof Error ? error.message : String(error)));
  });

  editor.addEventListener("input", () => {
    if (!activePath) return;
    const tab = tabs.get(activePath);
    if (!tab) return;
    tab.content = editor.value;
    status.textContent = "Unsaved changes";
    status.className = "save-status saving";
    renderTabs();
  });

  const saveActive = async () => {
    if (!activePath) return;
    const tab = tabs.get(activePath);
    if (!tab) return;
    tab.content = editor.value;
    status.textContent = "Saving…";
    status.className = "save-status saving";
    await bailey.writeModuleWorkspaceFile(moduleId(), activePath, tab.content);
    tab.saved = tab.content;
    status.textContent = "Saved";
    status.className = "save-status";
    renderTabs();
    await refreshTree();
  };

  saveButton.addEventListener("click", () => {
    void saveActive().catch((error) => {
      status.textContent = error instanceof Error ? error.message : String(error);
      status.className = "save-status error";
    });
  });
  editor.addEventListener("keydown", (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
      event.preventDefault();
      void saveActive();
    }
  });

  void refreshModules().catch((error) => {
    tree.textContent = error instanceof Error ? error.message : String(error);
  });
}

installWorkspaceUi();
