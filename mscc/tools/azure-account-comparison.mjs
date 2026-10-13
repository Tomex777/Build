/**
 * Read-only, privacy-limited cross-account WhatsApp health comparison.
 *
 * No phone numbers, JIDs, account IDs, display names, messages, credentials,
 * pairing codes or raw server-error strings may appear in stdout/stderr.
 *
 * Correlating A and the linked accounts on one VPS lets us distinguish
 * per-device revocation from a whole-process or network outage; it does NOT
 * determine whether WhatsApp rejects a server IP.
 */
import { pathToFileURL } from 'node:url'
import { realpathSync } from 'node:fs'

const STATUS = new Set(['connected', 'connecting', 'reconnecting', 'auth-invalid', 'offline', 'paused', 'pairing', 'disabled'])
const ACTIONS = new Set(['account.connected', 'account.disconnected', 'account.reconnect-failed', 'account.reconnect-scheduled'])
const ORIGINS = new Set(['intentional-logout', 'stream-error', 'server-failure', 'connection-replaced', 'transport-close', 'other'])
const DETAILS = new Set(['device-removed', 'connection-replaced', 'other-conflict', 'not-authorized', 'bad-session', 'policy-violation', 'unclassified'])
const SAFE_ID = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/

function finiteTime(value) {
  return Number.isSafeInteger(value) && value > 0 ? value : 0
}
function safeTime(value) {
  if (typeof value !== 'string' || value.length > 48) return ''
  const n = Date.parse(value)
  return Number.isFinite(n) ? new Date(n).toISOString() : ''
}
function sanitizedEvents(value) {
  return (Array.isArray(value) ? value : []).slice(0, 12).flatMap(event => {
    if (!ACTIONS.has(event?.action)) return []
    const row = { at: safeTime(event.at), action: event.action }
    if (Number.isInteger(event.detail?.reasonCode) && event.detail.reasonCode >= 0 &&
        event.detail.reasonCode <= 9999) row.code = event.detail.reasonCode
    if (ORIGINS.has(event.detail?.origin)) row.origin = event.detail.origin
    if (DETAILS.has(event.detail?.protocolDetail)) row.protocolDetail = event.detail.protocolDetail
    return [row]
  })
}

export function summarizeAccountComparison(state, diagnostics = [], observedAt = new Date().toISOString()) {
  const input = Array.isArray(state?.accounts) ? state.accounts : []
  if (!input.length || input.length > 50) throw new Error('Account inventory is unavailable')
  const main = input.filter(a => a?.id === 'A')
  if (main.length !== 1) throw new Error('Main is unavailable')
  let linked = 0
  const projected = input.map(account => {
    const isMain = account.id === 'A'
    const label = isMain ? 'Main' : `Linked #${++linked}`
    const diag = diagnostics.find(row => row?.id === account.id)?.data
    const snap = diag?.snapshot
    const status = STATUS.has(snap?.status) ? snap.status
      : (STATUS.has(account.status) ? account.status : 'unknown')
    const recent = sanitizedEvents(diag?.events)
    return {
      account: label,
      connected: snap?.connected === true,
      registered: snap?.registered === true,
      paused: snap?.paused === true,
      status,
      lastConnectedAt: finiteTime(snap?.lastConnectedAt),
      lastDisconnectedAt: finiteTime(snap?.lastDisconnectedAt),
      lastDisconnectCode: Number.isInteger(snap?.lastDisconnectCode) && snap.lastDisconnectCode >= 0 && snap.lastDisconnectCode <= 9999
        ? snap.lastDisconnectCode : null,
      recentEvents: recent.slice(0, 8),
    }
  })
  const mainStatus = projected.find(x => x.account === 'Main')
  const linkedStatuses = projected.filter(x => x.account !== 'Main')
  const verdict = mainStatus.status === 'auth-invalid' && mainStatus.lastDisconnectCode === 401 &&
    linkedStatuses.some(x => x.connected)
    ? 'main-revoked-while-linked-connected'
    : mainStatus.connected && linkedStatuses.some(x => x.connected)
      ? 'multiple-connected'
      : 'inconclusive'
  return { observedAt: safeTime(observedAt), accountCount: projected.length, verdict, accounts: projected }
}

export async function readAccountComparison({
  fetcher = fetch,
  localPort = Number(process.env.MSCC_LOCAL_CONTROL_PORT || 8788),
} = {}) {
  if (!Number.isInteger(localPort) || localPort < 1 || localPort > 65535) throw new Error('Local port is invalid')
  const base = `http://127.0.0.1:${localPort}`
  const get = async route => {
    const result = await fetcher(base + route, { method:'GET', headers: { accept:'application/json' }, signal:AbortSignal.timeout(8_000) })
    if (!result.ok) throw new Error('Read-only account diagnostics unavailable')
    return result.json()
  }
  const state = await get('/state')
  const selected = Array.isArray(state?.accounts) ? state.accounts : []
  if (!selected.length || selected.length > 50 || selected.some(a => !SAFE_ID.test(a?.id || ''))) {
    throw new Error('Account inventory failed validation')
  }
  const diagnostics = await Promise.all(selected.map(async account => ({
    id: account.id,
    data: await get(`/accounts/${account.id}/diagnostics?limit=24`)
  })))
  return summarizeAccountComparison(state, diagnostics)
}

if (process.argv[1] && import.meta.url === pathToFileURL(realpathSync(process.argv[1])).href) {
  readAccountComparison().then(result => {
    process.stdout.write(JSON.stringify(result) + '\n')
  }).catch(() => {
    // Never print network exception messages: they can include sensitive URLs.
    console.error('Sanitized cross-account diagnostic failed')
    process.exitCode = 1
  })
}
