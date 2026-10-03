import assert from 'node:assert/strict'
import {
  addScheduledTask,
  completeScheduledTask,
  dueScheduledTasks,
  formatDue,
  listScheduledTasks,
  parseDuration,
  removeScheduledTask,
} from './scheduled-tasks.js'

const map = new Map()
const storage = {
  sharedGet:(ns,key) => map.get(ns + '|' + key) ?? null,
  sharedSet:(ns,key,value) => { map.set(ns + '|' + key, structuredClone(value)); return value },
}

assert.equal(parseDuration('20m'), 20 * 60000)
assert.equal(parseDuration('2h'), 2 * 3600000)
assert.equal(parseDuration('3d'), 3 * 86400000)
assert.equal(parseDuration('bad'), 0)

const now = Date.now()
const a = addScheduledTask(storage, { userKey:'111', kind:'reminder', text:'one', dueAt:now + 60000 })
const b = addScheduledTask(storage, { userKey:'111', kind:'timer', text:'two', dueAt:now + 120000 })
addScheduledTask(storage, { userKey:'222', kind:'reminder', text:'other', dueAt:now + 90000 })

assert.equal(listScheduledTasks(storage, '111').length, 2)
assert.equal(dueScheduledTasks(storage, now + 70000).some(row => row.id === a.id), true)
assert.equal(dueScheduledTasks(storage, now + 70000).some(row => row.id === b.id), false)
assert.equal(removeScheduledTask(storage, '111', b.id), true)
assert.equal(listScheduledTasks(storage, '111').length, 1)
assert.equal(completeScheduledTask(storage, a.id), true)
assert.equal(listScheduledTasks(storage, '111').length, 0)
assert.match(formatDue(now + 60000, now), /1m/)

console.log('PASS persistent reminders/timers scheduling and cancellation')
