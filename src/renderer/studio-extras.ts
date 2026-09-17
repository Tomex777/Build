const studioStyles = document.createElement("link");
studioStyles.rel = "stylesheet";
studioStyles.href = "./studio.css";
document.head.append(studioStyles);

const bailey = (window as unknown as {
  bailey: {
    openModulesFolder(): Promise<{ ok: boolean; path: string }>;
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
