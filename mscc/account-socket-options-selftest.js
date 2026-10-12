import assert from 'node:assert/strict'
import { DEFAULT_CONNECTION_CONFIG, proto } from '@itsliaaa/baileys'
import { createAccountSocketOptions } from './account-socket-options.js'

const makeAuth = name => ({
  creds: { me: { id: name + '@s.whatsapp.net' } },
  keys: { owner: name },
})

const authA = makeAuth('account-a')
const authB = makeAuth('account-b')
const make = (auth, name) => createAccountSocketOptions({
  auth,
  logger: { level: 'silent' },
  browser: ['Mac OS', 'Chrome', '120'],
  getMessage: async () => name,
  version: [2, 3000, 1000],
})

const a = make(authA, 'A')
const b = make(authB, 'B')
assert.notStrictEqual(a, b)
assert.strictEqual(a.auth, authA)
assert.strictEqual(b.auth, authB)
assert.notStrictEqual(a.auth.creds, b.auth.creds, 'Main and linked account credentials must never be shared')
assert.notStrictEqual(a.auth.keys, b.auth.keys, 'Main and linked account key stores must never be shared')
assert.strictEqual(await a.getMessage(), 'A')
assert.strictEqual(await b.getMessage(), 'B')
assert.equal(a.syncFullHistory, false)
assert.equal(b.syncFullHistory, false)
assert.equal(a.markOnlineOnConnect, false)
assert.equal(Object.hasOwn(a, 'shouldSyncHistoryMessage'), false,
  'Do not override Baileys selective history sync with a global false')
assert.equal(Object.hasOwn(b, 'shouldSyncHistoryMessage'), false)

const acceptSync = DEFAULT_CONNECTION_CONFIG.shouldSyncHistoryMessage
const types = proto.HistorySync.HistorySyncType
assert.equal(acceptSync({ syncType: types.INITIAL_BOOTSTRAP }), true,
  'Initial bootstrap metadata must remain available for LID/PN mappings')
assert.equal(acceptSync({ syncType: types.RECENT }), true,
  'Recent synchronization must remain available')
assert.equal(acceptSync({ syncType: types.FULL }), false,
  'Keep full chat archive sync excluded')

assert.equal(Object.hasOwn(createAccountSocketOptions({
  auth: makeAuth('third'),
  logger: {},
  browser: ['Mac OS', 'Chrome', '120'],
  getMessage: async () => undefined,
}), 'version'), false, 'Allow Baileys default version when latest lookup fails')
assert.throws(() => createAccountSocketOptions({ auth: { creds: authA.creds } }), /own auth state/,
  'An account without a separate key store must be rejected')

console.log('PASS per-account socket isolation and Baileys selective INITIAL_BOOTSTRAP/RECENT sync')
