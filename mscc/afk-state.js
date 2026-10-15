import { contextInfo } from './utils/whatsapp/messages.js'
import { jidUser, normalizeJid } from './utils/whatsapp/jid.js'

const NS = 'group-afk'
const MAX_EVENTS = 40

function stateKey(group, phone) {
  return String(group || '') + '|' + String(phone || '')
}

export function setAfk(storage, group, phone, {
  reason = '',
  displayName = '',
  at = Date.now(),
} = {}) {
  const state = {
    group:String(group || ''),
    phone:String(phone || ''),
    reason:String(reason || '').trim().slice(0,500),
    displayName:String(displayName || '').trim().slice(0,120),
    since:Number(at || Date.now()),
    events:[],
  }
  storage?.sharedSet?.(NS, stateKey(group,phone), state)
  return state
}

// Preserve the most recent AFK state and events when verified identities join.
export function reconcileVerifiedAfk(storage, group, preferred, keys = []) {
  if (!preferred || !storage?.sharedGet || !storage?.sharedSet || !storage?.sharedDelete) return
  const aliases = [...new Set(keys)].filter(id => id && id !== preferred)
  const original = storage.sharedGet(NS, stateKey(group, preferred))
  const extras = aliases.map(id => ({ id, state:storage.sharedGet(NS, stateKey(group, id)) }))
    .filter(row => row.state)
  if (!extras.length) return
  const states = [original, ...extras.map(row => row.state)].filter(Boolean)
  const newest = [...states].sort((a,b) => Number(b.since || 0) - Number(a.since || 0))[0]
  const events = states.flatMap(row => Array.isArray(row.events) ? row.events : [])
    .sort((a,b) => Number(a.at || 0) - Number(b.at || 0)).slice(-MAX_EVENTS)
  storage.sharedSet(NS, stateKey(group, preferred), {
    ...newest, group:String(group), phone:preferred, events,
  })
  for (const { id } of extras) storage.sharedDelete(NS, stateKey(group, id))
}

export function getAfk(storage, group, phone) {
  return storage?.sharedGet?.(NS, stateKey(group,phone)) || null
}

export function clearAfk(storage, group, phone) {
  const state = getAfk(storage, group, phone)
  if (state) storage?.sharedDelete?.(NS, stateKey(group,phone))
  return state
}

export function addAfkEvent(storage, group, phone, event = {}) {
  const state = getAfk(storage, group, phone)
  if (!state) return null
  const row = {
    senderPhone:String(event.senderPhone || ''),
    senderJid:normalizeJid(event.senderJid),
    senderName:String(event.senderName || '').trim().slice(0,120),
    text:String(event.text || '').trim().slice(0,1200),
    kind:String(event.kind || 'mention'),
    at:Number(event.at || Date.now()),
  }
  const next = {
    ...state,
    events:[...(Array.isArray(state.events) ? state.events : []), row].slice(-MAX_EVENTS),
  }
  storage?.sharedSet?.(NS, stateKey(group,phone), next)
  return next
}

export function messageMentionJids(message) {
  const info = contextInfo(message)
  return [...new Set((info?.mentionedJid || []).map(normalizeJid).filter(Boolean))]
}

export function quotedParticipantJid(message) {
  const info = contextInfo(message)
  return normalizeJid(info?.participant || info?.remoteJid || '')
}

export function plainMentionNames(message, excludedPhone = '') {
  return messageMentionJids(message)
    .map(jid => jidUser(jid))
    .filter(phone => phone && phone !== String(excludedPhone || ''))
}
