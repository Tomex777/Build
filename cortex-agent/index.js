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
const PROJECT_ROOT = path.resolve(process.env.NIGHT_ROOT || '/opt/night');
const NIGHT_SERVICE = process.env.NIGHT_SERVICE || 'night.service';
const NIGHT_ENTRY = process.env.NIGHT_ENTRY || 'index.js';
const NIGHT_START_COMMAND = process.env.NIGHT_START_COMMAND || 'node index.js';
const MAX_BODY = 16 * 1024 * 1024;
const MAX_FILE_BYTES = 10 * 1024 * 1024;
const PROTECTED_NAMES = new Set(['.git', '.ssh', 'node_modules', '.gradle']);
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
    throw Object.assign(new Error('Path escapes Night project'), { statusCode: 400 });
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

async function serviceState() {
  try {
    const { stdout } = await exec('systemctl', ['is-active', NIGHT_SERVICE]);
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
      entryFile: NIGHT_ENTRY,
      startCommand: NIGHT_START_COMMAND,
    },
  };
}

async function logs(limit) {
  const safeLimit = Math.max(20, Math.min(1000, Number(limit) || 200));
  const { stdout } = await exec('journalctl', ['-u', NIGHT_SERVICE, '-n', String(safeLimit), '--no-pager', '-o', 'cat'], {
    maxBuffer: 4 * 1024 * 1024,
  });
  return stdout.split(/\r?\n/).filter(Boolean);
}

async function power(action) {
  if (!['start', 'stop', 'restart'].includes(action)) {
    throw Object.assign(new Error('Invalid power action'), { statusCode: 400 });
  }
  await exec('systemctl', [action, NIGHT_SERVICE], { timeout: 30_000 });
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

async function writeText(inputPath, content) {
  if (typeof content !== 'string') throw Object.assign(new Error('content must be text'), { statusCode: 400 });
  const target = safeProjectPath(inputPath);
  await assertNoSymlink(target);
  await fs.mkdir(path.dirname(target), { recursive: true });
  const temp = `${target}.cortex-${crypto.randomUUID()}.tmp`;
  await fs.writeFile(temp, content, 'utf8');
  await fs.rename(temp, target);
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
  return { message: (stdout || stderr || 'Dependencies installed').trim().slice(-1200) };
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

async function handler(req, res) {
  try {
    if (!authorized(req)) return json(res, 401, { error: 'Unauthorized' });
    const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);

    if (req.method === 'GET' && url.pathname === '/api/cortex/host/status') {
      return json(res, 200, await hostStatus());
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

    return json(res, 404, { error: 'Not found' });
  } catch (error) {
    console.error(error);
    return json(res, error?.statusCode || 500, { error: error?.message || 'Internal server error' });
  }
}

await fs.mkdir(PROJECT_ROOT, { recursive: true });
const server = http.createServer(handler);
server.listen(PORT, HOST, () => {
  console.log(`Cortex Agent listening on http://${HOST}:${PORT}`);
  console.log(`Night root: ${PROJECT_ROOT}`);
  console.log(`Night service: ${NIGHT_SERVICE}`);
});
