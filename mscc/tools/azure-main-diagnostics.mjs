import { pathToFileURL } from 'node:url'
import { realpathSync } from 'node:fs'

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

function safeTime(value) {
  if (typeof value !== 'string' || value.length > 48) return ''
  const date = new Date(value)
  return Number.isFinite(date.getTime()) ? date.toISOString() : ''
}

export function summarizeMainDiagnostics(payload, observedAt = new Date().toISOString()) {
  const snapshot = payload?.snapshot
  assert(snapshot?.id === 'A', 'Main account diagnostics are unavailable')
  assert(typeof snapshot.connected === 'boolean', 'Main connection state is unavailable')
  assert(typeof snapshot.registered === 'boolean', 'Main registration state is unavailable')
  assert(typeof snapshot.paused === 'boolean', 'Main pause state is unavailable')

  const disconnectsByCode = {}
  const recentEvents = []
  for (const event of Array.isArray(payload.events) ? payload.events.slice(0, 20) : []) {
    if (!['account.connected', 'account.disconnected', 'account.reconnect-scheduled', 'account.reconnect-failed'].includes(event?.action)) continue
    const row = { at: safeTime(event.at), action: event.action }
    if (event.action === 'account.disconnected' && Number.isFinite(event.detail?.reasonCode)) {
      const code = String(event.detail.reasonCode)
      disconnectsByCode[code] = (disconnectsByCode[code] || 0) + 1
      row.reasonCode = event.detail.reasonCode
    }
    if (row.at) recentEvents.push(row)
  }

  const status = typeof snapshot.status === 'string' && /^[a-z0-9-]{1,48}$/i.test(snapshot.status)
    ? snapshot.status
    : 'unknown'
  return {
    observedAt: safeTime(observedAt),
    account: 'Main',
    connected: snapshot.connected,
    registered: snapshot.registered,
    paused: snapshot.paused,
    status,
    lastDisconnectCode: Number.isFinite(snapshot.lastDisconnectCode) ? snapshot.lastDisconnectCode : null,
    disconnectsByCode,
    recentEvents: recentEvents.slice(0, 8),
  }
}

export async function readMainDiagnostics({
  fetcher = fetch,
  localPort = Number(process.env.MSCC_LOCAL_CONTROL_PORT || 8788),
} = {}) {
  assert(Number.isInteger(localPort) && localPort >= 1 && localPort <= 65535, 'Local control port is invalid')
  const response = await fetcher(`http://127.0.0.1:${localPort}/accounts/A/diagnostics?limit=20`, {
    method: 'GET',
    headers: { accept: 'application/json' },
    signal: AbortSignal.timeout(8_000),
  })
  assert(response.ok, 'Main diagnostics endpoint failed')
  return summarizeMainDiagnostics(await response.json())
}

if (process.argv[1] && import.meta.url === pathToFileURL(realpathSync(process.argv[1])).href) {
  readMainDiagnostics().then(report => {
    process.stdout.write(JSON.stringify(report) + '\n')
  }).catch(() => {
    console.error('Main diagnostics check failed; no account payload was logged.')
    process.exitCode = 1
  })
}
