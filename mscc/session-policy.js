import { DisconnectReason } from '@itsliaaa/baileys'

export function jidPhoneNumber(jid) {
  const raw = String(jid || '').trim()
  if (!raw) return ''
  const local = raw.split('@')[0].split(':')[0]
  return local.replace(/\D/g, '')
}

/**
 * A stable, operator-facing lifecycle state. In particular, a queued retry
 * must not be presented as an inert offline account in Cortex.
 */
export function sessionStatus(account) {
  if (!account?.enabled) return 'disabled'
  if (account.connected) return 'connected'
  if (account.invalid) return 'auth-invalid'
  if (account.paused) return 'paused'
  if (account.reconnectTimer) return 'reconnecting'
  if (account.pairingMode) return 'pairing'
  if (account.sock) return 'connecting'
  return 'offline'
}

/**
 * Only emit fixed buckets to diagnostics. A raw Baileys error can contain
 * WhatsApp JIDs or session details; it must never reach Cortex/Azure logs.
 */
export function disconnectOrigin(error) {
  const message = typeof error?.message === 'string' ? error.message : ''
  if (message === 'Intentional Logout') return 'intentional-logout'
  if (message.startsWith('Stream Errored')) return 'stream-error'
  if (message === 'Connection Failure') return 'server-failure'
  if (/connection was replaced/i.test(message)) return 'connection-replaced'
  if (/Connection Terminated|Connection Closed|Connection was lost/i.test(message)) return 'transport-close'
  return 'other'
}

/**
 * Preserve only protocol-category evidence from Baileys. The raw error
 * contains binary WhatsApp nodes and may include user IDs, auth material or
 * phone numbers; never log error.data, node.attrs or arbitrary type strings.
 *
 * A 401 with "device-removed" means WhatsApp actively unlinked the device.
 * This is materially different from an unrelated transport drop or another
 * socket replacing the connection.
 */
export function disconnectProtocolDetail(error) {
  const data = error?.data
  const reason = data?.tag === 'stream:error'
    ? (Array.isArray(data.content) ? data.content.find(child =>
      ['conflict', 'not-authorized', 'bad-session', 'policy-violation'].includes(child?.tag)
    ) : null)
    : data

  if (reason?.tag === 'conflict') {
    if (reason.attrs?.type === 'device_removed') return 'device-removed'
    if (reason.attrs?.type === 'replaced') return 'connection-replaced'
    return 'other-conflict'
  }
  if (reason?.tag === 'not-authorized') return 'not-authorized'
  if (reason?.tag === 'bad-session') return 'bad-session'
  if (reason?.tag === 'policy-violation') return 'policy-violation'
  return 'unclassified'
}

export function reconnectDelay(attempt, { baseMs = 2000, maxMs = 30000 } = {}) {
  const safeAttempt = Math.max(1, Number(attempt) || 1)
  return Math.min(maxMs, baseMs * (2 ** Math.min(4, safeAttempt - 1)))
}

/**
 * Baileys uses status 500 for `badSession`, but its numeric status alone is
 * not proof that WhatsApp removed this linked device. In particular, a 500
 * without an explicit auth-rejection protocol node gets a small, bounded
 * recovery window using the SAME on-disk credentials. Never erase auth here.
 *
 * A real 401/403, or a server-auth rejection node, still requires re-pair.
 * Old callers with no error object retain the conservative 500 behaviour.
 */
export function classifyDisconnect(code, error = null, ambiguous500Attempts = 0) {
  const repair = {
    action: 'repair',
    message: 'Saved auth is no longer valid for this session. Use Re-pair.',
  }
  const duplicate = {
    action: 'halt',
    message: 'Connection was replaced by another active socket. Stop the duplicate session, then reconnect this account.',
  }

  if (code === DisconnectReason.badSession) {
    if (!error) return repair
    const detail = disconnectProtocolDetail(error)
    if (['bad-session', 'not-authorized', 'device-removed', 'policy-violation'].includes(detail)) {
      return repair
    }
    if (detail === 'connection-replaced' || detail === 'other-conflict' ||
        disconnectOrigin(error) === 'connection-replaced') return duplicate

    const attempts = Math.max(0, Math.floor(Number(ambiguous500Attempts) || 0))
    // Even the library's own "Bad Session" text is not independently
    // verified WhatsApp revocation. Allow just one non-destructive probe.
    const libraryBadSessionHint = /^Bad Session(?:\s*\(500\))?$/i.test(
      typeof error.message === 'string' ? error.message.trim() : ''
    )
    const maxRetries = libraryBadSessionHint ? 1 : 3
    if (attempts >= maxRetries) {
      return {
        action: 'halt',
        message: 'WhatsApp returned disconnect 500 repeatedly. Saved auth preserved. Check Diagnostics, then use Reconnect; do not re-pair automatically.',
      }
    }
    return {
      action: 'reconnect',
      delayMs: reconnectDelay(attempts + 1, { baseMs: 4000, maxMs: 16000 }),
      message: 'WhatsApp returned disconnect 500 without confirmed session revocation. Retrying existing saved auth.',
    }
  }

  if (
    code === DisconnectReason.loggedOut ||
    code === DisconnectReason.multideviceMismatch ||
    code === DisconnectReason.forbidden
  ) return repair

  if (code === DisconnectReason.connectionReplaced) return duplicate
  if (code === DisconnectReason.restartRequired) return { action: 'reconnect', delayMs: 500 }
  return { action: 'reconnect' }
}

/**
 * A delayed retry is only eligible while it still owns the current account
 * generation. The socket check prevents a timer from creating a second active
 * Baileys client for the same auth directory.
 */
export function canStartScheduledReconnect(account, generation) {
  return Boolean(
    account?.enabled &&
    account.generation === generation &&
    !account.paused && !account.invalid && !account.sock
  )
}

/** Queues all socket-changing operations against one account's auth storage. */
export function serializeSessionOperation(account, operation) {
  const task = Promise.resolve(account.op).catch(() => {}).then(operation)
  account.op = task.catch(() => {})
  return task
}
