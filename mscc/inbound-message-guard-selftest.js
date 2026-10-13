import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createInboundMessageGuard, matchesDisplayedChoice } from './inbound-message-guard.js'

const entries = [
  { id:'18kami', title:'18Kami' },
  { id:'comix', title:'Comix' },
  { id:'mangadex', title:'MangaDex' },
  { id:'weebcentral', title:'WeebCentral' },
]

assert.equal(matchesDisplayedChoice('1', entries), true)
assert.equal(matchesDisplayedChoice('4', entries), true)
assert.equal(matchesDisplayedChoice('3', entries), true)
assert.equal(matchesDisplayedChoice('MangaDex', entries), true)
assert.equal(matchesDisplayedChoice('  mangadex ', entries), true)
assert.equal(matchesDisplayedChoice('weebcentral', entries), true)
assert.equal(matchesDisplayedChoice('0', entries), false)
assert.equal(matchesDisplayedChoice('5', entries), false)
assert.equal(matchesDisplayedChoice('.manga', entries), false)
assert.equal(matchesDisplayedChoice('I could not match that choice. Reply with one of the displayed numbers.', entries), false)
assert.equal(matchesDisplayedChoice('Manga source', entries), false)
assert.equal(matchesDisplayedChoice('Some unrelated conversation', entries), false)
assert.equal(matchesDisplayedChoice('', entries), false)
assert.equal(matchesDisplayedChoice('A'.repeat(130), entries), false)

const accept = createInboundMessageGuard({ maxEntries:3, ttlMs:2000 })
const msg = (id, text, fromMe=false, chat='group@g.us') => ({
  key:{ id, remoteJid:chat, fromMe },
  message:{ conversation:text },
})
assert.equal(accept('A', msg('user-1','1'), 10000), true)
assert.equal(accept('A', msg('user-1','1'), 10000), false, 'duplicated notify must not execute twice')
assert.equal(accept('A', msg('user-1','1',true), 10001), false, 'self-authored commands must not dispatch')
assert.equal(accept('A', msg('user-2','I could not match that choice. Reply with one of the displayed numbers.',true), 10002), false)
assert.equal(accept('account-3', msg('user-1','1'), 10003), true, 'account identity must isolate dedupe')
assert.equal(accept('A', msg('user-1','1',false,'different@g.us'), 10004), true, 'chat identity must isolate dedupe')
assert.equal(accept('A', msg('user-1','1'), 10005), false)
assert.equal(accept('A', msg('user-1','1'), 12010), true, 'TTL should release genuinely new deliveries')

const guard = createInboundMessageGuard()
const original = msg('incoming-command','.manga')
assert.equal(guard('A', original), true)
let sentWarnings = 0
// Simulate the original incident: 500 bot-authored feedback messages
// arrive while a selection is pending. None may invoke manga ~choice.
for (let i=0; i<500; i++) {
  const botReply = msg('bot-error-' + i,
    'I could not match that choice. Reply with one of the displayed numbers.', true)
  if (guard('A', botReply) && matchesDisplayedChoice(botReply.message.conversation, entries)) sentWarnings++
}
assert.equal(sentWarnings, 0, 'self-reply flood must never trigger a new invalid-choice warning')
assert.equal(guard('A', original), false, 'same user command cannot process twice')

const index = await readFile(new URL('./index.js', import.meta.url), 'utf8')
const inboundGate = index.indexOf('if (msg.key.fromMe === true)')
const dedupeGate = index.search(/if \(!acceptInboundCommand\(account\.id, msg\)\) (?:return|continue)/)
const dispatcher = index.indexOf('const commandHandled = await dispatchNamespacedCommand(', inboundGate)
assert(inboundGate >= 0 && dedupeGate > inboundGate && dispatcher > dedupeGate,
  'Main Baileys upsert handler must reject self/duplicate before dispatch')
assert(index.includes('matchesDisplayedChoice(text, pendingReply.entries)'),
  'Pending choice routing must accept only displayed options')

console.log('PASS MSCC self-message loop guard: 500 bot replies suppressed, duplicate upserts ignored, and menu choices scoped')
