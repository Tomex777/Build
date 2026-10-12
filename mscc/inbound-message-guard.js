/**
 * Reject bot-authored messages before command dispatch. A plain-text choice
 * prompt must never treat the bot's own "invalid choice" reply as user input:
 * otherwise each sent warning generates another warning indefinitely.
 *
 * In addition, Baileys can deliver the same upsert more than once (e.g.
 * notify/append or after a reconnect). De-duplicate by account/chat/message ID
 * before any command or selection side effects.
 */
export function createInboundMessageGuard({ maxEntries = 8192, ttlMs = 30 * 60_000 } = {}) {
  const seen = new Map()
  const cap = Math.max(1, Number(maxEntries) || 8192)
  const ttl = Math.max(1000, Number(ttlMs) || 30 * 60_000)

  return function acceptInbound(accountId, msg, now = Date.now()) {
    const key = msg?.key
    if (!msg?.message || !key?.id || key.fromMe === true) return false
    const chat = String(key.remoteJid || '')
    if (!chat) return false
    const identity = [accountId, chat, key.participant || '', key.id].join('|')
    const existing = seen.get(identity)
    if (existing !== undefined && now - existing >= 0 && now - existing < ttl) return false
    seen.delete(identity)
    seen.set(identity, now)

    if (seen.size > cap) {
      for (const [id, at] of seen) {
        if (now - at >= ttl) seen.delete(id)
      }
      while (seen.size > cap) seen.delete(seen.keys().next().value)
    }
    return true
  }
}

/**
 * A pending menu should consume ONLY a displayed number/option name.
 * Never convert arbitrary conversation, another command or a previous bot
 * reply into a synthetic .manga ~choice command.
 */
export function matchesDisplayedChoice(input, entries = []) {
  const text = String(input || '').trim()
  if (!text || text.length > 120 || !Array.isArray(entries) || !entries.length) return false
  if (/^\d{1,3}$/.test(text)) {
    const choice = Number(text)
    return choice >= 1 && choice <= entries.length
  }
  const wanted = text.toLocaleLowerCase()
  return entries.some(entry =>
    String(entry?.title || '').trim().toLocaleLowerCase() === wanted ||
    String(entry?.id || '').trim().toLocaleLowerCase() === wanted
  )
}
