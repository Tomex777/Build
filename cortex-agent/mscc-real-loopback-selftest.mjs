import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { createServer } from 'node:net';

const msccRoot = process.env.MSCC_CHECKOUT;
assert(msccRoot, 'MSCC_CHECKOUT must point to the separately checked-out mscc-azure source');
const importMSCC = async name => import(pathToFileURL(join(msccRoot, name)).href);
const [{ startWebPanel }, { AccountRegistry }, { sessionEventsFromJsonl, sessionSnapshot }] =
  await Promise.all([
    importMSCC('web-panel.js'),
    importMSCC('account-registry.js'),
    importMSCC('session-diagnostics.js'),
  ]);

async function ephemeralPort() {
  const server = createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  await new Promise(resolve => server.close(resolve));
  return port;
}
const root = await mkdtemp(join(tmpdir(), 'cortex-mscc-integration-'));
const token = 'integration-test-token-not-real-please-discard';
let panel;
let agent;
let stderr = '';
try {
  const registry = new AccountRegistry({ file: join(root, 'accounts.json'), authRoot: join(root, 'auth'), maxAccounts: 5 });
  await registry.load();
  const owner = await registry.create({ phoneNumber: '234000000001', displayName: 'Main' });
  const linked = await registry.create({ phoneNumber: '234000000002', displayName: 'Second' });
  assert.equal(owner.id, 'A');
  const creds = join(linked.authDir, 'creds.json');
  await writeFile(creds, JSON.stringify({ sentinel: 'KEEP SESSION DATA UNCHANGED' }));
  const before = await readFile(creds, 'utf8');
  const profiles = new Map([['A', 'control'], [linked.id, 'josiah']]);
  const publicState = () => ({
    version: 'integration-test',
    destination: 'A',
    capabilities: { fixedCcDestination: true, changeCcDestination: false, perAccountCcOverride: false, botProfiles: true },
    botProfiles: [{ id: 'control', displayName: 'Control' }, { id: 'josiah', displayName: 'Josiah' }, { id: 'nami', displayName: 'Nami' }],
    accounts: registry.list().map(a => ({
      ...a,
      profile: profiles.get(a.id) || 'unassigned',
      connected: a.id === 'A',
      status: a.id === 'A' ? 'connected' : 'offline',
      numberMasked: a.id === 'A' ? '234***0001' : '234***0002',
    })),
  });

  panel = startWebPanel({
    port: await ephemeralPort(),
    host: '127.0.0.1',
    localControlPort: 8788, // Cortex Agent's actual fixed, private MSCC control listener
    password: 'mock-panel-password-do-not-use',
    sessionSecret: 'mock-session-secret-do-not-use',
    getState: publicState,
    getActivity: async () => [],
    getAccountDiagnostics: async id => {
      if (id !== linked.id) return null;
      return {
        snapshot: sessionSnapshot({ ...publicState().accounts.find(a => a.id === id), disconnectReason: 'bearer:example-secret' }),
        events: sessionEventsFromJsonl(JSON.stringify({
          id: 'test1', at: '2026-10-11T00:00:00Z',
          action: 'account.disconnected',
          detail: { account: id, reasonCode: 408, token: 'must-not-leak' },
        }), id),
      };
    },
    renameAccount: async (id, displayName) => {
      const a = await registry.rename(id, displayName);
      return { ok: true, account: a.id, displayName: a.displayName };
    },
    assignAccountProfile: async (id, profileId) => {
      assert(registry.get(id), 'Unknown account');
      if (id === 'A' || profileId === 'control') throw new Error('Control profile is reserved for Account A');
      if (!['nami', 'josiah'].includes(profileId)) throw new Error('Unknown bot profile');
      profiles.set(id, profileId);
      return { ok: true, account: id, profile: profileId };
    },
  });

  const port = await ephemeralPort();
  agent = spawn(process.execPath, [new URL('./index.js', import.meta.url).pathname], {
    env: { ...process.env, PORT: String(port), HOST: '127.0.0.1', CORTEX_AGENT_TOKEN: token,
      CORTEX_PROJECT_ROOT: root, CORTEX_STATE_DIR: join(root, '.cortex') },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  agent.stderr.on('data', chunk => { stderr += String(chunk).slice(0, 2000); });
  const base = 'http://127.0.0.1:' + port;
  const request = (method, path, body, authed = true) => fetch(base + path, {
    method, headers: {
      ...(authed ? { authorization: 'Bearer ' + token } : {}),
      ...(body === undefined ? {} : { 'content-type': 'application/json' }),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    signal: AbortSignal.timeout(8000),
  });

  let ready = false;
  for (let i = 0; i < 120; i++) {
    if (agent.exitCode !== null) throw new Error('Agent exited before startup: ' + stderr);
    try { if ((await request('GET', '/api/cortex/mscc/pairing')).ok) { ready = true; break; } }
    catch {}
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  assert(ready, 'Agent could not read actual MSCC loopback panel: ' + stderr);

  const unauthorized = await request('GET', '/api/cortex/mscc/pairing', undefined, false);
  assert.equal(unauthorized.status, 401, 'MSCC proxy must reject unauthenticated requests');
  let state = await (await request('GET', '/api/cortex/mscc/pairing')).json();
  assert.equal(state.destination, 'A');
  assert.equal(state.capabilities.fixedCcDestination, true);
  assert.equal(state.accounts.find(a => a.id === linked.id).profile, 'josiah');

  let response = await request('PATCH', '/api/cortex/mscc/accounts/' + linked.id, { displayName: 'Work' });
  assert.equal(response.status, 200);
  assert.equal((await response.json()).displayName, 'Work');
  response = await request('POST', '/api/cortex/mscc/accounts/' + linked.id + '/profile', { profileId: 'nami' });
  assert.equal(response.status, 200);
  assert.equal((await response.json()).profile, 'nami');
  state = await (await request('GET', '/api/cortex/mscc/pairing')).json();
  assert.equal(state.accounts.find(a => a.id === linked.id).displayName, 'Work');
  assert.equal(state.accounts.find(a => a.id === linked.id).profile, 'nami');
  assert.equal(await readFile(creds, 'utf8'), before, 'Real MSCC auth state mutated during rename/profile update');

  response = await request('GET', '/api/cortex/mscc/accounts/' + linked.id + '/diagnostics?limit=5', undefined, false);
  assert.equal(response.status, 401);
  response = await request('GET', '/api/cortex/mscc/accounts/' + linked.id + '/diagnostics?limit=5');
  assert.equal(response.status, 200);
  const diag = await response.json();
  assert.equal(diag.snapshot.id, linked.id);
  assert.equal(diag.events[0].action, 'account.disconnected');
  const serialized = JSON.stringify(diag);
  assert(!serialized.includes('must-not-leak') && !serialized.includes('example-secret'), 'Sensitive session data reached Cortex');

  response = await request('POST', '/api/cortex/mscc/accounts/A/profile', { profileId: 'nami' });
  assert.notEqual(response.status, 200, 'Permanent Account A control personality must remain locked');
  assert.equal(profiles.get('A'), 'control');
  assert.equal(await readFile(creds, 'utf8'), before);
  console.log('PASS Cortex Agent ↔ real MSCC loopback account management, auth, persistence and diagnostics');
} finally {
  if (agent && agent.exitCode === null) {
    agent.kill('SIGTERM');
    await new Promise(resolve => { agent.once('exit', resolve); setTimeout(resolve, 2000).unref(); });
  }
  panel?.close();
  await rm(root, { recursive: true, force: true });
}
