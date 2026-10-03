import { commandText, contextInfo } from './utils/whatsapp/messages.js'
import { jidUser, normalizeJid } from './utils/whatsapp/jid.js'

const NS_POLICY = 'group-policy'
const NS_MUTE = 'group-user-mute'
const NS_WARN = 'group-user-warning'
const URL_RE = /(?:https?:\/\/|www\.|\b[a-z0-9][a-z0-9-]{1,62}\.(?:com|net|org|io|co|me|app|dev|gg|tv|ly|ng|xyz|info|site|link|online)\b)/i
const GROUP_MENTION_RE = /(^|\s)@(?:all|everyone|group)(?=\s|$|[.!?,:;])/i
const spamWindows = new Map()

export const DEFAULT_GROUP_POLICY = Object.freeze({
  antiLink:false,
  antiTag:false,
  antiGroupMention:false,
  antiSpam:false,
  rulesText:'',
  filters:[],
  welcome:false,
  goodbye:false,
  aiGreet:false,
  welcomeText:'Welcome {user} to *{group}* 👋',
  goodbyeText:'Goodbye {user} 👋',
})

function key(group) {
  return String(group || '').trim()
}

export function groupPolicy(storage, group) {
  const saved = storage?.sharedGet?.(NS_POLICY, key(group)) || {}
  return { ...DEFAULT_GROUP_POLICY, ...saved }
}

export function setGroupPolicy(storage, group, patch = {}) {
  const current = groupPolicy(storage, group)
  const next = { ...current, ...patch, updatedAt:Date.now() }
  storage?.sharedSet?.(NS_POLICY, key(group), next)
  return next
}

function muteKey(group, recipientPhone, senderPhone) {
  return [key(group), String(recipientPhone || ''), String(senderPhone || '')].join('|')
}

export function setUserMentionMute(storage, group, recipientPhone, senderPhone, enabled = true) {
  const itemKey = muteKey(group, recipientPhone, senderPhone)
  if (!enabled) {
    storage?.sharedDelete?.(NS_MUTE, itemKey)
    return false
  }
  storage?.sharedSet?.(NS_MUTE, itemKey, {
    group:key(group),
    recipient:String(recipientPhone || ''),
    sender:String(senderPhone || ''),
    at:Date.now(),
  })
  return true
}

export function userMentionMuted(storage, group, recipientPhone, senderPhone) {
  return Boolean(storage?.sharedGet?.(NS_MUTE, muteKey(group, recipientPhone, senderPhone)))
}

function warnKey(group, phone) {
  return key(group) + '|' + String(phone || '')
}

export function addWarning(storage, group, phone, reason = '', by = '') {
  const itemKey = warnKey(group, phone)
  const current = storage?.sharedGet?.(NS_WARN, itemKey) || { count:0, history:[] }
  const row = {
    at:Date.now(),
    reason:String(reason || '').trim().slice(0, 500),
    by:String(by || ''),
  }
  const next = {
    count:Number(current.count || 0) + 1,
    history:[...(Array.isArray(current.history) ? current.history : []), row].slice(-20),
    updatedAt:Date.now(),
  }
  storage?.sharedSet?.(NS_WARN, itemKey, next)
  return next
}

export function clearWarnings(storage, group, phone) {
  return storage?.sharedDelete?.(NS_WARN, warnKey(group, phone)) || 0
}

export function warningState(storage, group, phone) {
  return storage?.sharedGet?.(NS_WARN, warnKey(group, phone)) || { count:0, history:[] }
}

export function renderGroupTemplate(template, {
  groupName = 'the group',
  mentions = [],
} = {}) {
  const users = mentions.length
    ? mentions.map(jid => '@' + jidUser(jid)).join(' ')
    : 'there'
  return String(template || '')
    .replaceAll('{group}', String(groupName || 'the group'))
    .replaceAll('{user}', users)
    .replaceAll('{users}', users)
    .replaceAll('{count}', String(mentions.length || 1))
}

function mentionedJids(msg) {
  const info = contextInfo(msg?.message)
  return [...new Set(
    (Array.isArray(info?.mentionedJid) ? info.mentionedJid : [])
      .map(normalizeJid)
      .filter(Boolean)
  )]
}

function hasGroupMention(msg, text) {
  const info = contextInfo(msg?.message)
  return Boolean(
    (Array.isArray(info?.groupMentions) && info.groupMentions.length) ||
    (Array.isArray(info?.groupMention) && info.groupMention.length) ||
    GROUP_MENTION_RE.test(String(text || ''))
  )
}

function escapeRegExp(value) {
  return String(value || '').replace(/[-/\\^$*+?.()|[\]{}]/g, '\\$&')
}

function removeMentionTokens(text, jids = [], phones = []) {
  let value = String(text || '')
  const tokens = new Set()
  for (const jid of jids) {
    const user = jidUser(jid)
    if (user) tokens.add(user)
  }
  for (const phone of phones) if (phone) tokens.add(String(phone))
  for (const token of tokens) {
    value = value.replace(new RegExp('@' + escapeRegExp(token) + '(?=\\b|\\s|$|[.,!?;:])', 'g'), '')
  }
  return value.replace(/[ \t]{2,}/g, ' ').replace(/ *\n */g, '\n').trim()
}

function spamViolation(group, senderPhone, text, now = Date.now()) {
  const key = String(group || '') + '|' + String(senderPhone || '')
  const normalized = String(text || '').replace(/\s+/g,' ').trim().toLowerCase()
  if (!normalized) return ''

  const prior = spamWindows.get(key) || []
  const rows = [...prior, { at:now, text:normalized }]
    .filter(row => now - Number(row.at || 0) <= 15000)
    .slice(-12)
  spamWindows.set(key, rows)

  const last10 = rows.filter(row => now - row.at <= 10000)
  if (last10.length >= 8) return 'rapid-message-spam'

  const repeated = rows.filter(row => row.text === normalized && now - row.at <= 20000)
  if (normalized.length >= 2 && repeated.length >= 3) return 'repeated-message-spam'
  return ''
}

function matchedFilter(filters, text) {
  const body = String(text || '').toLowerCase()
  if (!body) return null
  for (const item of Array.isArray(filters) ? filters : []) {
    const trigger = String(item?.trigger || '').trim().toLowerCase()
    if (!trigger || !body.includes(trigger)) continue
    return {
      trigger,
      action:String(item?.action || 'reply').toLowerCase(),
      response:String(item?.response || '').trim(),
    }
  }
  return null
}

export async function enforceGroupMessage({
  storage,
  msg,
  senderPhone,
  senderIsAdmin = false,
  botIsAdmin = false,
  resolvePhoneJid,
  deleteMessage,
  resendQuoted,
  replyMessage,
  warnMember,
  record = async () => {},
} = {}) {
  const group = normalizeJid(msg?.key?.remoteJid)
  if (!group?.endsWith('@g.us') || msg?.key?.fromMe || !senderPhone) return { handled:false }

  const text = commandText(msg?.message)
  const mentions = mentionedJids(msg)

  if (mentions.length && botIsAdmin) {
    const blocked = []
    const allowed = []
    const blockedPhones = []

    for (const jid of mentions) {
      let phone = jid.endsWith('@s.whatsapp.net') ? jidUser(jid) : ''
      if (!phone && typeof resolvePhoneJid === 'function') {
        try { phone = jidUser(await resolvePhoneJid(jid)) } catch {}
      }
      if (phone && userMentionMuted(storage, group, phone, senderPhone)) {
        blocked.push(jid)
        blockedPhones.push(phone)
      } else {
        allowed.push(jid)
      }
    }

    if (blocked.length) {
      await deleteMessage?.(msg)
      if (allowed.length) {
        const rebuilt = removeMentionTokens(text, blocked, blockedPhones)
        if (rebuilt) await resendQuoted?.(rebuilt, allowed, msg)
      }
      await record('group.user-mention-muted', {
        group,
        sender:senderPhone,
        mutedRecipients:blockedPhones,
        preservedMentions:allowed.length,
      })
      return { handled:true, reason:'user-mention-mute', deleted:true, resent:allowed.length > 0 }
    }
  }

  if (senderIsAdmin) return { handled:false }

  const policy = groupPolicy(storage, group)

  if (policy.antiSpam) {
    const spam = spamViolation(group, senderPhone, text)
    if (spam && botIsAdmin) {
      await deleteMessage?.(msg)
      await record('group.antispam-delete', { group, sender:senderPhone, reason:spam })
      return { handled:true, reason:spam, deleted:true }
    }
  }

  const filter = matchedFilter(policy.filters, text)
  if (filter) {
    if (filter.action === 'delete') {
      if (!botIsAdmin) return { handled:false, reason:'filter-delete-needs-admin' }
      await deleteMessage?.(msg)
      await record('group.filter-hit', { group, sender:senderPhone, trigger:filter.trigger, action:'delete' })
      return { handled:true, reason:'filter-delete', deleted:true }
    }
    if (filter.action === 'warn') {
      await warnMember?.(filter.trigger)
      await replyMessage?.('⚠️ Please avoid *' + filter.trigger + '* in this group.')
      await record('group.filter-hit', { group, sender:senderPhone, trigger:filter.trigger, action:'warn' })
      return { handled:true, reason:'filter-warn' }
    }
    if (filter.action === 'reply' && filter.response) {
      await replyMessage?.(filter.response)
      await record('group.filter-hit', { group, sender:senderPhone, trigger:filter.trigger, action:'reply' })
      return { handled:true, reason:'filter-reply' }
    }
  }

  let reason = ''

  if (policy.antiGroupMention && hasGroupMention(msg, text)) reason = 'group-mention'
  else if (policy.antiTag && mentions.length) reason = 'tag'
  else if (policy.antiLink && URL_RE.test(String(text || ''))) reason = 'link'

  if (!reason || !botIsAdmin) return { handled:false, reason:reason || '' }

  await deleteMessage?.(msg)
  await record('group.policy-delete', { group, sender:senderPhone, reason })
  return { handled:true, reason, deleted:true }
}

export const _test = {
  URL_RE,
  GROUP_MENTION_RE,
  removeMentionTokens,
}
