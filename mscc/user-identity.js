// One verified identity vocabulary for WhatsApp users.
// Never treat numeric LID components as telephone numbers.
const NS = 'verified-user-lid-phone-v1'
const pnPattern = /^([1-9]\d{6,14})(?::\d+)?@s\.whatsapp\.net$/
const lidPattern = /^\d+(?::\d+)?@(lid|hosted\.lid)$/

export function normalizedIdentityJid(value = '') {
  const jid = String(value || '').trim().toLowerCase()
  return jid.replace(/:\d+(?=@)/, '')
}

export function verifiedPhoneNumberFromJid(jid = '') {
  return pnPattern.exec(String(jid || '').trim().toLowerCase())?.[1] || ''
}

export function isOpaqueLid(jid = '') {
  return lidPattern.test(String(jid || '').trim().toLowerCase())
}

export function identityKeyForJid(jid = '') {
  const normalized = normalizedIdentityJid(jid)
  const phone = verifiedPhoneNumberFromJid(normalized)
  if (phone) return phone
  return isOpaqueLid(normalized) ? 'jid:' + normalized : ''
}

export function phoneJidFromMappingValue(value) {
  const raw = typeof value === 'string' || typeof value === 'number'
    ? String(value).trim()
    : ''
  const phone = verifiedPhoneNumberFromJid(raw) ||
    (/^[1-9]\d{6,14}$/.test(raw) ? raw : '')
  return phone ? phone + '@s.whatsapp.net' : ''
}

export async function readAuthLidPhone(authKeys, lidJid) {
  const lid = normalizedIdentityJid(lidJid)
  if (!isOpaqueLid(lid) || typeof authKeys?.get !== 'function') return ''
  const user = lid.split('@')[0]
  // Baileys multi-file and SQLite stores share the lid-mapping key contract.
  // A reverse key's value is a verified PN; do not derive one from lid digits.
  const id = user + '_reverse'
  try {
    const row = await authKeys.get('lid-mapping', [id])
    return phoneJidFromMappingValue(row?.[id])
  } catch {
    return ''
  }
}

export function hasConflictingLidMapping(storage, lidJid) {
  const lid = normalizedIdentityJid(lidJid)
  return isOpaqueLid(lid) &&
    storage?.sharedGet?.(NS, 'lid:' + lid)?.conflict === true
}

export function storedLidPhone(storage, lidJid) {
  const lid = normalizedIdentityJid(lidJid)
  if (!isOpaqueLid(lid)) return ''
  const row = storage?.sharedGet?.(NS, 'lid:' + lid)
  if (!row || row.conflict) return ''
  return phoneJidFromMappingValue(row.phone)
}

export function recordVerifiedLidPhone(storage, lidJid, phoneJid) {
  const lid = normalizedIdentityJid(lidJid)
  const pn = phoneJidFromMappingValue(phoneJid)
  if (!isOpaqueLid(lid) || !pn) return ''
  if (!storage?.sharedGet || !storage?.sharedSet) return pn
  const key = 'lid:' + lid
  const prior = storage.sharedGet(NS, key)
  if (prior?.conflict) return ''
  const previous = phoneJidFromMappingValue(prior?.phone)
  if (previous && previous !== pn) {
    // A conflicting mapping must NEVER grant phone-linked permissions.
    storage.sharedSet(NS, key, { conflict:true })
    return ''
  }
  storage.sharedSet(NS, key, { phone:pn })
  const reverseKey = 'pn:' + verifiedPhoneNumberFromJid(pn)
  const reverse = storage.sharedGet(NS, reverseKey)
  const lids = Array.isArray(reverse?.lids) ? reverse.lids : []
  if (!lids.includes(lid)) {
    storage.sharedSet(NS, reverseKey, { lids:[...lids, lid].slice(-24) })
  }
  return pn
}

export function canonicalUserKey(storage, jid = '') {
  const normalized = normalizedIdentityJid(jid)
  const direct = verifiedPhoneNumberFromJid(normalized)
  if (direct) return direct
  if (!isOpaqueLid(normalized)) return ''
  const mapped = storedLidPhone(storage, normalized)
  return verifiedPhoneNumberFromJid(mapped) || identityKeyForJid(normalized)
}

export function verifiedIdentityKeys(storage, jid = '') {
  const canonical = canonicalUserKey(storage, jid)
  if (!canonical) return []
  if (!/^\d{7,15}$/.test(canonical)) return [canonical]
  const reverse = storage?.sharedGet?.(NS, 'pn:' + canonical)
  const aliases = (Array.isArray(reverse?.lids) ? reverse.lids : [])
    .filter(lid => verifiedPhoneNumberFromJid(storedLidPhone(storage, lid)) === canonical)
    .map(lid => 'jid:' + lid)
  return [...new Set([canonical, ...aliases])]
}
