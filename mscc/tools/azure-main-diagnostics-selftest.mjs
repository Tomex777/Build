import assert from 'node:assert/strict'
import { summarizeMainDiagnostics } from './azure-main-diagnostics.mjs'

const report = summarizeMainDiagnostics({
  snapshot: {
    id: 'A', displayName: 'private-name', numberMasked: '+234*******',
    connected: false, registered: true, paused: false, status: 'auth-invalid',
    lastDisconnectCode: 401, disconnectReason: 'private server detail',
    pairingCode: 'SECRET-CODE', pairingQr: 'SECRET-QR',
  },
  events: [
    { at: '2026-10-12T12:25:17.077Z', action: 'account.disconnected', detail: { reasonCode: 401, origin: 'stream-error', protocolDetail: 'device-removed', account: 'A', number: '2348012345678' } },
    { at: '2026-10-12T12:20:00.000Z', action: 'account.connected', detail: { account: 'A', phone: '2348012345678' } },
    { at: '2026-10-12T12:19:00.000Z', action: 'pairing.requested', detail: { pairingCode: 'SECRET-CODE' } },
  ],
}, '2026-10-12T12:26:00.000Z')

assert.deepEqual(report, {
  observedAt: '2026-10-12T12:26:00.000Z',
  account: 'Main',
  connected: false,
  registered: true,
  paused: false,
  status: 'auth-invalid',
  lastDisconnectCode: 401,
  disconnectsByCode: { '401': 1 },
  recentEvents: [
    { at: '2026-10-12T12:25:17.077Z', action: 'account.disconnected', reasonCode: 401, origin: 'stream-error', protocolDetail: 'device-removed' },
    { at: '2026-10-12T12:20:00.000Z', action: 'account.connected' },
  ],
})
const serialized = JSON.stringify(report)
for (const secret of ['private-name', '+234', 'private server detail', 'SECRET-CODE', 'SECRET-QR', '2348012345678']) {
  assert.equal(serialized.includes(secret), false, `sanitized report leaked ${secret}`)
}
console.log('PASS Main diagnostics projection contains only safe status and lifecycle fields')
