import type { PortableArchiveEntry } from "./portable-archive";

export const MODULE_PACKAGE_EXCLUDES = new Set([
  "node_modules", ".venv", "venv", ".bailey-venv", ".bailey-runtime", ".bailey-cache",
  "__pycache__", ".pytest_cache", ".mypy_cache", ".ruff_cache", ".cache", ".git", ".data",
  "auth", "auth_info", "sessions", "session",
  ".env", ".env.local", ".env.development", ".env.production", "credentials.json", "creds.json",
  "auth.json", "auth-state.json", "tokens.json", "token.json", "secret.json", "secrets.json", "session.json",
]);

export function validateModulePackageEntries(files: readonly PortableArchiveEntry[]): void {
  if (files.some((entry) => entry.path === "")) throw new Error("Module package contains an invalid path.");
  for (const entry of files) {
    if (entry.path.split("/").some((part) => MODULE_PACKAGE_EXCLUDES.has(part))) {
      throw new Error(`Module package contains a local environment, cache or secret path: ${entry.path}`);
    }
  }
}
