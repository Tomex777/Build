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
    }): Promise<ModuleCreateResult>;
    showModule(moduleId: string): Promise<{ ok: boolean; directory: string }>;
    reloadModules(): Promise<ModuleReloadResult>;
    detectModuleRuntimes(): Promise<Array<{ id: string; label: string; available: boolean; command: string; version?: string; detail?: string }>>;
    exportModulePackage(moduleId: string): Promise<{ ok: boolean; canceled?: boolean; path?: string }>;
    installModulePackage(): Promise<{ ok: boolean; canceled?: boolean; id?: string; name?: string }>;
    getModuleRuntimeStatus(): Promise<Array<{ id: string; name: string; running: boolean; pid?: number; crashCount: number; restartCount: number; lastError?: string; capabilities: string[]; permissions: string[]; logs: string[] }>>;
    restartModule(moduleId: string): Promise<unknown>;
    exportBackup(): Promise<{ ok: boolean; canceled?: boolean; path?: string }>;
    importBackup(): Promise<{ ok: boolean; canceled?: boolean; fileCount?: number }>;
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
          actions.append(exportButton, restartButton);

          const details = document.createElement("details");
          const summary = document.createElement("summary");
          summary.textContent = "Logs & permissions";
          const pre = document.createElement("pre");
          pre.className = "manifest-example";
          pre.textContent = [
            `Capabilities: ${module.capabilities.join(", ") || "none"}`,
            `Permissions: ${module.permissions.join(", ") || "none"}`,
            "",
            ...module.logs.slice(-30),
          ].join("\n");
          details.append(summary, pre);
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
