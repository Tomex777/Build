// Redacted timing markers for diagnosing slow WhatsApp commands.
// Never store chats, message text, phone numbers, LIDs, or command arguments.
export function commandNameForTiming(text, prefix = '.') {
  const value = String(text || '').trim()
  if (!prefix || !value.startsWith(prefix)) return ''
  const name = value.slice(prefix.length).trim().split(/\s+/)[0].toLowerCase()
  return /^[a-z0-9_-]{1,40}$/.test(name) ? name : ''
}

export function createCommandTiming({
  accountId = '',
  text = '',
  prefix = '.',
  now = () => performance.now(),
  log = message => console.warn(message),
  thresholdMs = 2000,
} = {}) {
  const command = commandNameForTiming(text, prefix)
  if (!command) return null
  const started = now()
  let previous = started
  let sendMs = 0
  const phases = {}

  return {
    mark(phase) {
      const moment = now()
      phases[String(phase)] = (phases[String(phase)] || 0) + Math.max(0, moment - previous)
      previous = moment
    },
    async send(fn) {
      const startedSend = now()
      try {
        return await fn()
      } finally {
        sendMs += Math.max(0, now() - startedSend)
      }
    },
    finish() {
      const total = Math.max(0, now() - started)
      const limit = Math.max(0, Number(thresholdMs) || 0)
      if (total < limit) return false
      const details = Object.entries(phases)
        .map(([phase, duration]) => `${phase}=${Math.round(duration)}ms`)
        .join(' ')
      log(
        `[MSCC command-latency] account=${String(accountId).replace(/[^a-zA-Z0-9_-]/g, '').slice(0,48)} ` +
        `command=.${command} total=${Math.round(total)}ms ${details} send=${Math.round(sendMs)}ms`
      )
      return true
    },
  }
}
