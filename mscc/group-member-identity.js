/**
 * Group participant identity helpers.
 *
 * WhatsApp LIDs are opaque identifiers: never infer a phone number from a LID's
 * numeric component. Prefer verified group participant IDs for actions/mentions.
 */
const normalizeId = value => String(value || '').trim().replace(/:\d+(?=@)/, '')

const phoneJid = value => {
  const v = normalizeId(value)
  if (/^\+?\d{7,15}$/.test(v)) return v.replace('+', '') + '@s.whatsapp.net'
  return v
}

const isPhoneJid = jid => /^\d{7,15}@s\.whatsapp\.net$/.test(normalizeId(jid))

export function participantIds(participant) {
  if (typeof participant === 'string') return [normalizeId(participant)].filter(Boolean)
  if (!participant || typeof participant !== 'object') return []
  return [...new Set([
    participant.id,
    participant.jid,
    participant.lid,
    participant.phoneNumber,
    participant.pnJid,
    participant.userJid,
  ].map(phoneJid).filter(Boolean))]
}

export function groupTargetJid(raw = '', info = null) {
  const token = String(raw || '').trim()
  const mentions = Array.isArray(info?.mentionedJid)
    ? info.mentionedJid.map(normalizeId).filter(Boolean)
    : []

  if (token.startsWith('@')) {
    if (mentions.length) return mentions[0]
    if (/^@\d{7,15}$/.test(token)) return token.slice(1) + '@s.whatsapp.net'
    return ''
  }

  if (/^\+?[\d(). -]{7,}$/.test(token)) {
    const number = token.replace(/\D/g, '')
    if (/^\d{7,15}$/.test(number)) return number + '@s.whatsapp.net'
  }

  // A reply's participant is the target, not a mention inside the quoted text.
  if (info?.stanzaId && info?.participant) return normalizeId(info.participant)
  return mentions[0] || ''
}

export async function findGroupParticipant(participants = [], targetJid = '', resolvePhoneJid = async jid => jid) {
  const target = phoneJid(targetJid)
  if (!target) return null
  const rows = (Array.isArray(participants) ? participants : [])
    .map(participant => ({ participant, ids:participantIds(participant) }))
    .filter(row => row.ids.length)

  // First match the exact PN or LID; this remains safe if LID->PN lookup fails.
  const direct = rows.find(row => row.ids.includes(target))
  if (direct) return { ...direct, jid:direct.ids[0] }

  // Only compare verified phone JIDs; numeric LID IDs are NOT phone numbers.
  const asPhone = async jid => {
    if (isPhoneJid(jid)) return normalizeId(jid)
    try {
      const resolved = normalizeId(await resolvePhoneJid(jid))
      return isPhoneJid(resolved) ? resolved : ''
    } catch {
      return ''
    }
  }
  const targetPhone = await asPhone(target)
  if (!targetPhone) return null

  for (const row of rows) {
    for (const alias of row.ids) {
      if (await asPhone(alias) === targetPhone) return { ...row, jid:row.ids[0] }
    }
  }
  return null
}

const cleanName = value => {
  const name = String(value || '').replace(/[\r\n\t]+/g, ' ').trim().slice(0, 100)
  return /^(?:whatsapp user|unknown(?: user)?|user|member)$/i.test(name) ? '' : name
}

export function groupMemberLabel({ participant, historyNames = [], pushName = '', jid = '' } = {}) {
  const names = [
    participant?.notify,
    participant?.displayName,
    participant?.name,
    participant?.pushName,
    ...historyNames,
    pushName,
  ]
  const displayName = names.map(cleanName).find(Boolean) || ''
  const identifier = normalizeId(jid).split('@')[0]
  const mentionText = '@' + (displayName.replace(/^@/, '') || identifier || 'member')
  return { displayName:displayName || mentionText, mentionText }
}

export function memberIdentityKey(phoneJidValue, memberJid) {
  const pn = normalizeId(phoneJidValue)
  if (isPhoneJid(pn)) return pn.split('@')[0]
  const jid = normalizeId(memberJid)
  return jid ? 'jid:' + jid : ''
}

export function updateSucceeded(response) {
  const rows = Array.isArray(response) ? response : response ? [response] : []
  if (!rows.length) return null
  const codes = rows.map(row => Number(row?.status ?? row?.code))
  if (codes.some(code => !Number.isFinite(code))) return null
  return codes.every(code => code >= 200 && code < 300)
}
