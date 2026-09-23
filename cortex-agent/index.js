import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { promises as fs } from 'node:fs';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const exec = promisify(execFile);
const PORT = Number(process.env.PORT || 47831);
const HOST = process.env.HOST || '127.0.0.1';
const TOKEN = process.env.CORTEX_AGENT_TOKEN || '';
const PROJECT_ROOT = path.resolve(process.env.CORTEX_PROJECT_ROOT || process.env.NIGHT_ROOT || '/opt/night');
const MANAGED_SERVICE = process.env.CORTEX_SERVICE || process.env.NIGHT_SERVICE || 'night.service';
const ENTRY_FILE = process.env.CORTEX_ENTRY || process.env.NIGHT_ENTRY || 'index.js';
const START_COMMAND = process.env.CORTEX_START_COMMAND || process.env.NIGHT_START_COMMAND || 'node index.js';
const GIT_REPOSITORY = process.env.CORTEX_GIT_REPO || '';
const GIT_BRANCH = process.env.CORTEX_GIT_BRANCH || '';
const STATE_DIR = path.resolve(process.env.CORTEX_STATE_DIR || path.join(PROJECT_ROOT, '.cortex'));
const ACTIVITY_FILE = path.join(STATE_DIR, 'activity.jsonl');
const BACKUP_DIR = path.join(STATE_DIR, 'backups');
const COMMAND_SETTINGS_FILE = path.resolve(process.env.CORTEX_COMMAND_SETTINGS_FILE || '/var/lib/mscc/data/mscc-settings.json');
const COMMAND_SETTINGS_SCHEMA_FILE = path.resolve(process.env.CORTEX_COMMAND_SETTINGS_SCHEMA_FILE || '/var/lib/mscc/data/cortex-settings-schema.json');
const MSCC_CONTROL_URL = 'http://127.0.0.1:8788';
const PRIVATE_BACKUP_PATHS = String(process.env.CORTEX_PRIVATE_BACKUP_PATHS || '')
  .split(':')
  .map((value) => value.trim())
  .filter(Boolean)
  .map((value) => path.resolve(value));
const MAX_BODY = 16 * 1024 * 1024;
const MAX_FILE_BYTES = 10 * 1024 * 1024;
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
  const decoded = decodeURIComponent(input);
  const relative = decoded.replace(/^\/+/, '');
  const target = path.resolve(PROJECT_ROOT, relative);
  if (target !== PROJECT_ROOT && !target.startsWith(PROJECT_ROOT + path.sep)) {
    throw Object.assign(new Error('Path escapes managed project'), { statusCode: 400 });
  }
  const segments = path.relative(PROJECT_ROOT, target).split(path.sep).filter(Boolean);
  if (segments.some(isProtectedName)) {
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
    detail,
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

async function activity(limit) {
  const safeLimit = Math.max(10, Math.min(500, Number(limit) || 100));
  try {
    const text = await fs.readFile(ACTIVITY_FILE, 'utf8');
    return text.trim().split(/\r?\n/).filter(Boolean).slice(-safeLimit).reverse().flatMap((line) => {
      try { return [JSON.parse(line)]; } catch { return []; }
    });
  } catch (error) {
    if (error?.code === 'ENOENT') return [];
    throw error;
  }
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
  await assertNoSymlink(from);
  await assertNoSymlink(to);
  await fs.mkdir(path.dirname(to), { recursive: true });
  await fs.rename(from, to);
  await recordActivity('server:file.rename', {
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
  for (const input of paths) {
    const target = safeProjectPath(String(input));
    await assertNoSymlink(target);
    relative.push(path.relative(PROJECT_ROOT, target) || '.');
  }
  await fs.rm(dest, { force: true });
  await fs.mkdir(path.dirname(dest), { recursive: true });
  await exec('zip', ['-rq', dest, ...relative], {
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
  const result = await exec('unzip', ['-Z1', archive], { timeout: 30_000, maxBuffer: 4 * 1024 * 1024 });
  result.stdout.split(/\r?\n/).filter(Boolean).forEach(cleanZipEntry);
  await fs.mkdir(dest, { recursive: true });
  await exec('unzip', ['-oq', archive, '-d', dest], {
    timeout: 5 * 60_000,
    maxBuffer: 8 * 1024 * 1024,
  });
  await recordActivity('server:file.decompress', {
    path: path.relative(PROJECT_ROOT, archive),
    destination: path.relative(PROJECT_ROOT, dest) || '/',
  });
}

function backupName(privateBackup) {
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  return (privateBackup ? 'private-' : 'project-') + stamp + '.zip';
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
      await exec('zip', ['-rq', target, relativeFromRoot], {
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

async function sendBackup(res, name) {
  const clean = safeBackupName(name);
  const target = path.join(BACKUP_DIR, clean);
  const data = await fs.readFile(target);
  res.writeHead(200, {
    'content-type': 'application/zip',
    'content-length': data.length,
    'content-disposition': 'attachment; filename="' + clean.replace(/"/g, '') + '"',
    'cache-control': 'no-store',
  });
  res.end(data);
  await recordActivity('server:backup.download', { name: clean });
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

  return {
    runtime: 'Node.js',
    version: process.version.replace(/^v/, ''),
    entryFile: ENTRY_FILE,
    startCommand: START_COMMAND,
    projectRoot: PROJECT_ROOT,
    service: MANAGED_SERVICE,
    gitRepository,
    gitBranch,
    additionalNodePackages,
  };
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
      enabled: values?.[entry.key] === true,
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

async function serviceState() {
  try {
    const { stdout } = await exec('systemctl', ['is-active', MANAGED_SERVICE]);
    return stdout.trim() || 'unknown';
  } catch (error) {
    return String(error?.stdout || '').trim() || 'inactive';
  }
}

async function sampleCpu() {
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

async function diskStats() {
  const stats = await fs.statfs(PROJECT_ROOT);
  const block = Number(stats.bsize);
  const total = Number(stats.blocks) * block;
  const free = Number(stats.bavail) * block;
  return { total, used: Math.max(0, total - free) };
}

async function hostStatus() {
  const [state, cpuPercent, disk] = await Promise.all([
    serviceState(),
    sampleCpu(),
    diskStats(),
  ]);
  const totalMemory = os.totalmem();
  const freeMemory = os.freemem();
  return {
    state,
    cpuPercent,
    memoryUsedBytes: Math.max(0, totalMemory - freeMemory),
    memoryLimitBytes: totalMemory,
    diskUsedBytes: disk.used,
    diskLimitBytes: disk.total,
    uptimeMs: Math.round(os.uptime() * 1000),
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
  const { stdout } = await exec('journalctl', ['-u', MANAGED_SERVICE, '-n', String(safeLimit), '--no-pager', '-o', 'cat'], {
    maxBuffer: 4 * 1024 * 1024,
  });
  return stdout.split(/\r?\n/).filter(Boolean);
}

async function power(action) {
  if (!['start', 'stop', 'restart'].includes(action)) {
    throw Object.assign(new Error('Invalid power action'), { statusCode: 400 });
  }
  await exec('systemctl', [action, MANAGED_SERVICE], { timeout: 30_000 });
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
  if (stat.size > 2 * 1024 * 1024) throw Object.assign(new Error('File too large for text editor'), { statusCode: 413 });
  return fs.readFile(target, 'utf8');
}


async function sendProjectFile(res, inputPath) {
  const target = safeProjectPath(inputPath);
  await assertNoSymlink(target);
  const info = await fs.stat(target);
  if (!info.isFile()) throw Object.assign(new Error('Not a file'), { statusCode: 400 });
  if (info.size > 100 * 1024 * 1024) throw Object.assign(new Error('File exceeds 100 MB download limit'), { statusCode: 413 });
  const data = await fs.readFile(target);
  const name = path.basename(target).replace(/"/g, '');
  res.writeHead(200, {
    'content-type': 'application/octet-stream',
    'content-length': data.length,
    'content-disposition': 'attachment; filename="' + name + '"',
    'cache-control': 'no-store',
  });
  res.end(data);
  await recordActivity('server:file.download', { path: path.relative(PROJECT_ROOT, target), bytes: data.length });
}

async function writeText(inputPath, content) {
  if (typeof content !== 'string') throw Object.assign(new Error('content must be text'), { statusCode: 400 });
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
    const pairRoute = url.pathname.match(/^\/api\/cortex\/mscc\/accounts\/(A|B)\/(pair|reconnect|repair)$/);
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
      const mode = body.mode === 'qr' ? 'qr' : 'code';
      const result = await msccControl('POST', '/accounts/' + id + '/repair', { mode });
      await recordActivity('mscc:pairing.repair', { account: id, mode });
      return json(res, 200, result);
    }
    if (req.method === 'GET' && url.pathname === '/api/cortex/host/logs') {
      return json(res, 200, { lines: await logs(url.searchParams.get('limit')) });
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

    return json(res, 404, { error: 'Not found' });
  } catch (error) {
    console.error(error);
    return json(res, error?.statusCode || 500, { error: error?.message || 'Internal server error' });
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
