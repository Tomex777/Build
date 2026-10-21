// WhatsApp presence is best-effort: a slow/missing Baileys presence endpoint
// must never suppress or delay a real conversational answer.
const TYPING_REFRESH_MS = 8000
const MIN_VISIBLE_MS = 1100
const MAX_VISIBLE_MS = 3300

export function naturalTypingMs(text = '') {
  const count = [...String(text || '').trim()].length
  // A short message gets a believable beat; a paragraph gets a little more
  // time, but never a word-by-word crawl. Provider latency counts toward it.
  return Math.max(MIN_VISIBLE_MS, Math.min(MAX_VISIBLE_MS, 700 + count * 24))
}

export function createAssistantTyping({
  sock,
  chat,
  now = () => Date.now(),
  sleep = ms => new Promise(resolve => setTimeout(resolve, ms)),
  schedule = (callback, ms) => setInterval(callback, ms),
  unschedule = timer => clearInterval(timer),
  canSignal = () => true,
} = {}) {
  let startedAt = 0
  let active = false
  let timer = null

  function presence(kind) {
    if (typeof sock?.sendPresenceUpdate !== 'function' || !chat || !canSignal()) return
    // The caller never awaits presence: network failures cannot break chat.
    try {
      Promise.resolve(sock.sendPresenceUpdate(kind, chat)).catch(() => {})
    } catch {}
  }

  function start() {
    if (active || !chat || typeof sock?.sendPresenceUpdate !== 'function') return
    active = true
    startedAt = now()
    presence('composing')
    timer = schedule(() => {
      if (active) presence('composing')
    }, TYPING_REFRESH_MS)
    timer?.unref?.()
  }

  async function beforeSend(text) {
    if (!active) return
    const remaining = naturalTypingMs(text) - (now() - startedAt)
    if (remaining > 0) await sleep(remaining)
  }

  function stop() {
    if (!active) return
    active = false
    if (timer !== null) unschedule(timer)
    timer = null
    presence('paused')
  }

  return { start, beforeSend, stop }
}
