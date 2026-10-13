import assert from 'node:assert/strict'
import { createMessageScheduler, isStatelessFastCommand } from './command-scheduler.js'

for (const command of ['.ping', '.menu', '.nami', '.mimi', '.help', '.sources', '.alive']) {
  assert.equal(isStatelessFastCommand(command), true, command)
}
for (const input of ['1', '18kami', '.anime', '.manga', '.anime ~source animepahe',
  '.source anime animepahe', '.ping extra', '.account', '.ttt']) {
  assert.equal(isStatelessFastCommand(input), false, input)
}
assert.equal(isStatelessFastCommand('!ping', '!'), true)

const events = []
const locks = new Map()
const process = async (account, msg) => {
  const command = msg.message.conversation
  events.push('start:' + account.id + ':' + command)
  if (command === '.anime' || command === '.manga') {
    await new Promise(resolve => locks.set(account.id + ':' + command, resolve))
  }
  events.push('done:' + account.id + ':' + command)
}
const scheduler = createMessageScheduler({ process })
const accountA = { id:'nami', generation:1 }
const accountB = { id:'josia', generation:1 }
const make = (chat, value) => ({ key:{ remoteJid:chat }, message:{ conversation:value } })
const wait = () => new Promise(resolve=>setImmediate(resolve))

// Within a single Baileys batch, .ping must not wait for slow .anime.
// Plain numbered/source selection remains queued in chat order.
const a = scheduler.submitBatch(accountA, [
  make('room@g.us', '.anime'),
  make('room@g.us', '.ping'),
  make('room@g.us', '1'),
])
await wait()
assert(events.includes('start:nami:.anime'))
assert(events.includes('done:nami:.ping'), 'fast ping blocked by anime command')
assert(!events.includes('start:nami:1'), 'stateful selection overtook anime')

// The same slow anime must not obstruct Josia or a separate Nami chat.
const b = scheduler.submit(accountB, make('room@g.us', '.ping'))
const c = scheduler.submit(accountA, make('other@g.us', '.manga'))
await wait()
assert(events.includes('done:josia:.ping'), 'other account blocked')
assert(events.includes('start:nami:.manga'), 'other chat blocked')

locks.get('nami:.anime')()
await a
assert(events.indexOf('done:nami:.anime') < events.indexOf('start:nami:1'), 'chat queue broke ordering')
locks.get('nami:.manga')()
await Promise.all([b, c])
assert.deepEqual(scheduler.stats(), { chats:0, queued:0 })

// An older disconnected socket must never dispatch queued messages through
// a replacement socket. Clean up the queue after the in-flight message exits.
const waitGen = scheduler.submit(accountA, make('room@g.us','.anime'))
await wait()
const stale = scheduler.submit(accountA, make('room@g.us', '2'))
accountA.generation = 2
locks.get('nami:.anime')()
await Promise.all([waitGen, stale])
assert(!events.includes('start:nami:2'))
assert.deepEqual(scheduler.stats(), { chats:0, queued:0 })

// Queue overload is confined to the offending chat; don't block .ping.
const warnings=[]
let unlock
const limited = createMessageScheduler({
  process: async (_, msg) => { if (msg.message.conversation === '.anime') await new Promise(resolve=>{unlock=resolve}) },
  maxPendingPerChat:2, log:warning=>warnings.push(warning),
})
const held = limited.submit(accountA, make('busy@g.us', '.anime'))
await wait()
const follow = limited.submit(accountA, make('busy@g.us', '3'))
assert.equal(await limited.submit(accountA, make('busy@g.us', '4')), false)
await limited.submit(accountA, make('busy@g.us', '.ping'))
unlock()
await Promise.all([held, follow])
assert.equal(warnings.length,1)
assert.deepEqual(limited.stats(), { chats:0, queued:0 })
console.log('PASS fast ping while anime stalls; independent bot/chat scheduling; FIFO choices; stale socket guard')
