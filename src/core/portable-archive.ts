import { gzipSync, gunzipSync } from "node:zlib";
import { mkdir, readFile, readdir, stat, writeFile } from "node:fs/promises";
import { dirname, relative, resolve, sep } from "node:path";

export type PortableArchiveKind = "module" | "backup";

export interface PortableArchiveEntry {
  path: string;
  data: string;
  size: number;
}

export interface PortableArchive {
  format: "bailey-portable-archive";
  version: 1;
  kind: PortableArchiveKind;
  createdAt: number;
  metadata?: Record<string, string>;
  files: PortableArchiveEntry[];
}

const MAX_ARCHIVE_BYTES = 512 * 1024 * 1024;
const MAX_FILE_BYTES = 256 * 1024 * 1024;
const MAX_FILES = 10_000;

export function safeArchivePath(value: unknown): string {
  const path = String(value ?? "").replace(/\\/g, "/").replace(/^\/+/, "");
  if (!path || path.includes("\0")) throw new Error("Archive path is invalid.");
  const parts = path.split("/");
  if (parts.some((part) => !part || part === "." || part === ".." || part.includes(":"))) {
    throw new Error("Archive path is unsafe.");
  }
  return parts.join("/");
}

function inside(root: string, relativePath: string): string {
  const normalized = safeArchivePath(relativePath);
  const rootPath = resolve(root);
  const output = resolve(rootPath, ...normalized.split("/"));
  if (!output.startsWith(rootPath + sep)) throw new Error("Archive entry escapes the target directory.");
  return output;
}

export function encodePortableArchive(archive: PortableArchive): Buffer {
  return gzipSync(Buffer.from(JSON.stringify(archive), "utf8"), { level: 9 });
}

export function decodePortableArchive(buffer: Buffer, expectedKind?: PortableArchiveKind): PortableArchive {
  if (buffer.byteLength > MAX_ARCHIVE_BYTES) throw new Error("Bailey archive is too large.");
  let parsed: unknown;
  try {
    parsed = JSON.parse(gunzipSync(buffer).toString("utf8"));
  } catch {
    throw new Error("This is not a valid Bailey archive.");
  }
  if (!parsed || typeof parsed !== "object") throw new Error("Bailey archive is invalid.");
  const archive = parsed as Partial<PortableArchive>;
  if (archive.format !== "bailey-portable-archive" || archive.version !== 1) throw new Error("Unsupported Bailey archive format.");
  if (archive.kind !== "module" && archive.kind !== "backup") throw new Error("Unsupported Bailey archive kind.");
  if (expectedKind && archive.kind !== expectedKind) throw new Error(`Expected a ${expectedKind} archive.`);
  if (!Array.isArray(archive.files) || archive.files.length > MAX_FILES) throw new Error("Bailey archive contains too many files.");
  let total = 0;
  for (const entry of archive.files) {
    if (!entry || typeof entry !== "object") throw new Error("Bailey archive contains an invalid file entry.");
    entry.path = safeArchivePath(entry.path);
    if (typeof entry.data !== "string" || !Number.isInteger(entry.size) || entry.size < 0 || entry.size > MAX_FILE_BYTES) {
      throw new Error(`Invalid archive entry: ${entry.path}`);
    }
    const actual = Buffer.from(entry.data, "base64");
    if (actual.byteLength !== entry.size) throw new Error(`Archive entry size mismatch: ${entry.path}`);
    total += actual.byteLength;
    if (total > MAX_ARCHIVE_BYTES) throw new Error("Bailey archive expands beyond its safety limit.");
  }
  return archive as PortableArchive;
}

export async function collectArchiveFiles(
  root: string,
  options: { prefix?: string; excludeNames?: ReadonlySet<string> } = {},
): Promise<PortableArchiveEntry[]> {
  const output: PortableArchiveEntry[] = [];
  const rootPath = resolve(root);
  const excludeNames = options.excludeNames ?? new Set<string>();

  const walk = async (directory: string): Promise<void> => {
    let entries;
    try {
      entries = await readdir(directory, { withFileTypes: true });
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === "ENOENT") return;
      throw error;
    }
    for (const entry of entries) {
      if (excludeNames.has(entry.name) || entry.isSymbolicLink()) continue;
      const path = resolve(directory, entry.name);
      if (entry.isDirectory()) {
        await walk(path);
        continue;
      }
      if (!entry.isFile()) continue;
      const info = await stat(path);
      if (info.size > MAX_FILE_BYTES) throw new Error(`File is too large to archive: ${entry.name}`);
      const rel = relative(rootPath, path).split(sep).join("/");
      const archivePath = safeArchivePath(options.prefix ? `${options.prefix}/${rel}` : rel);
      const data = await readFile(path);
      output.push({ path: archivePath, data: data.toString("base64"), size: data.byteLength });
      if (output.length > MAX_FILES) throw new Error("Too many files for one Bailey archive.");
    }
  };

  await walk(rootPath);
  return output;
}

export async function writeArchiveFiles(root: string, files: PortableArchiveEntry[]): Promise<void> {
  for (const entry of files) {
    const path = inside(root, entry.path);
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, Buffer.from(entry.data, "base64"));
  }
}
