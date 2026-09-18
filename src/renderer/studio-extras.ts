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
    installModuleDependencies(moduleId: string): Promise<{ ok: boolean; installed: boolean; detail: string; output: string[] }>;
    getModuleRuntimeStatus(): Promise<Array<{ id: string; name: string; running: boolean; pid?: number; crashCount: number; restartCount: number; lastError?: string; capabilities: string[]; permissions: string[]; grantedPermissions: string[]; logs: string[] }>>;
    restartModule(moduleId: string): Promise<unknown>;
    setModulePermissions(moduleId: string, grants: string[]): Promise<unknown>;
    exportBackup(): Promise<{ ok: boolean; canceled?: boolean; path?: string }>;
    importBackup(): Promise<{ ok: boolean; canceled?: boolean; fileCount?: number }>;
    getStorageProfiles(): Promise<Array<{ name: string; provider: "local" | "s3" | "azure" | "gcs" | "supabase"; isDefault: boolean; config: Record<string, string | boolean>; secretFields: string[] }>>;
    saveStorageProfile(input: { name: string; provider: string; config: Record<string, string | boolean>; secrets: Record<string, string> }): Promise<unknown>;
    setDefaultStorageProfile(name: string): Promise<unknown>;
    deleteStorageProfile(name: string): Promise<unknown>;
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
            permissionTitle.textContent = "Requested permissions";
            permissionWrap.append(permissionTitle);
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
            none.textContent = "This module requests no privileged host permissions.";
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
