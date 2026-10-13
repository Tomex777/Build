import { randomUUID } from 'node:crypto'

const NS = 'night-group-schedules'
const DEFAULT_TZ = 'Africa/Lagos'
const WEEK = ['sun','mon','tue','wed','thu','fri','sat']
const TZ_FORMATS = new Map()

function formatter(zone) {
  if (!TZ_FORMATS.has(zone)) TZ_FORMATS.set(zone, new Intl.DateTimeFormat('en-GB', {
    timeZone:zone, year:'numeric', month:'2-digit', day:'2-digit',
    hour:'2-digit', minute:'2-digit', hourCycle:'h23',
  }))
  return TZ_FORMATS.get(zone)
}

export function checkTimezone(zone) {
  const value = String(zone || '').trim()
  try { formatter(value).format(new Date()) } catch { throw new Error('Invalid IANA timezone. Example: Africa/Lagos.') }
  return value
}

function localParts(epoch, zone) {
  const parts = Object.fromEntries(formatter(zone).formatToParts(new Date(epoch))
    .filter(p => p.type !== 'literal').map(p => [p.type, Number(p.value)]))
  return { year:parts.year, month:parts.month, day:parts.day, hour:parts.hour, minute:parts.minute }
}

function zonedUtc(y, m, d, hour, minute, zone) {
  const naive = Date.UTC(y, m-1, d, hour, minute)
  let guess = naive
  for (let i=0;i<3;i++) {
    const p = localParts(guess, zone)
    const represented = Date.UTC(p.year, p.month-1, p.day, p.hour, p.minute)
    guess += naive - represented
  }
  const p = localParts(guess, zone)
  return p.year===y && p.month===m && p.day===d && p.hour===hour && p.minute===minute ? guess : null
}

export function nextScheduleTime(time, days, zone, after = Date.now()) {
  const match = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(time)
  if (!match) throw new Error('Use a 24-hour time such as 22:00.')
  const today = localParts(after, zone)
  const midnight = Date.UTC(today.year, today.month-1, today.day)
  for (let offset=0;offset<=8;offset++) {
    const date = new Date(midnight + offset*86400000)
    if (!days.includes(date.getUTCDay())) continue
    const timestamp = zonedUtc(date.getUTCFullYear(),date.getUTCMonth()+1,date.getUTCDate(),
      Number(match[1]),Number(match[2]),zone)
    if (timestamp !== null && timestamp > after) return timestamp
  }
  throw new Error('Could not calculate the next group schedule occurrence.')
}

export function scheduleDays(raw = 'daily') {
  const value = String(raw || 'daily').trim().toLowerCase()
  if (['daily','everyday','all'].includes(value)) return [0,1,2,3,4,5,6]
  if (['weekdays','weekday'].includes(value)) return [1,2,3,4,5]
  if (['weekends','weekend'].includes(value)) return [0,6]
  const entries = value.split(',').map(x=>x.trim()).filter(Boolean)
  const days = [...new Set(entries.map(x=>WEEK.indexOf(x.slice(0,3))))]
  if (!entries.length || days.includes(-1)) throw new Error('Days: daily, weekdays, weekends or mon,tue,wed.')
  return days.sort((a,b)=>a-b)
}

function read(storage, group) {
  const saved = storage?.sharedGet?.(NS, group)
  return {
    timezone:saved?.timezone || DEFAULT_TZ,
    entries:Array.isArray(saved?.entries) ? saved.entries : [],
  }
}

function write(storage, group, value) {
  storage?.sharedSet?.(NS, group, value)
  return value
}

export function groupScheduleInfo(storage, group) {
  return structuredClone(read(storage,group))
}

export function setGroupScheduleTimezone(storage, group, raw) {
  const zone = checkTimezone(raw)
  const current = read(storage,group)
  current.timezone = zone
  current.entries = current.entries.map(row=>({
    ...row, nextAt:nextScheduleTime(row.time,row.days,zone,Date.now()),
  }))
  return write(storage,group,current)
}

export function addGroupSchedule(storage, { group, accountId, action, time, days, now=Date.now() }) {
  if (!group || !accountId) throw new Error('Missing group or bot account.')
  if (!['open','close'].includes(action)) throw new Error('Expected open or close.')
  const current = read(storage,group)
  const weekdays = scheduleDays(days)
  const nextAt = nextScheduleTime(time,weekdays,current.timezone,now)
  if (current.entries.length >= 20) throw new Error('A group can have at most 20 schedules.')
  if (current.entries.some(row=>row.action===action && row.time===time && JSON.stringify(row.days)===JSON.stringify(weekdays))) {
    throw new Error('That group schedule already exists.')
  }
  const row = { id:randomUUID().slice(0,8), accountId, action, time, days:weekdays,
    nextAt, createdAt:now }
  current.entries.push(row)
  write(storage,group,current)
  return row
}

export function removeGroupSchedule(storage, group, id) {
  const current = read(storage,group)
  const wanted = String(id || '').toLowerCase()
  const next = wanted === 'all' ? [] : current.entries.filter(row=>row.id.toLowerCase() !== wanted)
  if (next.length === current.entries.length) return false
  current.entries = next
  write(storage,group,current)
  return true
}

// Shared storage exposes lists through sharedList(namespace), used here to avoid
// a separate shadow index which can get out of sync on crashes.
export function dueGroupSchedules(storage, now=Date.now()) {
  const rows = storage?.sharedEntries?.(NS) || []
  const due = []
  for (const item of rows) {
    const group = String(item.key || '')
    const state = read(storage,group)
    for (const rule of state.entries) {
      if (Number(rule.nextAt) <= now) due.push({ group, timezone:state.timezone, ...rule })
    }
  }
  return due.sort((a,b)=>a.nextAt-b.nextAt).slice(0,100)
}

export function finishGroupSchedule(storage, group, id, now=Date.now()) {
  const state = read(storage,group)
  const match = state.entries.find(x=>x.id===id)
  if (!match) return false
  match.nextAt = nextScheduleTime(match.time,match.days,state.timezone,now + 60000)
  write(storage,group,state)
  return true
}

export function groupScheduleDaysLabel(days) {
  if (days.length===7) return 'daily'
  if (JSON.stringify(days)==='[1,2,3,4,5]') return 'weekdays'
  if (JSON.stringify(days)==='[0,6]') return 'weekends'
  return days.map(day=>WEEK[day]).join(',')
}
