import assert from 'node:assert/strict'
import { mkdtemp, mkdir, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { AccountRegistry } from './account-registry.js'
import { createMessageScheduler } from './command-scheduler.js'
import { createInboundMessageGuard, matchesDisplayedChoice } from './inbound-message-guard.js'
import { parseNumberSelection } from './number-selection.js'

// Fixed seed ensures that failures are reproducible in GitHub Actions.
let seed = 0x5eed1a
const random = () => ((seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0) / 2 ** 32)
const rand = max => Math.floor(random() * max)

// 2,000 mixed-selection cases with an independent reference implementation.
// Verify caps, sorting, duplicate removal, missing numbers and decimal episodes.
const entries = Array.from({ length: 50 }, (_, index) => ({
  id: 'ep-' + index, number: String((index + 1) / 2),
}))
for (let trial = 0; trial < 2000; trial++) {
  const parts = []
  const total = 1 + rand(12)
  const limit = rand(32)
  for (let i = 0; i < total; i++) {
    const start = rand(60) / 2
    if (rand(3) === 0) {
      parts.push(String(start))
    } else {
      const end = rand(60) / 2
      parts.push(`${start}-${end}`)
    }
  }
  const input = parts.join(',')
  const model = new Set()
  for (const part of parts) {
    const tokens = part.split('-').map(Number)
    const low = Math.min(...tokens), high = Math.max(...tokens)
    for (const entry of entries) {
      const number = Number(entry.number)
      if (number >= low && number <= high) model.add(entry.id)
    }
  }
  const expected = entries.filter(entry => model.has(entry.id)).map(entry => entry.id)
  const result = parseNumberSelection(input, entries, { maxSelected: limit })
  if (expected.length > limit) {
    assert.equal(result.error, 'too-many', `trial ${trial}: ${input} cap=${limit}`)
  } else if (!expected.length) {
    assert.equal(result.error, 'not-found', `trial ${trial}: ${input}`)
  } else {
    assert.equal(result.ok, true, `trial ${trial}: ${input}`)
    assert.deepEqual(result.selected.map(entry => entry.id), expected, `trial ${trial}: ${input}`)
  }
}

// Guard bot-authored posts, duplicate upserts, chat/account isolation and TTL.
let claims = 0
const accept = createInboundMessageGuard({ maxEntries: 512, ttlMs: 1000, claim: () => { claims++; return true } })
const incoming = (chat, id, fromMe = false) => ({ key:{ remoteJid:chat, id, fromMe }, message:{ conversation:'hi' } })
assert.equal(accept('A', incoming('room@g.us', 'm1'), 1000), true)
assert.equal(accept('A', incoming('room@g.us', 'm1'), 1001), false)
assert.equal(accept('A', incoming('other@g.us', 'm1'), 1001), true)
assert.equal(accept('B', incoming('room@g.us', 'm1'), 1001), true)
assert.equal(accept('A', incoming('room@g.us', 'from-me', true), 1002), false)
assert.equal(claims, 3)
assert.equal(accept('A', incoming('room@g.us', 'm1'), 2002), true)
assert.equal(matchesDisplayedChoice('.manga', [{ title:'One' }]), false)
assert.equal(matchesDisplayedChoice('1', [{ title:'One' }]), true)
assert.equal(matchesDisplayedChoice('2', [{ title:'One' }]), false)
const refuse = createInboundMessageGuard({ claim:()=>false })
assert.equal(refuse('A', incoming('room@g.us', 'm2')), false)

// Thousands of simultaneous messages must preserve FIFO *within* each chat,
// not couple independent rooms, and continue after handler exceptions.
const errors = []
const processed = new Map()
const scheduler = createMessageScheduler({
  maxPendingPerChat: 300,
  log: error => errors.push(error),
  process: async (account, msg) => {
    const id = account.id + '/' + msg.key.remoteJid
    const n = Number(msg.message.conversation)
    const list = processed.get(id) || []
    list.push(n)
    processed.set(id, list)
    if (n % 4 === 0) await new Promise(resolve => setImmediate(resolve))
    if (n === 53) throw new Error('test-only handler failure')
  },
})
const jobs = []
for (const account of [{ id:'A', generation:1 }, { id:'B', generation:1 }]) {
  for (let c = 0; c < 6; c++) {
    const chat = `room-${c}@g.us`
    for (let n = 0; n < 120; n++) jobs.push(scheduler.submit(account, {
      key:{ remoteJid:chat }, message:{ conversation:String(n) },
    }))
  }
}
await Promise.all(jobs)
assert.equal(processed.size, 12)
for (const [chat, events] of processed) {
  assert.deepEqual(events, Array.from({ length:120 }, (_, n) => n), `FIFO broken: ${chat}`)
}
assert.equal(errors.length, 12, 'handler errors should be isolated to their chat')
assert.deepEqual(scheduler.stats(), { chats:0, queued:0 })

// Account control durability: concurrent mutations must result in a valid,
// complete JSON file and must never modify underlying WhatsApp auth tokens.
const root = await mkdtemp(join(tmpdir(), 'mscc-deep-audit-'))
try {
  const file = join(root, 'accounts.json')
  const registry = new AccountRegistry({ file, authRoot:join(root, 'auth'), maxAccounts:50 })
  await registry.load()
  const main = await registry.create({ phoneNumber:'2348000000001' })
  assert.equal(main.id, 'A')
  const created = await Promise.all(Array.from({ length:30 }, (_,i) =>
    registry.create({ phoneNumber:String(2348000000002 + i), displayName:'Linked '+i })))
  assert.equal(new Set(created.map(row => row.id)).size, 30)
  assert.equal(registry.list().length,31)
  assert.equal(registry.main().id,'A')
  const token = join(created[0].authDir, 'creds.json')
  await writeFile(token, 'SENTINEL-CREDENTIALS-DO-NOT-TOUCH')
  await Promise.all(created.flatMap(row => [
    registry.rename(row.id, 'Renamed-' + row.id),
    registry.setPaused(row.id, true),
  ]))
  const stored = JSON.parse(await readFile(file, 'utf8'))
  assert.equal(stored.accounts.length, 31)
  assert.equal(stored.accounts.filter(row => row.paused && row.role === 'linked').length, 30)
  assert.equal(new Set(stored.accounts.map(row => row.authDir)).size, 31)
  assert.equal((await stat(file)).mode & 0o077, 0, 'registry phones/auth locations must not be world-readable')
  const restored = new AccountRegistry({ file, authRoot:join(root,'auth'), maxAccounts:50 })
  await restored.load()
  assert.deepEqual(restored.list(), registry.list(), 'registry not durable across restart')
  assert.equal(await readFile(token, 'utf8'), 'SENTINEL-CREDENTIALS-DO-NOT-TOUCH')
  const removed = await Promise.all(created.slice(1,11).map(row => registry.remove(row.id)))
  assert(removed.every(row => row.authPreserved))
  assert.equal(registry.list().length,21)
  assert.equal(await readFile(token, 'utf8'), 'SENTINEL-CREDENTIALS-DO-NOT-TOUCH')
  await assert.rejects(registry.remove('A'), /permanent main/)
  await assert.rejects(registry.create({ phoneNumber:'not-a-number' }), /Phone number/)
  await assert.rejects(registry.create({ phoneNumber:created[0].phoneNumber }), /already registered/)
  // Force an I/O failure and verify a rejected API call does not mutate RAM.
  const previousFile=registry.file
  const directory=join(root,'not-a-file')
  await mkdir(directory)
  registry.file=directory
  const before=registry.list()
  await assert.rejects(registry.rename(created[0].id, 'MUST NOT PERSIST'))
  assert.deepEqual(registry.list(), before, 'failed save left a phantom account rename in memory')
  await assert.rejects(registry.create({ phoneNumber:'2348000000999' }))
  assert.deepEqual(registry.list(), before, 'failed account creation left a phantom registered account')
  registry.file=previousFile
  await registry.rename(created[0].id, 'Recovered')
  assert.equal(JSON.parse(await readFile(file,'utf8')).accounts.find(row=>row.id===created[0].id).displayName, 'Recovered')
  assert.equal((await readdir(root)).filter(name=>name.endsWith('.tmp')).length, 0)
} finally {
  await rm(root, { recursive:true, force:true })
}
console.log('PASS NIGHT aggressive audit: 2,000 selection fuzz cases, 1,440 FIFO events, message dedup and 90+ account mutations with I/O rollback')
