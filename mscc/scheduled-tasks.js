import { randomUUID } from 'node:crypto'

const NS = 'night-scheduler'
const KEY = 'tasks'
const MAX_TASKS = 5000

function tasks(storage) {
  const value = storage?.sharedGet?.(NS, KEY)
  return Array.isArray(value) ? value : []
}

function save(storage, rows) {
  const clean = [...rows]
    .filter(row => row?.id && Number(row?.dueAt) > 0)
    .sort((a,b) => Number(a.dueAt) - Number(b.dueAt))
    .slice(-MAX_TASKS)
  storage?.sharedSet?.(NS, KEY, clean)
  return clean
}

export function parseDuration(value) {
  const raw = String(value || '').trim().toLowerCase()
  const match = /^(\d+(?:\.\d+)?)(s|sec|secs|m|min|mins|h|hr|hrs|d|day|days|w|week|weeks)$/.exec(raw)
  if (!match) return 0
  const n = Number(match[1])
  const unit = match[2]
  const factor = unit.startsWith('s') ? 1000
    : unit.startsWith('m') ? 60000
      : unit.startsWith('h') ? 3600000
        : unit.startsWith('d') ? 86400000
          : 7 * 86400000
  return Math.round(n * factor)
}

export function addScheduledTask(storage, {
  userKey,
  kind = 'reminder',
  text = '',
  dueAt,
  meta = {},
  createdAt = Date.now(),
} = {}) {
  const user = String(userKey || '').trim()
  const due = Number(dueAt || 0)
  if (!user || !Number.isFinite(due) || due <= Date.now()) throw new Error('The reminder time must be in the future.')

  const row = {
    id:randomUUID().slice(0,8),
    userKey:user,
    kind:String(kind || 'reminder'),
    text:String(text || '').trim().slice(0, 2000),
    meta:meta && typeof meta === 'object' ? structuredClone(meta) : {},
    dueAt:due,
    createdAt:Number(createdAt || Date.now()),
  }
  save(storage, [...tasks(storage), row])
  return row
}

export function listScheduledTasks(storage, userKey) {
  const user = String(userKey || '').trim()
  return tasks(storage).filter(row => row.userKey === user).sort((a,b) => a.dueAt - b.dueAt)
}

export function removeScheduledTask(storage, userKey, id) {
  const user = String(userKey || '').trim()
  const wanted = String(id || '').trim().toLowerCase()
  const rows = tasks(storage)
  const next = rows.filter(row => !(row.userKey === user && String(row.id).toLowerCase() === wanted))
  if (next.length === rows.length) return false
  save(storage, next)
  return true
}

export function dueScheduledTasks(storage, now = Date.now()) {
  return tasks(storage).filter(row => Number(row.dueAt) <= Number(now))
}

export function completeScheduledTask(storage, id) {
  const wanted = String(id || '')
  const rows = tasks(storage)
  const next = rows.filter(row => String(row.id) !== wanted)
  save(storage, next)
  return next.length !== rows.length
}

export function formatDue(ms, now = Date.now()) {
  const diff = Math.max(0, Number(ms) - Number(now))
  if (diff < 60000) return Math.max(1, Math.ceil(diff / 1000)) + 's'
  if (diff < 3600000) return Math.ceil(diff / 60000) + 'm'
  if (diff < 86400000) return (diff / 3600000).toFixed(diff < 10 * 3600000 ? 1 : 0).replace('.0','') + 'h'
  return (diff / 86400000).toFixed(diff < 10 * 86400000 ? 1 : 0).replace('.0','') + 'd'
}
