import type { PortableArchiveEntry } from "./portable-archive";

export const BACKUP_EXCLUDES = new Set([
  "node_modules", ".venv", "venv", ".bailey-venv", ".bailey-runtime", ".bailey-cache",
  "__pycache__", ".pytest_cache", ".mypy_cache", ".ruff_cache", ".cache", ".git",
  "auth", "auth_info", "sessions", "session",
  ".env", ".env.local", ".env.development", ".env.production", "credentials.json", "creds.json",
  "auth.json", "auth-state.json", "tokens.json", "token.json", "secret.json", "secrets.json", "session.json",
]);

const BACKUP_ROOT_FILES = new Set(["config.json", "commands.json", "chats.json"]);

export function validateBackupEntries(files: readonly PortableArchiveEntry[]): void {
  for (const entry of files) {
    const parts = entry.path.split("/");
    const first = parts[0];
    const allowed = BACKUP_ROOT_FILES.has(entry.path) || first === "modules" || first === "storage";
    if (!allowed || parts.some((part) => BACKUP_EXCLUDES.has(part)) || first === "sessions" || first === "engines") {
      throw new Error(`Backup contains a forbidden or unsupported path: ${entry.path}`);
    }
  }
}
