import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { promises as fs, existsSync, realpathSync, createReadStream } from 'node:fs';
import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import { pipeline } from 'node:stream/promises';

const exec = promisify(execFile);
const PORT = Number(process.env.PORT || 47831);
const HOST = process.env.HOST || '127.0.0.1';
const TOKEN = process.env.CORTEX_AGENT_TOKEN || '';
const PROJECT_ROOT = path.resolve(process.env.CORTEX_PROJECT_ROOT || process.env.NIGHT_ROOT || '/opt/night');
const MANAGED_SERVICE = process.env.CORTEX_SERVICE || process.env.NIGHT_SERVICE || 'night.service';
const SERVICE_CONTROL_HELPER = '/usr/local/libexec/cortex-agent-control';
const ENTRY_FILE = process.env.CORTEX_ENTRY || process.env.NIGHT_ENTRY || 'index.js';
const START_COMMAND = process.env.CORTEX_START_COMMAND || process.env.NIGHT_START_COMMAND || 'node index.js';
const GIT_REPOSITORY = process.env.CORTEX_GIT_REPO || '';
const GIT_BRANCH = process.env.CORTEX_GIT_BRANCH || '';
const STATE_DIR = path.resolve(process.env.CORTEX_STATE_DIR || path.join(PROJECT_ROOT, '.cortex'));
const ACTIVITY_FILE = path.join(STATE_DIR, 'activity.jsonl');
const BACKUP_DIR = path.join(STATE_DIR, 'backups');
const COMMAND_SETTINGS_FILE = path.resolve(process.env.CORTEX_COMMAND_SETTINGS_FILE || '/var/lib/mscc/data/mscc-settings.json');
const COMMAND_SETTINGS_SCHEMA_FILE = path.resolve(process.env.CORTEX_COMMAND_SETTINGS_SCHEMA_FILE || '/var/lib/mscc/data/cortex-settings-schema.json');
const RUNTIME_REGISTRY_FILE = path.resolve(process.env.CORTEX_RUNTIME_REGISTRY_FILE || '/var/lib/mscc/data/cortex-runtime-registry.json');
const MANAGED_ENV_FILE = path.resolve(process.env.CORTEX_MSCC_ENV_FILE || '/etc/mscc.env');
const ENVIRONMENT_SCHEMA_FILE = path.resolve(process.env.CORTEX_ENV_SCHEMA_FILE || '/var/lib/mscc/data/cortex-environment-schema.json');
const DEFAULT_ENVIRONMENT_SCHEMA = [
  { key: 'OWNER_NUMBER', label: 'Owner number', description: 'Primary private-control WhatsApp number.', secret: true, type: 'phone', requiresRestart: true },
  { key: 'CONTROL_NUMBERS', label: 'Control numbers', description: 'Optional comma-separated private-control numbers.', secret: true, type: 'phones', requiresRestart: true },
  { key: 'MAX_ACCOUNTS', label: 'Account limit', description: 'Maximum managed WhatsApp sessions.', type: 'integer', min: 1, max: 50, requiresRestart: true },
  { key: 'MESSAGE_TTL_HOURS', label: 'Message retention', description: 'Hours retained in the on-disk message index.', type: 'integer', min: 1, max: 168, requiresRestart: true },
  { key: 'MAX_MESSAGE_CACHE', label: 'Messages per account', description: 'Maximum retained indexed messages per account.', type: 'integer', min: 100, max: 20000, requiresRestart: true },
  { key: 'GROUP_META_TTL_SECONDS', label: 'Group metadata TTL', description: 'Seconds before cached group metadata is refreshed.', type: 'integer', min: 10, max: 600, requiresRestart: true },
  { key: 'GROUP_META_CACHE_MAX', label: 'Group metadata cache', description: 'Maximum in-memory group metadata entries.', type: 'integer', min: 16, max: 1024, requiresRestart: true },
  { key: 'LOG_LEVEL', label: 'Log level', description: 'MSCC runtime log verbosity.', type: 'enum', values: ['silent', 'fatal', 'error', 'warn', 'info', 'debug', 'trace'], requiresRestart: true },
];
const MODULES_DIR = path.resolve(process.env.CORTEX_MODULES_DIR || path.join(PROJECT_ROOT, 'modules'));
const MSCC_CONTROL_URL = 'http://127.0.0.1:8788';
const PRIVATE_BACKUP_PATHS = String(process.env.CORTEX_PRIVATE_BACKUP_PATHS || '')
  .split(':')
  .map((value) => value.trim())
  .filter(Boolean)
  .map((value) => path.resolve(value));
const MAX_BODY = 16 * 1024 * 1024;
const MAX_FILE_BYTES = 10 * 1024 * 1024;
const MAX_TEXT_BYTES = 2 * 1024 * 1024;
const configuredTransferLimit = Number(process.env.CORTEX_MAX_TRANSFER_BYTES || 512 * 1024 * 1024);
const MAX_TRANSFER_BYTES = Number.isSafeInteger(configuredTransferLimit) && configuredTransferLimit >= MAX_FILE_BYTES
  ? configuredTransferLimit
  : 512 * 1024 * 1024;
const PROTECTED_NAMES = new Set(['.git', '.ssh', 'node_modules', '.gradle', '.cortex']);
const PROTECTED_FILES = new Set(['id_rsa', 'id_ed25519', 'id_ecdsa', 'id_dsa']);

function isProtectedName(name) {
  const lower = name.toLowerCase();
  return PROTECTED_NAMES.has(name) || PROTECTED_FILES.has(name) || lower.startsWith('.env') ||
    ['.pem', '.p12', '.pfx', '.key', '.keystore'].some((extension) => lower.endsWith(extension));
}

if (!TOKEN || TOKEN.length < 24) {
  console.error('CORTEX_AGENT_TOKEN must be set to a strong token (24+ characters).');
  process.exit(1);
}

async function controlManagedService(action, timeout = 30_000) {
  if (!['start', 'stop', 'restart', 'enable', 'disable'].includes(action)) {
    throw Object.assign(new Error('Invalid service control action'), { statusCode: 400 });
  }
  if (typeof process.getuid === 'function' && process.getuid() === 0) {
    return exec('systemctl', [action, MANAGED_SERVICE], { timeout });
  }
  return exec('/usr/bin/sudo', ['-n', SERVICE_CONTROL_HELPER, action], { timeout });
}

function json(res, status, body) {
  const data = Buffer.from(JSON.stringify(body));
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': data.length,
    'cache-control': 'no-store',
  });
  res.end(data);
}

function sameToken(value) {
  const expected = Buffer.from(TOKEN);
  const actual = Buffer.from(value || '');
  return expected.length === actual.length && crypto.timingSafeEqual(expected, actual);
}

function authorized(req) {
  const header = req.headers.authorization || '';
  return header.startsWith('Bearer ') && sameToken(header.slice(7));
}

function redactLogLine(input) {
  let line = String(input ?? '');
  line = line.replace(/(authorization\s*[:=]\s*bearer\s+)[^\s"',}]+/gi, '$1[REDACTED]');
  line = line.replace(/\bBearer\s+[A-Za-z0-9._~+\/=:-]{12,}/g, 'Bearer [REDACTED]');
  line = line.replace(
    /(["']?(?:token|password|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|pairing[_-]?(?:code|qr)|sas)["']?\s*[:=]\s*["']?)([^"',\s}]{4,})/gi,
    '$1[REDACTED]',
  );
  line = line.replace(/([?&](?:sig|signature|token|key)=)[^&\s]+/gi, '$1[REDACTED]');
  return line;
}

function publicErrorMessage(statusCode) {
  const status = Number(statusCode) || 500;
  if (status === 400) return 'Request could not be completed.';
  if (status === 401) return 'Authentication required.';
  if (status === 403) return 'Request is not allowed.';
  if (status === 404) return 'Not found.';
  if (status === 408 || status === 504) return 'Request timed out.';
  if (status === 409) return 'Request conflicts with the current state.';
  if (status === 413) return 'Request is too large.';
  if (status === 429) return 'Too many requests. Try again shortly.';
  if (status >= 500 && status <= 599) return 'Server request failed.';
  return 'Request failed.';
}

function sanitizeActivityDetail(value, key = '', depth = 0) {
  if (depth > 6) return '[REDACTED]';
  if (/token|password|secret|api[_-]?key|access[_-]?token|refresh[_-]?token|authorization|cookie|session|pairing[_-]?(?:code|qr)|sas/i.test(key)) {
    return '[REDACTED]';
  }
  if (typeof value === 'string') return redactLogLine(value).slice(0, 1200);
  if (Array.isArray(value)) return value.slice(0, 100).map((item) => sanitizeActivityDetail(item, key, depth + 1));
  if (value && typeof value === 'object') {
    const out = {};
    for (const [childKey, childValue] of Object.entries(value).slice(0, 100)) {
      out[childKey] = sanitizeActivityDetail(childValue, childKey, depth + 1);
    }
    return out;
  }
  return value;
}

async function readJson(req) {
  const chunks = [];
  let length = 0;
  for await (const chunk of req) {
    length += chunk.length;
    if (length > MAX_BODY) throw Object.assign(new Error('Request body too large'), { statusCode: 413 });
    chunks.push(chunk);
  }
  if (!chunks.length) return {};
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

function safeProjectPath(input = '/') {
  // URLSearchParams has already decoded query values, while JSON body paths are
  // plain strings. Never decode a second time: double-decoding can turn a
  // harmless literal percent sequence into traversal syntax.
  const requested = String(input || '/');
  const relative = requested.replace(/^\/+/, '');
  const target = path.resolve(PROJECT_ROOT, relative);
  if (target !== PROJECT_ROOT && !target.startsWith(PROJECT_ROOT + path.sep)) {
    throw Object.assign(new Error('Path escapes managed project'), { statusCode: 400 });
  }

  const segments = path.relative(PROJECT_ROOT, target).split(path.sep).filter(Boolean);
  if (segments.some(isProtectedName)) {
    throw Object.assign(new Error('Protected path'), { statusCode: 403 });
  }

  // Lexical containment is not enough: a symlink inside PROJECT_ROOT could
  // otherwise point to /etc, /var/lib, or another secret-bearing tree. Resolve
  // the deepest existing ancestor (the target itself when it exists) and prove
  // its real path is still inside the real project root. This also protects
  // creation below a symlink whose destination is outside the project.
  const rootReal = realpathSync(PROJECT_ROOT);
  let existing = target;
  while (!existsSync(existing)) {
    const parent = path.dirname(existing);
    if (parent === existing) break;
    existing = parent;
  }

  let existingReal;
  try {
    existingReal = realpathSync(existing);
  } catch {
    throw Object.assign(new Error('Invalid project path'), { statusCode: 400 });
  }

  if (existingReal !== rootReal && !existingReal.startsWith(rootReal + path.sep)) {
    throw Object.assign(new Error('Path escapes managed project through a symbolic link'), { statusCode: 400 });
  }

  const realSegments = path.relative(rootReal, existingReal).split(path.sep).filter(Boolean);
  if (realSegments.some(isProtectedName)) {
    throw Object.assign(new Error('Protected path'), { statusCode: 403 });
  }

  return target;
}

function fileType(dirent) {
  if (dirent.isDirectory()) return 'directory';
  if (dirent.isFile()) return 'file';
  if (dirent.isSymbolicLink()) return 'symlink';
  return 'other';
}


async function ensureState() {
  await fs.mkdir(BACKUP_DIR, { recursive: true });
}

async function recordActivity(action, detail = {}) {
  await ensureState();
  const row = JSON.stringify({
    id: crypto.randomUUID(),
    at: new Date().toISOString(),
    action,
    detail: sanitizeActivityDetail(detail),
  });
  await fs.appendFile(ACTIVITY_FILE, row + '\n', 'utf8');
  try {
    const current = await fs.stat(ACTIVITY_FILE);
    if (current.size > 2 * 1024 * 1024) {
      const text = await fs.readFile(ACTIVITY_FILE, 'utf8');
      const lines = text.trim().split(/\r?\n/).slice(-2500);
      await fs.writeFile(ACTIVITY_FILE, lines.join('\n') + '\n', 'utf8');
    }
  } catch {}
}

async function agentActivity(limit) {
  const safeLimit = Math.max(10, Math.min(500, Number(limit) || 100));
  try {
    const text = await fs.readFile(ACTIVITY_FILE, 'utf8');
    return text.trim().split(/\r?\n/).filter(Boolean).slice(-safeLimit).reverse().flatMap((line) => {
      try { return [{ ...JSON.parse(line), source: 'agent' }]; } catch { return []; }
    });
  } catch (error) {
    if (error?.code === 'ENOENT') return [];
    throw error;
  }
}

async function activity(limit) {
  const safeLimit = Math.max(10, Math.min(500, Number(limit) || 100));
  const agentRows = await agentActivity(safeLimit);
  let msccRows = [];
  try {
    const payload = await msccControl('GET', '/activity?limit=' + safeLimit);
    msccRows = (Array.isArray(payload?.entries) ? payload.entries : []).map((row) => ({
      ...row,
      detail: sanitizeActivityDetail(row?.detail || {}),
      source: 'mscc',
    }));
  } catch {
    // Activity must remain available even while MSCC itself is restarting/offline.
  }
  return [...agentRows, ...msccRows]
    .filter((row) => row && row.at && row.action)
    .sort((a, b) => String(b.at).localeCompare(String(a.at)))
    .slice(0, safeLimit);
}

async function makeDirectory(inputPath) {
  const target = safeProjectPath(inputPath);
  await assertNoSymlink(target);
  await fs.mkdir(target, { recursive: false });
  await recordActivity('server:file.mkdir', { path: path.relative(PROJECT_ROOT, target) || '/' });
}

async function renamePath(fromInput, toInput) {
  const from = safeProjectPath(fromInput);
  const to = safeProjectPath(toInput);
  if (from === PROJECT_ROOT || to === PROJECT_ROOT) throw Object.assign(new Error('Project root cannot be renamed'), { statusCode: 400 });
  if (from === to) throw Object.assign(new Error('Source and destination are the same'), { statusCode: 400 });
  await assertNoSymlink(from);
  await assertNoSymlink(to);
  await fs.mkdir(path.dirname(to), { recursive: true });
  await fs.rename(from, to);
  await recordActivity('server:file.rename', {
    from: path.relative(PROJECT_ROOT, from),
    to: path.relative(PROJECT_ROOT, to),
  });
}

async function copyPath(fromInput, toInput) {
  const from = safeProjectPath(fromInput);
  const to = safeProjectPath(toInput);
  if (from === PROJECT_ROOT || to === PROJECT_ROOT) throw Object.assign(new Error('Project root cannot be copied'), { statusCode: 400 });
  if (from === to) throw Object.assign(new Error('Source and destination are the same'), { statusCode: 400 });
  await assertNoSymlink(from);
  await assertNoSymlink(to);

  const sourceInfo = await fs.stat(from);
  const relativeToSource = path.relative(from, to);
  if (
    sourceInfo.isDirectory() &&
    relativeToSource &&
    !relativeToSource.startsWith('..') &&
    !path.isAbsolute(relativeToSource)
  ) {
    throw Object.assign(new Error('A directory cannot be copied into itself'), { statusCode: 400 });
  }

  try {
    await fs.access(to);
    throw Object.assign(new Error('Destination already exists'), { statusCode: 409 });
  } catch (error) {
    if (error?.statusCode) throw error;
    if (error?.code !== 'ENOENT') throw error;
  }

  await fs.mkdir(path.dirname(to), { recursive: true });
  await fs.cp(from, to, {
    recursive: sourceInfo.isDirectory(),
    force: false,
    errorOnExist: true,
    preserveTimestamps: true,
  });
  await recordActivity('server:file.copy', {
    from: path.relative(PROJECT_ROOT, from),
    to: path.relative(PROJECT_ROOT, to),
  });
}

async function deletePath(inputPath) {
  const target = safeProjectPath(inputPath);
  if (target === PROJECT_ROOT) throw Object.assign(new Error('Project root cannot be deleted'), { statusCode: 400 });
  await assertNoSymlink(target);
  await fs.rm(target, { recursive: true, force: false });
  await recordActivity('server:file.delete', { path: path.relative(PROJECT_ROOT, target) });
}

function cleanZipEntry(name) {
  const normalized = String(name || '').replace(/\\/g, '/');
  if (!normalized || normalized.startsWith('/') || normalized.split('/').includes('..')) {
    throw Object.assign(new Error('Unsafe zip entry'), { statusCode: 400 });
  }
  return normalized;
}

async function inspectZipArchive(archive, {
  maxEntries = 5000,
  maxUncompressedBytes = 512 * 1024 * 1024,
} = {}) {
  const names = await exec('unzip', ['-Z1', archive], {
    timeout: 30_000,
    maxBuffer: 16 * 1024 * 1024,
  });
  const entries = names.stdout.split(/\r?\n/).filter(Boolean);
  if (entries.length < 1) {
    throw Object.assign(new Error('Archive is empty'), { statusCode: 400 });
  }
  if (entries.length > maxEntries) {
    throw Object.assign(new Error('Archive contains too many entries'), { statusCode: 413 });
  }
  entries.forEach(cleanZipEntry);

  // Info-ZIP's verbose central-directory view exposes the original Unix file
  // mode and uncompressed size without extracting anything. Reject links and
  // device/special files before unzip gets a chance to materialize them.
  const verbose = await exec('unzip', ['-Z', '-v', archive], {
    timeout: 30_000,
    maxBuffer: 16 * 1024 * 1024,
  });
  const modes = [...verbose.stdout.matchAll(
    /Unix file attributes \([0-7]+ octal\):\s*([^\r\n]+)/g,
  )].map((match) => match[1].trim());

  if (modes.some((mode) => /^[lbcps]/.test(mode))) {
    throw Object.assign(new Error('Archive contains a link or special file'), { statusCode: 400 });
  }

  const totalUncompressed = [...verbose.stdout.matchAll(
    /uncompressed size:\s*(\d+)\s*bytes/gi,
  )].reduce((sum, match) => sum + Number(match[1]), 0);

  if (!Number.isSafeInteger(totalUncompressed) || totalUncompressed > maxUncompressedBytes) {
    throw Object.assign(new Error('Archive expands beyond the allowed size'), { statusCode: 413 });
  }

  return entries;
}

async function projectArchiveExcludes(root, archivePrefix = '') {
  const patterns = [];
  const rootInfo = await fs.lstat(root);
  const prefix = archivePrefix === '.' ? '' : archivePrefix.replace(/\/+$/, '');

  if (rootInfo.isSymbolicLink()) {
    return prefix ? [prefix, prefix + '/*'] : [];
  }
  if (!rootInfo.isDirectory()) return patterns;

  async function walk(current, relative = '') {
    const entries = await fs.readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      const childRelative = relative ? relative + '/' + entry.name : entry.name;
      const archiveName = prefix ? prefix + '/' + childRelative : childRelative;

      if (entry.isSymbolicLink() || isProtectedName(entry.name)) {
        patterns.push(archiveName, archiveName + '/*');
        if (patterns.length > 4000) {
          throw Object.assign(new Error('Selection contains too many protected or linked paths to archive safely'), { statusCode: 413 });
        }
        continue;
      }
      if (entry.isDirectory()) await walk(path.join(current, entry.name), childRelative);
    }
  }

  await walk(root);
  return patterns;
}

async function archivePaths(paths, destination) {
  if (!Array.isArray(paths) || paths.length < 1 || paths.length > 100) {
    throw Object.assign(new Error('paths must contain 1-100 items'), { statusCode: 400 });
  }
  const dest = safeProjectPath(destination);
  if (!String(destination).toLowerCase().endsWith('.zip')) {
    throw Object.assign(new Error('Archive destination must end in .zip'), { statusCode: 400 });
  }
  if (dest === PROJECT_ROOT) throw Object.assign(new Error('Invalid archive destination'), { statusCode: 400 });
  const relative = [];
  const excludes = [];
  for (const input of paths) {
    const target = safeProjectPath(String(input));
    await assertNoSymlink(target);
    const item = path.relative(PROJECT_ROOT, target) || '.';
    relative.push(item);
    excludes.push(...await projectArchiveExcludes(target, item));
  }

  // Never let a destination inside the selected tree become an input to its own
  // archive. This also keeps a previous archive of the same name out of the next.
  const destinationRelative = path.relative(PROJECT_ROOT, dest).split(path.sep).join('/');
  excludes.push(destinationRelative, destinationRelative + '/*');

  await fs.rm(dest, { force: true });
  await fs.mkdir(path.dirname(dest), { recursive: true });
  await exec('zip', [
    '-rq',
    dest,
    ...relative,
    ...[...new Set(excludes)].flatMap((pattern) => ['-x', pattern]),
  ], {
    cwd: PROJECT_ROOT,
    timeout: 5 * 60_000,
    maxBuffer: 8 * 1024 * 1024,
  });
  await recordActivity('server:file.compress', { paths: relative, destination: path.relative(PROJECT_ROOT, dest) });
}

async function extractArchive(inputPath, destination = '/') {
  const archive = safeProjectPath(inputPath);
  const dest = safeProjectPath(destination);
  await assertNoSymlink(archive);
  await assertNoSymlink(dest);
  const entries = await inspectZipArchive(archive);
  // Extraction must obey the same protected-path boundary as ordinary file
  // operations so archives cannot write hidden server state.
  entries.forEach(validateRestoreEntry);
  await fs.mkdir(dest, { recursive: true });
  await exec('unzip', ['-oq', archive, '-d', dest], {
    timeout: 5 * 60_000,
    maxBuffer: 8 * 1024 * 1024,
  });
  // Defense in depth: re-check only paths that belonged to this archive.
  // Do not scan unrelated existing project directories such as .git/.cortex.
  for (const entry of entries) {
    const clean = cleanZipEntry(entry);
    const extracted = path.resolve(dest, clean);
    if (extracted !== dest && !extracted.startsWith(dest + path.sep)) {
      throw Object.assign(new Error('Archive extraction escaped its destination'), { statusCode: 400 });
    }
    try {
      const info = await fs.lstat(extracted);
      if (info.isSymbolicLink() || (!info.isDirectory() && !info.isFile())) {
        throw Object.assign(new Error('Archive materialized an unsafe file type'), { statusCode: 400 });
      }
    } catch (error) {
      if (error?.code !== 'ENOENT') throw error;
    }
  }
  await recordActivity('server:file.decompress', {
    path: path.relative(PROJECT_ROOT, archive),
    destination: path.relative(PROJECT_ROOT, dest) || '/',
  });
}

function backupName(privateBackup) {
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  return (privateBackup ? 'private-' : 'project-') + stamp + '.zip';
}

const BACKUP_SKIP_DIRS = new Set([
  'node_modules', '.git', '.cortex', '.cache', '.npm', 'temp', 'tmp', 'downloads',
]);

async function symlinkBackupExcludes(root, archivePrefix = '') {
  const patterns = [];
  const rootInfo = await fs.lstat(root);
  if (rootInfo.isSymbolicLink()) {
    const clean = archivePrefix.replace(/\/+$/, '');
    return clean ? [clean, clean + '/*'] : [];
  }
  if (!rootInfo.isDirectory()) return patterns;

  async function walk(current, relative = '') {
    const entries = await fs.readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      const childRelative = relative ? relative + '/' + entry.name : entry.name;
      const archiveName = archivePrefix
        ? archivePrefix.replace(/\/+$/, '') + '/' + childRelative
        : childRelative;
      if (entry.isSymbolicLink()) {
        patterns.push(archiveName, archiveName + '/*');
        if (patterns.length > 2000) {
          throw Object.assign(new Error('Project contains too many symbolic links to back up safely'), { statusCode: 413 });
        }
        continue;
      }
      if (entry.isDirectory() && !BACKUP_SKIP_DIRS.has(entry.name)) {
        await walk(path.join(current, entry.name), childRelative);
      }
    }
  }

  await walk(root);
  return patterns;
}

async function createProjectBackup(privateBackup = false) {
  await ensureState();
  const name = backupName(privateBackup);
  const target = path.join(BACKUP_DIR, name);
  const excludes = [
    'node_modules/*', '.git/*', '.cortex/*', '.cache/*', '.npm/*',
    'temp/*', 'tmp/*', 'downloads/*', '*.log', '*.tmp'
  ];
  if (!privateBackup) {
    excludes.push('.env', '.env.*', 'session/*', 'sessions/*', 'auth/*', 'auth-b/*', 'pair/*', 'pair_temp/*', '*.pem', '*.key', '*.p12', '*.pfx', '*.keystore');
  }
  // Info-ZIP follows symlinks unless told otherwise, which could copy data
  // from outside PROJECT_ROOT into a backup. Discover symlinks without
  // traversing excluded dependency/cache trees and exclude each link path
  // (and anything beneath a directory link) before zip reads file contents.
  excludes.push(...await symlinkBackupExcludes(PROJECT_ROOT));
  const args = ['-rq', target, '.', ...excludes.flatMap((pattern) => ['-x', pattern])];
  await exec('zip', args, {
    cwd: PROJECT_ROOT,
    timeout: 10 * 60_000,
    maxBuffer: 16 * 1024 * 1024,
  });

  if (privateBackup) {
    for (const extraPath of PRIVATE_BACKUP_PATHS) {
      try {
        await fs.access(extraPath);
      } catch {
        continue;
      }
      const relativeFromRoot = path.relative('/', extraPath);
      if (!relativeFromRoot || relativeFromRoot.startsWith('..')) continue;
      const extraExcludes = await symlinkBackupExcludes(extraPath, relativeFromRoot);
      await exec('zip', [
        '-rq',
        target,
        relativeFromRoot,
        ...extraExcludes.flatMap((pattern) => ['-x', pattern]),
      ], {
        cwd: '/',
        timeout: 10 * 60_000,
        maxBuffer: 16 * 1024 * 1024,
      });
    }
  }

  const info = await fs.stat(target);
  await recordActivity('server:backup.create', { name, private: privateBackup, sizeBytes: info.size });
  return { name, sizeBytes: info.size, createdAt: info.mtime.toISOString(), private: privateBackup };
}

async function listBackups() {
  await ensureState();
  const entries = await fs.readdir(BACKUP_DIR, { withFileTypes: true });
  const rows = [];
  for (const entry of entries) {
    if (!entry.isFile() || !entry.name.endsWith('.zip')) continue;
    const full = path.join(BACKUP_DIR, entry.name);
    const info = await fs.stat(full);
    rows.push({
      name: entry.name,
      sizeBytes: info.size,
      createdAt: info.mtime.toISOString(),
      private: entry.name.startsWith('private-'),
    });
  }
  return rows.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

function safeBackupName(name) {
  const raw = String(name || '');
  const clean = path.basename(raw);
  if (clean !== raw || !clean.endsWith('.zip')) throw Object.assign(new Error('Invalid backup name'), { statusCode: 400 });
  return clean;
}

async function deleteBackup(name) {
  const clean = safeBackupName(name);
  const target = path.join(BACKUP_DIR, clean);
  const info = await fs.stat(target);
  if (!info.isFile()) throw Object.assign(new Error('Backup is not a file'), { statusCode: 400 });
  await fs.unlink(target);
  await recordActivity('server:backup.delete', { name: clean, private: clean.startsWith('private-') });
  return { ok: true, name: clean };
}

function validateRestoreEntry(name) {
  const clean = cleanZipEntry(name);
  const segments = clean.split('/').filter(Boolean);
  if (segments.some(isProtectedName)) {
    throw Object.assign(new Error('Backup contains a protected path'), { statusCode: 400 });
  }
  return clean;
}

async function assertSafeRestoreTree(root) {
  const entries = await fs.readdir(root, { withFileTypes: true });
  for (const entry of entries) {
    if (isProtectedName(entry.name)) {
      throw Object.assign(new Error('Backup contains a protected path'), { statusCode: 400 });
    }
    const full = path.join(root, entry.name);
    const info = await fs.lstat(full);
    if (info.isSymbolicLink()) {
      throw Object.assign(new Error('Backup restore refuses symbolic links'), { statusCode: 400 });
    }
    if (info.isDirectory()) await assertSafeRestoreTree(full);
    else if (!info.isFile()) throw Object.assign(new Error('Backup contains an unsupported file type'), { statusCode: 400 });
  }
}

async function restoreProjectBackup(name) {
  const clean = safeBackupName(name);
  if (!clean.startsWith('project-')) {
    throw Object.assign(new Error('Only source/project backups can be restored automatically'), { statusCode: 400 });
  }
  const archive = path.join(BACKUP_DIR, clean);
  const info = await fs.stat(archive);
  if (!info.isFile()) throw Object.assign(new Error('Backup is not a file'), { statusCode: 400 });

  const entries = await inspectZipArchive(archive);
  entries.forEach(validateRestoreEntry);

  const safetyBackup = await createProjectBackup(false);
  await ensureState();
  const staging = path.join(STATE_DIR, 'restore-' + crypto.randomUUID());
  let wasActive = false;
  try {
    await fs.mkdir(staging, { recursive: false });
    await exec('unzip', ['-oq', archive, '-d', staging], {
      timeout: 5 * 60_000,
      maxBuffer: 8 * 1024 * 1024,
    });
    await assertSafeRestoreTree(staging);

    wasActive = (await serviceState()) === 'active';
    if (wasActive) await controlManagedService('stop');

    const entries = await fs.readdir(staging, { withFileTypes: true });
    for (const entry of entries) {
      const source = path.join(staging, entry.name);
      const destination = safeProjectPath(entry.name);
      await fs.cp(source, destination, {
        recursive: entry.isDirectory(),
        force: true,
        preserveTimestamps: true,
      });
    }

    await recordActivity('server:backup.restore', {
      name: clean,
      safetyBackup: safetyBackup.name,
      mode: 'source-overlay',
    });
    return {
      ok: true,
      name: clean,
      safetyBackup: safetyBackup.name,
      restarted: wasActive,
    };
  } finally {
    await fs.rm(staging, { recursive: true, force: true }).catch(() => {});
    if (wasActive) {
      await controlManagedService('start').catch(async error => {
        await recordActivity('server:backup.restore-restart-failed', {
          name: clean,
          error: error?.message || String(error),
        });
      });
    }
  }
}

async function streamDownload(res, target, downloadName, contentType, activityAction, detail = {}) {
  const info = await fs.stat(target);
  if (!info.isFile()) throw Object.assign(new Error('Not a file'), { statusCode: 400 });
  if (info.size > MAX_TRANSFER_BYTES) {
    throw Object.assign(new Error('File exceeds configured transfer limit'), { statusCode: 413 });
  }
  res.writeHead(200, {
    'content-type': contentType,
    'content-length': info.size,
    'content-disposition': 'attachment; filename="' + downloadName.replace(/"/g, '') + '"',
    'cache-control': 'no-store',
  });
  try {
    await pipeline(createReadStream(target), res);
  } catch (error) {
    if (error?.code === 'ERR_STREAM_PREMATURE_CLOSE' || error?.code === 'ECONNRESET') return;
    throw error;
  }
  await recordActivity(activityAction, { ...detail, bytes: info.size });
}

async function sendBackup(res, name) {
  const clean = safeBackupName(name);
  const target = path.join(BACKUP_DIR, clean);
  return streamDownload(res, target, clean, 'application/zip', 'server:backup.download', { name: clean });
}

async function startupInfo() {
  let gitRepository = GIT_REPOSITORY;
  let gitBranch = GIT_BRANCH;
  if (!gitRepository) {
    try {
      gitRepository = (await exec('git', ['-C', PROJECT_ROOT, 'config', '--get', 'remote.origin.url'], { timeout: 5000 })).stdout.trim();
    } catch {}
  }
  if (!gitBranch) {
    try {
      gitBranch = (await exec('git', ['-C', PROJECT_ROOT, 'rev-parse', '--abbrev-ref', 'HEAD'], { timeout: 5000 })).stdout.trim();
    } catch {}
  }

  let additionalNodePackages = [];
  try {
    const pkg = JSON.parse(await fs.readFile(path.join(PROJECT_ROOT, 'package.json'), 'utf8'));
    additionalNodePackages = Object.keys(pkg?.dependencies || {}).sort();
  } catch {}

  let startupMode = 'unknown';
  try {
    startupMode = (await exec('systemctl', ['is-enabled', MANAGED_SERVICE], { timeout: 5000 })).stdout.trim() || 'unknown';
  } catch (error) {
    startupMode = String(error?.stdout || '').trim() || 'disabled';
  }

  return {
    runtime: 'Node.js',
    version: process.version.replace(/^v/, ''),
    entryFile: ENTRY_FILE,
    startCommand: START_COMMAND,
    projectRoot: PROJECT_ROOT,
    service: MANAGED_SERVICE,
    startupMode,
    gitRepository,
    gitBranch,
    additionalNodePackages,
  };
}

async function setStartupEnabled(enabled) {
  await controlManagedService(enabled ? 'enable' : 'disable');
  await recordActivity('server:startup.update', { service: MANAGED_SERVICE, enabled });
  return startupInfo();
}


async function discoveredModules() {
  let entries = [];
  try {
    entries = await fs.readdir(MODULES_DIR, { withFileTypes: true });
  } catch (error) {
    if (error?.code === 'ENOENT') return [];
    throw error;
  }

  const result = [];
  for (const entry of entries) {
    if (!entry.isDirectory() || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(entry.name)) continue;
    const directory = path.join(MODULES_DIR, entry.name);
    let metadata = {};
    for (const filename of ['bailey.module.json', 'module.json', 'package.json']) {
      try {
        metadata = JSON.parse(await fs.readFile(path.join(directory, filename), 'utf8'));
        break;
      } catch (error) {
        if (error?.code !== 'ENOENT' && !(error instanceof SyntaxError)) throw error;
      }
    }
    result.push({
      id: entry.name,
      displayName: String(metadata.displayName || metadata.name || entry.name).slice(0, 96),
      version: String(metadata.version || ''),
      status: 'discovered',
      enabled: metadata.enabled !== false,
      commands: Array.isArray(metadata.commands)
        ? metadata.commands.map((command) => String(command?.name || command?.id || command)).filter(Boolean).slice(0, 128)
        : [],
      configuration: Array.isArray(metadata.configuration)
        ? metadata.configuration
        : Array.isArray(metadata.settings) ? metadata.settings : [],
      loadError: '',
      lastReload: '',
      moduleDirectory: path.relative(PROJECT_ROOT, directory) || entry.name,
      dependencies: metadata.dependencies && typeof metadata.dependencies === 'object'
        ? Object.keys(metadata.dependencies).slice(0, 128)
        : [],
      permissions: Array.isArray(metadata.permissions) ? metadata.permissions.map(String).slice(0, 128) : [],
    });
  }
  return result.sort((a, b) => a.displayName.localeCompare(b.displayName));
}

function normalizeRuntimeRegistry(raw, fallbackModules) {
  const normalizeModule = (row) => ({
    id: String(row.id),
    displayName: String(row.displayName || row.name || row.id).slice(0, 96),
    version: String(row.version || '').slice(0, 48),
    status: String(row.status || 'unknown').slice(0, 48),
    enabled: row.enabled !== false,
    commands: Array.isArray(row.commands) ? row.commands.map(String).slice(0, 128) : [],
    configuration: Array.isArray(row.configuration) ? row.configuration : [],
    loadError: String(row.loadError || row.error || '').slice(0, 2000),
    lastReload: String(row.lastReload || ''),
    moduleDirectory: String(row.moduleDirectory || row.directory || '').slice(0, 512),
    dependencies: Array.isArray(row.dependencies) ? row.dependencies.map(String).slice(0, 128) : [],
    permissions: Array.isArray(row.permissions) ? row.permissions.map(String).slice(0, 128) : [],
  });

  const runtimeModules = (Array.isArray(raw?.modules) ? raw.modules : [])
    .filter((row) => row && /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(String(row.id || '')))
    .map(normalizeModule);
  const known = new Set(runtimeModules.map((row) => row.id));
  const discoveredOnly = fallbackModules
    .filter((row) => !known.has(String(row.id)))
    .map(normalizeModule);
  const modules = runtimeModules.length ? [...runtimeModules, ...discoveredOnly] : fallbackModules.map(normalizeModule);
  modules.sort((a, b) => a.displayName.localeCompare(b.displayName));

  const commands = (Array.isArray(raw?.commands) ? raw.commands : [])
    .filter((row) => row && typeof row.name === 'string')
    .map((row) => ({
      name: String(row.name).slice(0, 96),
      moduleId: String(row.moduleId || row.module || '').slice(0, 64),
      description: String(row.description || '').slice(0, 1000),
      aliases: Array.isArray(row.aliases) ? row.aliases.map(String).slice(0, 64) : [],
      enabled: row.enabled !== false,
      permission: String(row.permission || row.access || '').slice(0, 96),
      usage: String(row.usage || '').slice(0, 1000),
      error: String(row.error || '').slice(0, 2000),
      scope: String(row.scope || row.namespace || '').slice(0, 24),
      capability: String(row.capability || row.category || '').slice(0, 64),
    }));
  return {
    version: Number(raw?.version) || 1,
    generatedAt: String(raw?.generatedAt || ''),
    source: runtimeModules.length ? (discoveredOnly.length ? 'runtime+filesystem' : 'runtime') : 'filesystem',
    modules,
    commands,
  };
}

async function runtimeRegistry() {
  const fallbackModules = await discoveredModules();
  try {
    const raw = JSON.parse(await fs.readFile(RUNTIME_REGISTRY_FILE, 'utf8'));
    return normalizeRuntimeRegistry(raw, fallbackModules);
  } catch (error) {
    if (error?.code !== 'ENOENT' && !(error instanceof SyntaxError)) throw error;
    return normalizeRuntimeRegistry({}, fallbackModules);
  }
}
async function commandSettings() {
  let schema;
  let values = {};
  try {
    schema = JSON.parse(await fs.readFile(COMMAND_SETTINGS_SCHEMA_FILE, 'utf8'));
  } catch (error) {
    if (error?.code === 'ENOENT') return [];
    throw error;
  }
  try {
    values = JSON.parse(await fs.readFile(COMMAND_SETTINGS_FILE, 'utf8'));
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }
  const entries = Array.isArray(schema?.entries) ? schema.entries : [];
  return entries
    .filter((entry) => entry && entry.type === 'boolean' && typeof entry.key === 'string')
    .map((entry) => ({
      key: entry.key,
      label: String(entry.label || entry.key),
      description: String(entry.description || ''),
      command: String(entry.command || ''),
      enabled: typeof values?.[entry.key] === 'boolean' ? values[entry.key] : entry.default === true,
    }));
}

async function setCommandSetting(key, enabled) {
  const entries = await commandSettings();
  if (!entries.some((entry) => entry.key === key)) {
    throw Object.assign(new Error('Unknown command setting'), { statusCode: 400 });
  }
  let values = {};
  try {
    values = JSON.parse(await fs.readFile(COMMAND_SETTINGS_FILE, 'utf8'));
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }
  await fs.mkdir(path.dirname(COMMAND_SETTINGS_FILE), { recursive: true });
  const temp = COMMAND_SETTINGS_FILE + '.cortex-' + crypto.randomUUID() + '.tmp';
  values = { ...values, version: 1, [key]: enabled === true, savedAt: Date.now() };
  await fs.writeFile(temp, JSON.stringify(values, null, 2), 'utf8');
  await fs.rename(temp, COMMAND_SETTINGS_FILE);
  await recordActivity('server:setting.update', { key, enabled: enabled === true });
  return commandSettings();
}


async function environmentSchema() {
  let entries = DEFAULT_ENVIRONMENT_SCHEMA;
  try {
    const custom = JSON.parse(await fs.readFile(ENVIRONMENT_SCHEMA_FILE, 'utf8'));
    if (Array.isArray(custom?.entries)) entries = custom.entries;
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }

  const allowed = new Set(DEFAULT_ENVIRONMENT_SCHEMA.map((entry) => entry.key));
  return entries
    .filter((entry) =>
      entry &&
      allowed.has(String(entry.key || '')) &&
      /^[A-Z][A-Z0-9_]{0,95}$/.test(String(entry.key || ''))
    )
    .map((entry) => ({
      key: String(entry.key),
      label: String(entry.label || entry.key).slice(0, 96),
      description: String(entry.description || '').slice(0, 500),
      secret: entry.secret === true,
      type: String(entry.type || 'string'),
      min: Number.isFinite(Number(entry.min)) ? Number(entry.min) : null,
      max: Number.isFinite(Number(entry.max)) ? Number(entry.max) : null,
      values: Array.isArray(entry.values) ? entry.values.map(String).slice(0, 64) : [],
      requiresRestart: entry.requiresRestart !== false,
    }));
}

function unquoteEnvironmentValue(value) {
  const text = String(value || '').trim();
  if (text.length >= 2 && ((text.startsWith('"') && text.endsWith('"')) || (text.startsWith("'") && text.endsWith("'")))) {
    return text.slice(1, -1);
  }
  return text;
}

async function managedEnvironmentValues() {
  let content = '';
  try {
    content = await fs.readFile(MANAGED_ENV_FILE, 'utf8');
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }
  const values = {};
  for (const line of content.split(/\r?\n/)) {
    const match = line.match(/^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/);
    if (!match) continue;
    values[match[1]] = unquoteEnvironmentValue(match[2]);
  }
  return values;
}

function validateEnvironmentValue(entry, input) {
  const value = String(input ?? '');
  if (value.length > 4096 || /[\u0000\r\n]/.test(value)) {
    throw Object.assign(new Error('Environment value is invalid'), { statusCode: 400 });
  }
  if (entry.type === 'integer' && value !== '') {
    if (!/^-?\d+$/.test(value)) throw Object.assign(new Error('Environment value must be an integer'), { statusCode: 400 });
    const number = Number(value);
    if ((entry.min != null && number < entry.min) || (entry.max != null && number > entry.max)) {
      throw Object.assign(new Error('Environment value is outside the allowed range'), { statusCode: 400 });
    }
  }
  if (entry.type === 'enum' && value !== '' && !entry.values.includes(value)) {
    throw Object.assign(new Error('Environment value is not an allowed option'), { statusCode: 400 });
  }
  if (entry.type === 'phone' && value !== '' && !/^\d{7,15}$/.test(value)) {
    throw Object.assign(new Error('Environment value must be a phone number with country code'), { statusCode: 400 });
  }
  if (entry.type === 'phones' && value !== '') {
    const rows = value.split(',').map((row) => row.trim()).filter(Boolean);
    if (!rows.length || rows.some((row) => !/^\d{7,15}$/.test(row))) {
      throw Object.assign(new Error('Environment value must contain comma-separated phone numbers'), { statusCode: 400 });
    }
  }
  return value;
}

async function environmentState(restartRequired = false) {
  const [schema, values] = await Promise.all([environmentSchema(), managedEnvironmentValues()]);
  return {
    restartRequired,
    entries: schema.map((entry) => {
      const value = Object.prototype.hasOwnProperty.call(values, entry.key) ? String(values[entry.key]) : '';
      return {
        key: entry.key,
        label: entry.label,
        description: entry.description,
        secret: entry.secret,
        hasValue: value.length > 0,
        value: entry.secret ? '' : value,
        requiresRestart: entry.requiresRestart,
      };
    }),
  };
}

async function writeEnvironmentDirect(key, value) {
  let content = '';
  try {
    content = await fs.readFile(MANAGED_ENV_FILE, 'utf8');
  } catch (error) {
    if (error?.code !== 'ENOENT') throw error;
  }
  const lines = content.split(/\r?\n/);
  let replaced = false;
  const next = lines.map((line) => {
    if (line.startsWith(key + '=')) {
      replaced = true;
      return key + '=' + value;
    }
    return line;
  });
  if (!replaced) next.push(key + '=' + value);
  while (next.length && next[next.length - 1] === '') next.pop();

  await fs.mkdir(path.dirname(MANAGED_ENV_FILE), { recursive: true });
  const temp = MANAGED_ENV_FILE + '.cortex-' + crypto.randomUUID() + '.tmp';
  try {
    await fs.writeFile(temp, next.join('\n') + '\n', { encoding: 'utf8', mode: 0o600 });
    await fs.chmod(temp, 0o600);
    await fs.rename(temp, MANAGED_ENV_FILE);
  } catch (error) {
    await fs.rm(temp, { force: true }).catch(() => {});
    throw error;
  }
}

async function writeEnvironmentViaHelper(key, value) {
  await new Promise((resolve, reject) => {
    const child = spawn('/usr/bin/sudo', ['-n', SERVICE_CONTROL_HELPER, 'env-set', key], {
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    let stderr = '';
    child.stderr.on('data', (chunk) => { stderr += chunk.toString('utf8'); });
    child.once('error', reject);
    child.once('exit', (code) => {
      if (code === 0) return resolve();
      reject(Object.assign(new Error('Environment update helper failed'), {
        statusCode: 500,
        cause: stderr.slice(0, 500),
      }));
    });
    child.stdin.end(value);
  });
}

async function setEnvironmentValue(key, input) {
  const schema = await environmentSchema();
  const entry = schema.find((row) => row.key === key);
  if (!entry) throw Object.assign(new Error('Environment variable is not managed by Cortex'), { statusCode: 400 });
  const value = validateEnvironmentValue(entry, input);

  try {
    await writeEnvironmentDirect(entry.key, value);
  } catch (error) {
    if (!['EACCES', 'EPERM', 'EROFS'].includes(error?.code)) throw error;
    await writeEnvironmentViaHelper(entry.key, value);
  }

  await recordActivity('server:environment.update', {
    key: entry.key,
    restartRequired: entry.requiresRestart,
  });
  return environmentState(entry.requiresRestart);
}

async function revealEnvironmentValue(key) {
  const schema = await environmentSchema();
  const entry = schema.find((row) => row.key === key);
  if (!entry) throw Object.assign(new Error('Environment variable is not managed by Cortex'), { statusCode: 400 });
  const values = await managedEnvironmentValues();
  await recordActivity('server:environment.reveal', { key: entry.key });
  return String(values[entry.key] || '');
}


async function serviceState() {
  try {
    const { stdout } = await exec('systemctl', ['is-active', MANAGED_SERVICE]);
    return stdout.trim() || 'unknown';
  } catch (error) {
    return String(error?.stdout || '').trim() || 'inactive';
  }
}

async function sampleSystemCpu() {
  const sample = () => os.cpus().map((cpu) => {
    const times = cpu.times;
    const total = Object.values(times).reduce((sum, value) => sum + value, 0);
    return { idle: times.idle, total };
  });
  const first = sample();
  await new Promise((resolve) => setTimeout(resolve, 160));
  const second = sample();
  let idle = 0;
  let total = 0;
  for (let i = 0; i < Math.min(first.length, second.length); i += 1) {
    idle += second[i].idle - first[i].idle;
    total += second[i].total - first[i].total;
  }
  return total > 0 ? Math.max(0, Math.min(100, (1 - idle / total) * 100)) : null;
}

async function serviceCpuPercent(state) {
  if (state !== 'active') return 0;
  const read = async () => {
    const { stdout } = await exec('systemctl', ['show', MANAGED_SERVICE, '--property=CPUUsageNSec', '--value'], { timeout: 5000 });
    const value = Number(stdout.trim());
    return Number.isFinite(value) && value >= 0 ? value : null;
  };
  try {
    const first = await read();
    if (first == null) return sampleSystemCpu();
    const started = process.hrtime.bigint();
    await new Promise((resolve) => setTimeout(resolve, 220));
    const second = await read();
    const elapsed = Number(process.hrtime.bigint() - started);
    if (second == null || elapsed <= 0 || second < first) return sampleSystemCpu();
    return Math.max(0, Math.min(os.cpus().length * 100, ((second - first) / elapsed) * 100));
  } catch {
    return sampleSystemCpu();
  }
}

async function serviceMemoryBytes(state) {
  if (state !== 'active') return 0;
  try {
    const { stdout } = await exec('systemctl', ['show', MANAGED_SERVICE, '--property=MemoryCurrent', '--value'], { timeout: 5000 });
    const value = Number(stdout.trim());
    return Number.isFinite(value) && value >= 0 ? value : null;
  } catch {
    return null;
  }
}

async function serviceUptimeMs(state) {
  if (state !== 'active') return 0;
  try {
    const { stdout } = await exec('systemctl', ['show', MANAGED_SERVICE, '--property=ActiveEnterTimestamp', '--value'], { timeout: 5000 });
    const at = Date.parse(stdout.trim());
    return Number.isFinite(at) ? Math.max(0, Date.now() - at) : 0;
  } catch {
    return 0;
  }
}

async function diskStats() {
  const stats = await fs.statfs(PROJECT_ROOT);
  const block = Number(stats.bsize);
  const total = Number(stats.blocks) * block;
  const free = Number(stats.bavail) * block;
  return { total, used: Math.max(0, total - free) };
}

async function hostStatus() {
  const state = await serviceState();
  const [cpuPercent, serviceMemory, disk, uptimeMs] = await Promise.all([
    serviceCpuPercent(state),
    serviceMemoryBytes(state),
    diskStats(),
    serviceUptimeMs(state),
  ]);
  return {
    state,
    cpuPercent,
    memoryUsedBytes: serviceMemory ?? Math.max(0, os.totalmem() - os.freemem()),
    memoryLimitBytes: os.totalmem(),
    diskUsedBytes: disk.used,
    diskLimitBytes: disk.total,
    uptimeMs,
    runtime: {
      runtime: 'Node.js',
      version: process.version.replace(/^v/, ''),
      entryFile: ENTRY_FILE,
      startCommand: START_COMMAND,
    },
  };
}

async function logs(limit) {
  const safeLimit = Math.max(20, Math.min(1000, Number(limit) || 200));
  const { stdout } = await exec('journalctl', ['-u', MANAGED_SERVICE, '-n', String(safeLimit), '--no-pager', '-o', 'short-iso-precise'], {
    maxBuffer: 4 * 1024 * 1024,
  });
  return stdout.split(/\r?\n/).filter(Boolean).map(redactLogLine);
}

async function streamLogs(req, res, initialLimit = 120) {
  res.writeHead(200, {
    'content-type': 'text/event-stream; charset=utf-8',
    'cache-control': 'no-store',
    'connection': 'keep-alive',
    'x-accel-buffering': 'no',
  });
  if (typeof res.flushHeaders === 'function') res.flushHeaders();

  const send = (line) => {
    if (!res.destroyed) res.write('data: ' + JSON.stringify({ line: redactLogLine(line) }) + '\n\n');
  };

  const initialCount = Math.max(0, Math.min(500, Number(initialLimit) || 0));
  if (initialCount > 0) {
    try {
      const initial = await logs(initialCount);
      initial.forEach(send);
    } catch (error) {
      send('[Cortex] Unable to read initial journal: ' + (error?.message || error));
    }
  }

  const child = spawn('journalctl', ['-u', MANAGED_SERVICE, '-f', '-n', '0', '--no-pager', '-o', 'short-iso-precise'], {
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let pending = '';

  child.stdout.on('data', (chunk) => {
    pending += chunk.toString('utf8');
    const rows = pending.split(/\r?\n/);
    pending = rows.pop() || '';
    rows.filter(Boolean).forEach(send);
  });
  child.stderr.on('data', (chunk) => {
    const message = chunk.toString('utf8').trim();
    if (message) send('[journalctl] ' + message);
  });

  const heartbeat = setInterval(() => {
    if (!res.destroyed) res.write(': heartbeat\n\n');
  }, 15_000);
  heartbeat.unref?.();

  let closed = false;
  const close = () => {
    if (closed) return;
    closed = true;
    clearInterval(heartbeat);
    if (!child.killed) child.kill('SIGTERM');
    if (!res.destroyed) res.end();
  };

  req.once('close', close);
  res.once('close', close);
  child.once('error', (error) => {
    send('[Cortex] Log stream error: ' + (error?.message || error));
    close();
  });
  child.once('exit', close);
}

async function power(action) {
  if (!['start', 'stop', 'restart'].includes(action)) {
    throw Object.assign(new Error('Invalid power action'), { statusCode: 400 });
  }
  await controlManagedService(action);
  await recordActivity('server:power.' + action, { service: MANAGED_SERVICE });
  return hostStatus();
}

async function listFiles(inputPath) {
  const target = safeProjectPath(inputPath);
  const entries = await fs.readdir(target, { withFileTypes: true });
  const result = [];
  for (const entry of entries) {
    if (isProtectedName(entry.name)) continue;
    const full = path.join(target, entry.name);
    const stat = await fs.lstat(full);
    result.push({
      name: entry.name,
      type: fileType(entry),
      sizeBytes: stat.isFile() ? stat.size : 0,
      modifiedAt: stat.mtime.toISOString(),
    });
  }
  return result.sort((a, b) => {
    if (a.type === b.type) return a.name.localeCompare(b.name);
    if (a.type === 'directory') return -1;
    if (b.type === 'directory') return 1;
    return a.name.localeCompare(b.name);
  });
}

async function readText(inputPath) {
  const target = safeProjectPath(inputPath);
  await assertNoSymlink(target);
  const stat = await fs.stat(target);
  if (!stat.isFile()) throw Object.assign(new Error('Not a file'), { statusCode: 400 });
  if (stat.size > MAX_TEXT_BYTES) throw Object.assign(new Error('File too large for text editor'), { statusCode: 413 });
  return fs.readFile(target, 'utf8');
}


async function sendProjectFile(res, inputPath) {
  const target = safeProjectPath(inputPath);
  await assertNoSymlink(target);
  const name = path.basename(target).replace(/"/g, '');
  return streamDownload(
    res,
    target,
    name,
    'application/octet-stream',
    'server:file.download',
    { path: path.relative(PROJECT_ROOT, target) },
  );
}

async function writeRawFile(req, inputPath) {
  const target = safeProjectPath(inputPath);
  await assertNoSymlink(target);
  await fs.mkdir(path.dirname(target), { recursive: true });

  const declaredLength = Number(req.headers['content-length'] || 0);
  if (
    declaredLength < 0 ||
    !Number.isSafeInteger(declaredLength) ||
    declaredLength > MAX_TRANSFER_BYTES
  ) {
    req.resume();
    throw Object.assign(new Error('File exceeds configured transfer limit'), { statusCode: 413 });
  }

  const temp = `${target}.cortex-${crypto.randomUUID()}.upload`;
  let handle = null;
  let bytes = 0;
  try {
    handle = await fs.open(temp, 'wx', 0o600);
    for await (const chunk of req) {
      bytes += chunk.length;
      if (bytes > MAX_TRANSFER_BYTES) {
        req.resume();
        throw Object.assign(new Error('File exceeds configured transfer limit'), { statusCode: 413 });
      }
      let offset = 0;
      while (offset < chunk.length) {
        const { bytesWritten } = await handle.write(chunk, offset, chunk.length - offset);
        if (bytesWritten <= 0) throw new Error('Unable to persist upload chunk');
        offset += bytesWritten;
      }
    }
    await handle.sync();
    await handle.close();
    handle = null;
    await fs.rename(temp, target);
  } catch (error) {
    if (handle) await handle.close().catch(() => {});
    await fs.rm(temp, { force: true }).catch(() => {});
    throw error;
  }

  await recordActivity('server:file.uploaded', {
    path: path.relative(PROJECT_ROOT, target),
    bytes,
  });
  return bytes;
}

async function writeText(inputPath, content) {
  if (typeof content !== 'string') throw Object.assign(new Error('content must be text'), { statusCode: 400 });
  if (Buffer.byteLength(content, 'utf8') > MAX_TEXT_BYTES) {
    throw Object.assign(new Error('File too large for text editor'), { statusCode: 413 });
  }
  const target = safeProjectPath(inputPath);
  await assertNoSymlink(target);
  await fs.mkdir(path.dirname(target), { recursive: true });
  const temp = `${target}.cortex-${crypto.randomUUID()}.tmp`;
  await fs.writeFile(temp, content, 'utf8');
  await fs.rename(temp, target);
  await recordActivity('server:file.write', { path: path.relative(PROJECT_ROOT, target), bytes: Buffer.byteLength(content, 'utf8') });
}

async function writeBinary(inputPath, contentBase64) {
  if (typeof contentBase64 !== 'string') throw Object.assign(new Error('contentBase64 must be text'), { statusCode: 400 });
  const bytes = Buffer.from(contentBase64, 'base64');
  if (bytes.length > MAX_FILE_BYTES) throw Object.assign(new Error('File exceeds 10 MB limit'), { statusCode: 413 });
  const target = safeProjectPath(inputPath);
  await assertNoSymlink(target);
  await fs.mkdir(path.dirname(target), { recursive: true });
  const temp = `${target}.cortex-${crypto.randomUUID()}.tmp`;
  await fs.writeFile(temp, bytes, { flag: 'wx' });
  await fs.rename(temp, target);
  await recordActivity('server:file.uploaded', { path: path.relative(PROJECT_ROOT, target), bytes: bytes.length });
}

async function installDependencies() {
  const packagePath = path.join(PROJECT_ROOT, 'package.json');
  try {
    await fs.access(packagePath);
  } catch {
    throw Object.assign(new Error('package.json is missing'), { statusCode: 400 });
  }
  let hasLock = false;
  try {
    await fs.access(path.join(PROJECT_ROOT, 'package-lock.json'));
    hasLock = true;
  } catch {
    // A package-lock is optional for legacy projects.
  }
  const args = hasLock ? ['ci', '--omit=dev'] : ['install', '--omit=dev'];
  const { stdout, stderr } = await exec('npm', args, {
    cwd: PROJECT_ROOT,
    timeout: 5 * 60_000,
    maxBuffer: 8 * 1024 * 1024,
  });
  const message = (stdout || stderr || 'Dependencies installed').trim().slice(-1200);
  await recordActivity('server:dependencies.install', { message });
  return { message };
}

async function assertNoSymlink(target) {
  let cursor = PROJECT_ROOT;
  const parts = path.relative(PROJECT_ROOT, target).split(path.sep).filter(Boolean);
  for (const part of parts) {
    cursor = path.join(cursor, part);
    try {
      const stat = await fs.lstat(cursor);
      if (stat.isSymbolicLink()) throw Object.assign(new Error('Symlinks are not supported'), { statusCode: 403 });
    } catch (error) {
      if (error?.code === 'ENOENT') return;
      throw error;
    }
  }
}

async function msccControl(method, pathname, body = null) {
  try {
    const response = await fetch(MSCC_CONTROL_URL + pathname, {
      method,
      headers: body ? { 'content-type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
      signal: AbortSignal.timeout(15_000),
    });
    const text = await response.text();
    let payload = {};
    try { payload = text ? JSON.parse(text) : {}; } catch { payload = { error: text || 'Invalid MSCC response' }; }
    if (!response.ok) {
      throw Object.assign(new Error(payload.error || ('MSCC control HTTP ' + response.status)), { statusCode: 502 });
    }
    return payload;
  } catch (error) {
    if (error?.statusCode) throw error;
    throw Object.assign(new Error('MSCC pairing control unavailable: ' + (error?.message || error)), { statusCode: 502 });
  }
}

async function handler(req, res) {
  try {
    if (!authorized(req)) return json(res, 401, { error: 'Unauthorized' });
    const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);

    if (req.method === 'GET' && url.pathname === '/api/cortex/host/status') {
      return json(res, 200, await hostStatus());
    }
    if (req.method === 'GET' && url.pathname === '/api/cortex/mscc/pairing') {
      return json(res, 200, await msccControl('GET', '/state'));
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/mscc/accounts') {
      const body = await readJson(req);
      const phoneNumber = String(body.phoneNumber || '').replace(/\D/g, '');
      const displayName = String(body.displayName || '').trim().slice(0, 48);
      if (!/^\d{7,15}$/.test(phoneNumber)) {
        throw Object.assign(new Error('Phone number must contain 7 to 15 digits'), { statusCode: 400 });
      }
      const result = await msccControl('POST', '/accounts', { phoneNumber, displayName });
      await recordActivity('mscc:account.create', { account: result?.account?.id || '', displayName });
      return json(res, 200, result);
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/mscc/destination') {
      const body = await readJson(req);
      const account = String(body.account || '').trim();
      if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/.test(account)) {
        throw Object.assign(new Error('Invalid destination account ID'), { statusCode: 400 });
      }
      const result = await msccControl('POST', '/destination', { account });
      await recordActivity('mscc:destination.update', { account });
      return json(res, 200, result);
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/mscc/commands/reload') {
      try {
        const result = await msccControl('POST', '/commands/reload', {});
        await recordActivity('mscc:commands.reload', { count: Array.isArray(result.commands) ? result.commands.length : 0 });
        return json(res, 200, result);
      } catch (error) {
        await recordActivity('mscc:commands.reload-failed', { error: 'Command reload failed.' });
        throw error;
      }
    }
    if (req.method === 'GET' && url.pathname === '/api/cortex/mscc/registry') {
      return json(res, 200, await runtimeRegistry());
    }
    const moduleReloadRoute = url.pathname.match(/^\/api\/cortex\/mscc\/modules\/([A-Za-z0-9][A-Za-z0-9._-]{0,63})\/reload$/);
    if (req.method === 'POST' && moduleReloadRoute) {
      const id = moduleReloadRoute[1];
      try {
        const result = await msccControl('POST', '/modules/' + id + '/reload', {});
        await recordActivity('mscc:module.reload', { module: id });
        return json(res, 200, result);
      } catch (error) {
        await recordActivity('mscc:module.reload-failed', {
          module: id,
          error: 'Module reload failed.',
        });
        throw error;
      }
    }
    const pairRoute = url.pathname.match(/^\/api\/cortex\/mscc\/accounts\/([A-Za-z0-9][A-Za-z0-9._-]{0,63})\/(pair|reconnect|disconnect|repair)$/);
    if (req.method === 'POST' && pairRoute) {
      const [, id, action] = pairRoute;
      const body = await readJson(req);
      if (action === 'pair') {
        const mode = body.mode === 'qr' ? 'qr' : 'code';
        const result = await msccControl('POST', '/accounts/' + id + '/pair', { mode });
        await recordActivity('mscc:pairing.pair', { account: id, mode });
        return json(res, 200, result);
      }
      if (action === 'reconnect') {
        const result = await msccControl('POST', '/accounts/' + id + '/reconnect', {});
        await recordActivity('mscc:pairing.reconnect', { account: id });
        return json(res, 200, result);
      }
      if (action === 'disconnect') {
        const result = await msccControl('POST', '/accounts/' + id + '/disconnect', {});
        await recordActivity('mscc:account.disconnect', { account: id });
        return json(res, 200, result);
      }
      const mode = body.mode === 'qr' ? 'qr' : 'code';
      const result = await msccControl('POST', '/accounts/' + id + '/repair', { mode });
      await recordActivity('mscc:pairing.repair', { account: id, mode });
      return json(res, 200, result);
    }

    const removeAccountRoute = url.pathname.match(/^\/api\/cortex\/mscc\/accounts\/([A-Za-z0-9][A-Za-z0-9._-]{0,63})$/);
    if (req.method === 'DELETE' && removeAccountRoute) {
      const id = removeAccountRoute[1];
      const result = await msccControl('DELETE', '/accounts/' + id);
      await recordActivity('mscc:account.remove', { account: id, authPreserved: result?.authPreserved === true });
      return json(res, 200, result);
    }
    if (req.method === 'GET' && url.pathname === '/api/cortex/host/environment') {
      return json(res, 200, await environmentState(false));
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/environment/reveal') {
      const body = await readJson(req);
      const key = String(body.key || '').trim();
      return json(res, 200, { key, value: await revealEnvironmentValue(key) });
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/environment') {
      const body = await readJson(req);
      const key = String(body.key || '').trim();
      return json(res, 200, await setEnvironmentValue(key, body.value));
    }
    if (req.method === 'GET' && url.pathname === '/api/cortex/host/logs') {
      return json(res, 200, { lines: await logs(url.searchParams.get('limit')) });
    }
    if (req.method === 'GET' && url.pathname === '/api/cortex/host/logs/stream') {
      return streamLogs(req, res, url.searchParams.get('initial'));
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/power') {
      const body = await readJson(req);
      return json(res, 200, await power(String(body.action || '')));
    }
    if (req.method === 'GET' && url.pathname === '/api/cortex/host/files') {
      return json(res, 200, { entries: await listFiles(url.searchParams.get('path') || '/') });
    }
    if (req.method === 'GET' && url.pathname === '/api/cortex/host/files/content') {
      return json(res, 200, { content: await readText(url.searchParams.get('path') || '') });
    }
    if (req.method === 'GET' && url.pathname === '/api/cortex/host/files/raw') {
      return sendProjectFile(res, url.searchParams.get('path') || '');
    }
    if (req.method === 'PUT' && url.pathname === '/api/cortex/host/files/raw') {
      const bytes = await writeRawFile(req, url.searchParams.get('path') || '');
      return json(res, 200, { ok: true, bytes });
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/files/content') {
      const body = await readJson(req);
      await writeText(String(body.path || ''), body.content);
      return json(res, 200, { ok: true });
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/files/binary') {
      const body = await readJson(req);
      await writeBinary(String(body.path || ''), body.contentBase64);
      return json(res, 200, { ok: true });
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/dependencies/install') {
      return json(res, 200, await installDependencies());
    }

    if (req.method === 'POST' && url.pathname === '/api/cortex/host/files/directory') {
      const body = await readJson(req);
      await makeDirectory(String(body.path || ''));
      return json(res, 200, { ok: true });
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/files/rename') {
      const body = await readJson(req);
      await renamePath(String(body.from || ''), String(body.to || ''));
      return json(res, 200, { ok: true });
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/files/copy') {
      const body = await readJson(req);
      await copyPath(String(body.from || ''), String(body.to || ''));
      return json(res, 200, { ok: true });
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/files/delete') {
      const body = await readJson(req);
      await deletePath(String(body.path || ''));
      return json(res, 200, { ok: true });
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/files/archive') {
      const body = await readJson(req);
      await archivePaths(body.paths, String(body.destination || ''));
      return json(res, 200, { ok: true });
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/files/extract') {
      const body = await readJson(req);
      await extractArchive(String(body.path || ''), String(body.destination || '/'));
      return json(res, 200, { ok: true });
    }
    if (req.method === 'GET' && url.pathname === '/api/cortex/host/startup') {
      return json(res, 200, await startupInfo());
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/startup') {
      const body = await readJson(req);
      if (typeof body.enabled !== 'boolean') {
        throw Object.assign(new Error('enabled must be boolean'), { statusCode: 400 });
      }
      return json(res, 200, await setStartupEnabled(body.enabled));
    }

    if (req.method === 'GET' && url.pathname === '/api/cortex/host/settings') {
      return json(res, 200, { entries: await commandSettings() });
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/settings') {
      const body = await readJson(req);
      if (typeof body.enabled !== 'boolean') {
        throw Object.assign(new Error('enabled must be boolean'), { statusCode: 400 });
      }
      return json(res, 200, { entries: await setCommandSetting(String(body.key || ''), body.enabled) });
    }
    if (req.method === 'GET' && url.pathname === '/api/cortex/host/activity') {
      return json(res, 200, { entries: await activity(url.searchParams.get('limit')) });
    }
    if (req.method === 'GET' && url.pathname === '/api/cortex/host/backups') {
      return json(res, 200, { entries: await listBackups() });
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/backups') {
      const body = await readJson(req);
      return json(res, 200, await createProjectBackup(body.private === true));
    }
    if (req.method === 'GET' && url.pathname === '/api/cortex/host/backups/content') {
      return sendBackup(res, url.searchParams.get('name') || '');
    }
    if (req.method === 'POST' && url.pathname === '/api/cortex/host/backups/restore') {
      const body = await readJson(req);
      return json(res, 200, await restoreProjectBackup(String(body.name || '')));
    }
    if (req.method === 'DELETE' && url.pathname === '/api/cortex/host/backups') {
      return json(res, 200, await deleteBackup(url.searchParams.get('name') || ''));
    }

    return json(res, 404, { error: 'Not found' });
  } catch (error) {
    // Never let a thrown transport/control error bypass the same secret
    // redaction used for journal output.
    console.error(redactLogLine(error?.stack || error?.message || error));
    if (res.headersSent || res.destroyed) {
      if (!res.destroyed && !res.writableEnded) res.end();
      return;
    }
    const statusCode = Number(error?.statusCode) || 500;
    return json(res, statusCode, { error: publicErrorMessage(statusCode) });
  }
}

await fs.mkdir(PROJECT_ROOT, { recursive: true });
await ensureState();
const server = http.createServer(handler);
server.listen(PORT, HOST, () => {
  console.log(`Cortex Agent listening on http://${HOST}:${PORT}`);
  console.log(`Project root: ${PROJECT_ROOT}`);
  console.log(`Managed service: ${MANAGED_SERVICE}`);
});
