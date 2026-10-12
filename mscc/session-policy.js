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

export function reconnectDelay(attempt, { baseMs = 2000, maxMs = 30000 } = {}) {
  const safeAttempt = Math.max(1, Number(attempt) || 1)
  return Math.min(maxMs, baseMs * (2 ** Math.min(4, safeAttempt - 1)))
}

export function classifyDisconnect(code) {
  if (
    code === DisconnectReason.loggedOut ||
    code === DisconnectReason.badSession ||
    code === DisconnectReason.multideviceMismatch ||
    code === DisconnectReason.forbidden
  ) {
    return {
      action: 'repair',
      message: 'Saved auth is no longer valid for this session. Use Re-pair.',
    }
  }

  if (code === DisconnectReason.connectionReplaced) {
    return {
      action: 'halt',
      message: 'Connection was replaced by another active socket. Stop the duplicate session, then reconnect this account.',
    }
  }

  if (code === DisconnectReason.restartRequired) {
    return { action: 'reconnect', delayMs: 500 }
  }

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
