// Read only the linked person's verified WhatsApp avatar through existing
// authenticated Baileys sockets. Store a bounded validated image in MSCC
// SQLite; never expose PN/LID, WhatsApp CDN tokens or a third-party URL.
const MAX_BYTES = 196608
const ALLOWED_IMAGE_HOST = hostname => hostname === 'whatsapp.net' || hostname.endsWith('.whatsapp.net') ||
  hostname === 'whatsapp.com' || hostname.endsWith('.whatsapp.com') ||
  hostname === 'fbcdn.net' || hostname.endsWith('.fbcdn.net')
function acceptableUrl(raw) {
  try {
    const parsed = new URL(String(raw || ''))
    return parsed.protocol === 'https:' && !parsed.username && !parsed.password && ALLOWED_IMAGE_HOST(parsed.hostname.toLowerCase())
  } catch { return false }
}
const withDeadline = (promise, ms) => new Promise((resolve, reject) => {
  const timer = setTimeout(() => reject(new Error('Avatar lookup timed out')), ms)
  Promise.resolve(promise).then(value => { clearTimeout(timer); resolve(value) },
    error => { clearTimeout(timer); reject(error) })
})
export function createNightPlayAvatarSync(play, accounts, { fetcher = fetch, now = Date.now, cooldownMs = 15 * 60_000 } = {}) {
  const lastAttempt = new Map()
  const inflight = new Map()
  return async userId => {
    if (inflight.has(userId)) return inflight.get(userId)
    if (now() - (lastAttempt.get(userId) || 0) < cooldownMs) return false
    lastAttempt.set(userId, now())
    const job = (async () => {
      const jids = play.avatarCandidates(userId)
      if (!jids.length) return false
      const sockets = [...accounts.values()].map(a => a?.sock).filter(s => s?.profilePictureUrl)
      const deadline = now() + 6500
      for (const sock of sockets) {
        for (const jid of jids) {
          const left = deadline - now()
          if (left < 600) return false
          try {
            const candidate = await withDeadline(sock.profilePictureUrl(jid, 'image'), Math.min(left, 1500))
            if (!acceptableUrl(candidate)) continue
            const response = await fetcher(candidate, { signal:AbortSignal.timeout(Math.min(deadline - now(), 3500)), redirect:'follow' })
            if (!response.ok || !acceptableUrl(response.url || candidate)) continue
            const type = String(response.headers?.get('content-type') || '').split(';')[0].toLowerCase()
            if (type !== 'image/jpeg' && type !== 'image/png') continue
            if (Number(response.headers?.get('content-length') || 0) > MAX_BYTES) continue
            const chunks = []; let total = 0
            for await (const chunk of response.body) {
              total += chunk.length
              if (total > MAX_BYTES) { try { await response.body.cancel() } catch {} ; return false }
              chunks.push(Buffer.from(chunk))
            }
            if (play.saveAvatar(userId, Buffer.concat(chunks))) return true
          } catch { /* unavailable or blocked by WhatsApp privacy: keep default silhouette */ }
        }
      }
      return false
    })()
    inflight.set(userId, job)
    try { return await job } finally { inflight.delete(userId) }
  }
}
