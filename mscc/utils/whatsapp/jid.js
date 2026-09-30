import { jidNormalizedUser } from '@itsliaaa/baileys'

export const digits = value => String(value || '').replace(/\D/g, '')

export const normalizeJid = jid => jidNormalizedUser(jid || '')

export const jidUser = jid => String(normalizeJid(jid)).split('@')[0].split(':')[0]

export const jidForPhone = value => {
  const number = digits(value)
  return number ? `${number}@s.whatsapp.net` : ''
}

export const selfJid = account => jidForPhone(account?.number)

export const isGroupJid = jid => normalizeJid(jid).endsWith('@g.us')

export const isDirectJid = jid => normalizeJid(jid).endsWith('@s.whatsapp.net')

export const isLidJid = jid => normalizeJid(jid).endsWith('@lid')

export const isTrackableJid = jid => {
  const value = normalizeJid(jid)
  return value.endsWith('@g.us') || value.endsWith('@s.whatsapp.net') || value.endsWith('@lid')
}

export const maskedPhone = value => {
  const number = digits(value)
  if (!number) return 'Not configured'
  return number.length < 8 ? number : `${number.slice(0, 3)}••••${number.slice(-4)}`
}

export const sameUserJid = (left, right) => {
  const a = jidUser(left)
  const b = jidUser(right)
  return Boolean(a && b && a === b)
}
