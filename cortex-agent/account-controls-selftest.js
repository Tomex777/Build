import assert from 'node:assert/strict';
import http from 'node:http';
import { spawn } from 'node:child_process';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const root = await mkdtemp(join(tmpdir(), 'cortex-accounts-'));
const token = 'cortex-agent-test-token-very-long-and-not-real';
const forwarded = [];
let agent = null;
let upstream = null;

async function listen(server, port) {
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', resolve);
  });
}
async function freePort() {
  const server = http.createServer();
  await listen(server, 0);
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}
async function closeServer(server) {
  if (!server) return;
  await new Promise(resolve => server.close(resolve));
}
try {
  // MSCC's production Cortex control listener binds only to this loopback port.
  upstream = http.createServer(async (req, res) => {
    const data = [];
    for await (const chunk of req) data.push(chunk);
    const body = Buffer.concat(data).toString('utf8');
    const parsed = body ? JSON.parse(body) : {};
    forwarded.push({ method: req.method, path: req.url, body: parsed });
    const respond = (status, value) => {
      res.writeHead(status, { 'content-type': 'application/json' });
      res.end(JSON.stringify(value));
    };
    if (req.url === '/state' && req.method === 'GET') {
      return respond(200, { accounts: [
        { id: 'A', displayName: 'Main', profile: 'control' },
        { id: 'account-2', displayName: 'Josia', profile: 'josiah' },
      ], botProfiles: [
        { id: 'control', displayName: 'Control' },
        { id: 'nami', displayName: 'Nami' },
      ] });
    }
    if (req.url === '/accounts/account-2' && req.method === 'PATCH') {
      return respond(200, { ok: true, account: 'account-2', displayName: parsed.displayName });
    }
    if (req.url === '/accounts/account-2/profile' && req.method === 'POST') {
      return respond(200, { ok: true, account: 'account-2', profile: parsed.profileId });
    }
    respond(404, { error: 'Unsupported mock route' });
  });
  await listen(upstream, 8788);
  const port = await freePort();
  agent = spawn(process.execPath, [new URL('./index.js', import.meta.url).pathname], {
    env: {
      ...process.env, PORT: String(port), HOST: '127.0.0.1', CORTEX_AGENT_TOKEN: token,
      CORTEX_PROJECT_ROOT: root, CORTEX_STATE_DIR: join(root, '.cortex'),
    }, stdio: ['ignore', 'pipe', 'pipe'],
  });
  let stderr = '';
  agent.stderr.on('data', bytes => { stderr += bytes.toString(); });
  const base = `http://127.0.0.1:${port}`;
  async function request(method, path, body = undefined, authed = true) {
    return fetch(base + path, {
      method,
      headers: { ...(authed ? { authorization: 'Bearer ' + token } : {}),
        ...(body === undefined ? {} : { 'content-type': 'application/json' }) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      signal: AbortSignal.timeout(8_000),
    });
  }
  let healthy = false;
  for (let i = 0; i < 80; i++) {
    if (agent.exitCode !== null) throw new Error('Cortex Agent exited early: ' + stderr);
    try {
      const resp = await request('GET', '/api/cortex/mscc/pairing');
      if (resp.ok) { healthy = true; break; }
    } catch {}
    await new Promise(resolve => setTimeout(resolve, 75));
  }
  assert.equal(healthy, true, 'Cortex Agent never became ready: ' + stderr);
  const denied = await request('PATCH', '/api/cortex/mscc/accounts/account-2', {
    displayName: 'Ignored',
  }, false);
  assert.equal(denied.status, 401, 'Mutation must require Cortex Agent bearer token');
  const rename = await request('PATCH', '/api/cortex/mscc/accounts/account-2', { displayName: 'Night Backup' });
  assert.equal(rename.status, 200);
  assert.deepEqual((await rename.json()).displayName, 'Night Backup');
  const profile = await request('POST', '/api/cortex/mscc/accounts/account-2/profile', { profileId: 'nami' });
  assert.equal(profile.status, 200);
  assert.equal((await profile.json()).profile, 'nami');

  const invalidName = await request('PATCH', '/api/cortex/mscc/accounts/account-2', { displayName: '' });
  assert.equal(invalidName.status, 400);
  const invalidProfile = await request('POST', '/api/cortex/mscc/accounts/account-2/profile', { profileId: '../control' });
  assert.equal(invalidProfile.status, 400);
  const badAccount = await request('PATCH', '/api/cortex/mscc/accounts/../../A', { displayName: 'Nope' });
  assert.notEqual(badAccount.status, 200);
  const unexpected = forwarded.filter(row => row.path.includes('accounts'));
  assert.deepEqual(unexpected, [
    { method: 'PATCH', path: '/accounts/account-2', body: { displayName: 'Night Backup' } },
    { method: 'POST', path: '/accounts/account-2/profile', body: { profileId: 'nami' } },
  ], 'Only valid, authenticated updates may reach MSCC');
  console.log('PASS Cortex Agent authenticated account rename/profile proxy, validations, audit boundary');
} finally {
  if (agent && agent.exitCode === null) {
    agent.kill('SIGTERM');
    await new Promise(resolve => { agent.once('exit', resolve); setTimeout(resolve, 2000).unref(); });
  }
  await closeServer(upstream);
  await rm(root, { recursive: true, force: true });
}
