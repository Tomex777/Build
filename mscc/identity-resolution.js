// WhatsApp sometimes resolves LID -> phone via a socket request. Without a
// deadline, one stale/slow lookup delays even trivial .ping commands. Falling
// back to the original LID is conservative: unknown identity never inherits
// phone-based owner permissions.
export async function resolveIdentityWithDeadline({
  jid,
  lookup,
  normalize = value => String(value || ''),
  timeoutMs = 2500,
  onResolved = () => {},
} = {}) {
  const fallback = String(jid || '')
  if (typeof lookup !== 'function') return fallback
  let timer
  const request = Promise.resolve()
    .then(() => lookup(fallback))
    .then(result => {
      const phone = normalize(result?.phoneNumber || '')
      if (phone?.endsWith('@s.whatsapp.net')) {
        onResolved(phone)
        return phone
      }
      return fallback
    })
    .catch(() => fallback)
  const timeout = new Promise(resolve => {
    timer = setTimeout(() => resolve(fallback), Math.min(10000, Math.max(200, Number(timeoutMs) || 2500)))
  })
  try {
    return await Promise.race([request, timeout])
  } finally {
    clearTimeout(timer)
  }
}
