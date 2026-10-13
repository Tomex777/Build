// Read-only activity reports from MSCC's existing deduplicated chat history.
// No separate counter writes, paid rewards, or user-message content in reports.
export const ACTIVITY_PERIODS = Object.freeze({
  '24h': { hours: 24, label: 'last 24 hours' },
  '7d': { hours: 168, label: 'last 7 days' },
  '30d': { hours: 720, label: 'last 30 days' },
})

export function activityPeriod(raw = '') {
  const value = String(raw || '').trim().toLowerCase()
  const normalized = { today:'24h', daily:'24h', week:'7d', weekly:'7d', month:'30d', monthly:'30d' }[value] || value
  return { key:ACTIVITY_PERIODS[normalized] ? normalized : '7d', ...(ACTIVITY_PERIODS[normalized] || ACTIVITY_PERIODS['7d']) }
}

export function activitySenderKey(jid = '') {
  const value = String(jid || '').trim().replace(/:\d+(?=@)/, '')
  if (/^\d{7,15}@s\.whatsapp\.net$/.test(value)) return value.split('@')[0]
  // Never interpret the numerical part of an opaque LID as a phone number.
  return value ? 'jid:' + value : ''
}

export function effectiveSenderKey(row = {}) {
  return String(row.senderKey || '').trim() || activitySenderKey(row.participantJid)
}

function freshMember(key) {
  return { key, name:'Member', messages:0, text:0, photos:0, videos:0, stickers:0, audio:0, documents:0, otherMedia:0, media:0 }
}

export function aggregateActivity(rows = [], { nowMs = Date.now(), period = '7d' } = {}) {
  const window = activityPeriod(period)
  const cutoff = nowMs - window.hours * 3600000
  const members = new Map()
  let total = 0
  for (const row of rows) {
    if (row?.fromBot) continue
    const atMs = Number(row?.atMs || 0)
    if (!atMs || atMs < cutoff || atMs > nowMs + 60000) continue
    const key = effectiveSenderKey(row)
    if (!key) continue
    if (!members.has(key)) members.set(key, freshMember(key))
    const member = members.get(key)
    const name = String(row.speaker || '').trim()
    if (name && !/^(?:unknown|whatsapp user)$/i.test(name) && member.name === 'Member') {
      member.name = name.slice(0, 50).replace(/[\r\n\t]+/g, ' ')
    }
    member.messages += 1
    total += 1
    const kind = String(row.mediaType || '').trim().toLowerCase()
    if (!kind) member.text += 1
    else {
      member.media += 1
      const category = { image:'photos', video:'videos', sticker:'stickers', audio:'audio', document:'documents' }[kind] || 'otherMedia'
      member[category] += 1
    }
  }
  const ranked = [...members.values()].sort((a,b) => b.messages - a.messages || a.key.localeCompare(b.key))
  return {
    period:window,
    total,
    uniqueMembers:ranked.length,
    ranked:ranked.map((member,index) => ({ ...member, rank:index + 1 })),
    totals:ranked.reduce((out,member) => {
      for (const field of ['text','photos','videos','stickers','audio','documents','otherMedia','media']) out[field] += member[field]
      return out
    }, { text:0, photos:0, videos:0, stickers:0, audio:0, documents:0, otherMedia:0, media:0 }),
  }
}

export function activityBadges(messages) {
  const n = Number(messages || 0)
  const levels = [
    { threshold:25, name:'Contributor', icon:'🥉' },
    { threshold:75, name:'Regular', icon:'🥈' },
    { threshold:150, name:'Community Star', icon:'🥇' },
    { threshold:300, name:'Legend', icon:'🏆' },
  ]
  return { earned:levels.filter(item => n >= item.threshold), next:levels.find(item => n < item.threshold) || null }
}

export function activityReport(storage, groupJid, { period = '7d', nowMs = Date.now() } = {}) {
  const window = activityPeriod(period)
  const rows = storage?.listConversationMessages?.({
    chatJid:groupJid,
    sinceMs:nowMs - window.hours * 3600000,
    limit:25000,
    ascending:false,
  }) || []
  return aggregateActivity(rows, { nowMs, period:window.key })
}
