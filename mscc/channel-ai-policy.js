// WhatsApp Channels are newsletters, not direct messages or groups.
// A channel is ALWAYS opted out unless the supreme owner explicitly enables
// this exact JID for one linked bot account. Settings live in shared SQLite.
const NS = 'channel-ai-opt-in-v1'
const CHANNEL_JID = /^\d{8,25}@newsletter$/

export function isChannelJid(jid) {
  return CHANNEL_JID.test(String(jid || '').trim().toLowerCase())
}

export function channelAiSetting(storage, jid) {
  const chat = String(jid || '').trim().toLowerCase()
  if (!isChannelJid(chat)) return { enabled:false, accountId:'' }
  const row = storage?.sharedGet?.(NS, chat)
  return row?.enabled === true && typeof row.accountId === 'string' && row.accountId.trim()
    ? { enabled:true, accountId:row.accountId.trim() }
    : { enabled:false, accountId:'' }
}

export function channelAiAllowed(storage, jid, accountId) {
  const setting = channelAiSetting(storage, jid)
  return setting.enabled && Boolean(accountId) && setting.accountId === String(accountId)
}

export function setChannelAiSetting(storage, jid, { enabled, accountId = '' } = {}) {
  const chat = String(jid || '').trim().toLowerCase()
  if (!isChannelJid(chat)) throw new Error('Invalid WhatsApp Channel JID.')
  if (typeof storage?.sharedSet !== 'function') {
    throw new Error('Persistent channel AI settings are unavailable; refusing to enable AI.')
  }
  const active = enabled === true
  if (active && !String(accountId || '').trim()) throw new Error('A specific bot account is required.')
  const row = { enabled:active, accountId:active ? String(accountId).trim() : '', updatedAt:Date.now() }
  storage.sharedSet(NS, chat, row)
  return channelAiSetting(storage, chat)
}
