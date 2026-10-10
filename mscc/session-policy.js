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
