import assert from 'node:assert/strict'
import { sessionEventsFromJsonl, sessionSnapshot, sanitizeSessionReason } from './session-diagnostics.js'

const events = [
  { id:'a', at:'2026-10-09T10:00:00Z', action:'account.connected', detail:{ account:'account-2', displayName:'Josia', phone:'234123456789' } },
  { id:'b', at:'2026-10-09T11:00:00Z', action:'message.received', detail:{ account:'account-2', body:'private message' } },
  { id:'c', at:'2026-10-09T12:00:00Z', action:'account.disconnected', detail:{ account:'account-3', reasonCode:500 } },
  { id:'d', at:'2026-10-09T13:00:00Z', action:'account.disconnected', detail:{ account:'account-2', reasonCode:408, auth:'secret', jid:'234123456789@s.whatsapp.net' } },
  { id:'e', at:'2026-10-09T14:00:00Z', action:'pairing.repair-requested', detail:{ account:'account-2', mode:'code', pairingCode:'SECRET-1234' } },
  { id:'f', at:'2026-10-09T15:00:00Z', action:'account.profile-changed', detail:{ account:'account-2', previousProfile:'josiah', profile:'nami', token:'my-secret' } },
]
const jsonl = events.map(row => JSON.stringify(row)).join('\n') + '\n{broken json'
const account = sessionEventsFromJsonl(jsonl, 'account-2')
assert.deepEqual(account.map(row => row.id), ['f', 'e', 'd', 'a'])
assert.deepEqual(account[0].detail, { profile:'nami', previousProfile:'josiah' })
assert.deepEqual(account[1].detail, { mode:'code' })
assert.deepEqual(account[2].detail, { reasonCode:408 })
assert.equal(sessionEventsFromJsonl(jsonl, 'account-2', 2).length, 2)
assert.equal(sessionEventsFromJsonl(jsonl, 'account-3').length, 1)
const serialized = JSON.stringify(account)
for (const secret of ['my-secret', 'SECRET-1234', 'private message', '234123456789', 's.whatsapp.net', 'auth']) {
  assert.equal(serialized.includes(secret), false, 'Leaked sensitive event field: ' + secret)
}
const snapshot = sessionSnapshot({
  id:'account-2',
  displayName:'Night Backup',
  role:'linked', profile:'nami', numberMasked:'234***6789', status:'reconnecting',
  connected:false, paused:false, registered:true, reconnectAttempts:4,
  nextReconnectAt: 3000, lastConnectedAt: 100, lastDisconnectedAt: 200,
  lastDisconnectCode:408,
  disconnectReason:'Connection to 234123456789@s.whatsapp.net failed with token=secret123',
}, 1000)
assert.equal(snapshot.profile, 'nami')
assert.equal(snapshot.nextReconnectAt, 3000)
assert.equal(snapshot.lastDisconnectCode, 408)
assert.equal(snapshot.disconnectReason.includes('234123456789'), false)
assert.equal(snapshot.disconnectReason.includes('secret123'), false)
assert.equal(sessionSnapshot({id:'A',status:'connected'}, 4000).nextReconnectAt, 0)
assert.equal(sanitizeSessionReason('pairingCode=ASDF'), 'pairingCode=[redacted]')
console.log('PASS account-scoped MSCC lifecycle diagnostics redact private message/auth data')
