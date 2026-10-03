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
