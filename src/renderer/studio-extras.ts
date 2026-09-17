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
  };
}).bailey;

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
          <p>Bailey creates the folder, manifest and starter worker. You can edit the generated files immediately afterwards.</p>
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
          <small>This becomes the command’s initial .menu section.</small>
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
    }).then((result) => {
      createdModuleId = result.id;
      status.textContent = `Created ${result.name}. Restart Bailey Host to load the new module.`;
      status.className = "save-status";
      openCreated.hidden = false;
      createButton.textContent = "Created";
    }).catch((error) => {
      status.textContent = error instanceof Error ? error.message : String(error);
      status.className = "editor-error";
      createButton.disabled = false;
      createButton.textContent = "Create module";
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

const modulesFolderButton = document.querySelector<HTMLButtonElement>("#studio-open-modules-folder");
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
