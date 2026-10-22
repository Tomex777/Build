// Independent Agent E release gate: call the real MSCC Channel
// metadata path, not a duplicate client-side "sanitizer" implementation.
// Expected to fail against the October 22 baseline until C fixes identity
// preservation; never bypass this by comparing the altered output instead.
import assert from 'node:assert/strict';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const root=path.resolve(process.env.NIGHT_MSCC_SOURCE_ROOT || path.resolve(process.cwd(),'mscc'));
const {NightPlayChannels}=await import(pathToFileURL(path.join(root,'night-play-channels.js')).href);

const original = 'NIGHT 👩🏽‍💻 👨‍👩‍👧‍👦 🇳🇬 العربية हिन्दी 𝙏𝙀𝙎𝙏';
const jid = '120363282083849178@newsletter';
const invitation = 'https://whatsapp.com/channel/0029VExampleTestingCode';
const calls = [];
const fakeDb = {
  exec(sql) { calls.push({ type: 'schema', sql: String(sql).slice(0, 50) }); },
  prepare(sql) {
    calls.push({ type: 'query', sql: String(sql).slice(0, 80) });
    return {
      get() { return undefined; },
      run() { return { changes: 1 }; },
      all() { return []; }
    };
  }
};
const play = {
  db: fakeDb,
  linkedProfile() { return { id: 'verified-qa-owner' }; },
  limit() {}
};
const manager = new NightPlayChannels(play);
const sock = {
  async newsletterMetadata(type, key) {
    assert.equal(type, 'invite');
    assert.equal(key, '0029VExampleTestingCode');
    return { id: jid, name: original, viewer_metadata: { role: 'ADMIN' } };
  }
};
const account = { connected: true, id: 'bot-account-a', sock };
const result = await manager.begin({ userKey: 'fixture-user' }, account, invitation);
assert.equal(result.name, original,
  'Exact WhatsApp channel display name must retain emoji ZWJs and all Unicode graphemes');
assert.ok(calls.some(x => x.type === 'query'), 'Real claim path was exercised');

// Preservation must also apply near a boundary that previously split a
// surrogate pair; never silently clip names with String.slice(0, 100).
const longExact = 'N'.repeat(99) + '👩🏽‍💻' + ' 𝙎𝙏𝙔𝙇𝙀';
const longSocket = {
  async newsletterMetadata() {
    return { id: jid, name: longExact, viewer_metadata: { role: 'ADMIN' } };
  }
};
const second = await manager.begin({ userKey: 'fixture-user' },
  { ...account, sock: longSocket }, invitation);
assert.equal(second.name, longExact,
  'Never truncate exact verified channel display name at an arbitrary UTF-16 code-unit boundary');
console.log('PASS verified channel display-name ZWJ, skin tone and surrogate boundaries');
