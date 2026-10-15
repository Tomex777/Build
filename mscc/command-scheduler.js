// Isolate slow source operations from lightweight commands while retaining
// FIFO processing for interactive sessions, moderation and game choices.
//
// A queue is keyed by (account, chat). Two different chats/accounts never
// depend on each other's command completion. Explicitly independent requests
// can bypass their own chat queue; numbered replies and stateful commands stay FIFO.
const FAST_COMMANDS = new Set(['ping', 'alive', 'menu', 'nami', 'mimi', 'help', 'sources'])
// These operate exclusively on the incoming message/quoted media (or a supplied
// song query), never on per-chat selections. In particular, a stuck provider
// must not delay a new lyrics request or a sticker requested in the same room.
const INDEPENDENT_COMMANDS = new Set(['lyrics', 'lyric', 'sticker', 's'])
const MAX_PENDING_PER_CHAT = 256

export function isStatelessFastCommand(text, prefix = '.') {
  if (!prefix || typeof text !== 'string') return false
  const clean = text.trim()
  if (!clean.startsWith(prefix)) return false
  const body = clean.slice(prefix.length)
  const match = /^([a-zA-Z][a-zA-Z0-9_-]*)\s*$/u.exec(body)
  return Boolean(match && FAST_COMMANDS.has(match[1].toLowerCase()))
}

export function isIndependentCommand(text, prefix = '.') {
  if (!prefix || typeof text !== 'string') return false
  const clean = text.trim()
  if (!clean.startsWith(prefix)) return false
  const match = /^([a-zA-Z][a-zA-Z0-9_-]*)(?:\s|$)/u.exec(clean.slice(prefix.length))
  return Boolean(match && INDEPENDENT_COMMANDS.has(match[1].toLowerCase()))
}

export function createMessageScheduler({
  process,
  accountKey = value => String(value?.id || ''),
  chatKey = msg => String(msg?.key?.remoteJid || ''),
  textOf = msg => String(msg?.message?.conversation || ''),
  prefix = () => '.',
  maxPendingPerChat = MAX_PENDING_PER_CHAT,
  log = message => console.warn(message),
} = {}) {
  if (typeof process !== 'function') throw new TypeError('Message processor required')
  const queued = new Map()
  const counters = new Map()
  const max = Math.max(1, Number(maxPendingPerChat) || MAX_PENDING_PER_CHAT)

  function submit(account, msg) {
    const key = accountKey(account) + '|' + chatKey(msg)
    const incoming = textOf(msg)
    const fast = isStatelessFastCommand(incoming, prefix()) || isIndependentCommand(incoming, prefix())
    const generation = account?.generation
    const safelyProcess = () => account?.generation === generation ? process(account, msg) : false
    if (fast) {
      return Promise.resolve().then(safelyProcess)
        .catch(() => log('[MSCC scheduler] fast command handler failed'))
    }

    const count = counters.get(key) || 0
    if (count >= max) {
      // Backpressure on one unusually busy chat, not on the entire bot.
      log('[MSCC scheduler] dropped message after per-chat queue reached ' + max)
      return Promise.resolve(false)
    }

    counters.set(key, count + 1)
    const previous = queued.get(key) || Promise.resolve()
    const job = previous.catch(() => {})
      .then(safelyProcess)
      .catch(() => {
        log('[MSCC scheduler] message processing failed')
        return false
      })
    queued.set(key, job)
    job.finally(() => {
      const remaining = (counters.get(key) || 1) - 1
      if (remaining > 0) counters.set(key, remaining)
      else counters.delete(key)
      if (queued.get(key) === job) queued.delete(key)
    }).catch(() => {})
    return job
  }

  function submitBatch(account, messages = []) {
    // Start all jobs synchronously without waiting on a slow provider in the
    // same event batch. The per-chat chain preserves interactive ordering.
    return Promise.allSettled((messages || []).map(msg => submit(account, msg)))
  }

  return { submit, submitBatch, stats: () => ({
    chats:counters.size,
    queued:[...counters.values()].reduce((sum, item) => sum + item, 0),
  }) }
}
