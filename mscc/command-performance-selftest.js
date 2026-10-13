import assert from 'node:assert/strict'
import { createCommandTiming, commandNameForTiming } from './command-performance.js'
import { resolveIdentityWithDeadline } from './identity-resolution.js'
import { groupMessageNeedsModeration, DEFAULT_GROUP_POLICY } from './group-policy.js'
import { chooseGroupExecutor } from './bot-routing.js'

// Slow command logging never contains chat content, number, LID or arguments.
assert.equal(commandNameForTiming('.anime Naruto'), 'anime')
assert.equal(commandNameForTiming('1'), '')
assert.equal(commandNameForTiming('.'), '')
assert.equal(commandNameForTiming('!menu', '!'), 'menu')
let now = 0
const messages = []
const timer = createCommandTiming({
  accountId:'account-5', text:'.anime Very Private Title',
  now:()=>now, thresholdMs:1000, log:message=>messages.push(message),
})
now = 100
timer.mark('identity')
now = 105
timer.mark('storage')
now = 205
timer.mark('moderation')
now = 250
await timer.send(async () => {
  now = 950
  return true
})
now = 1200
timer.mark('command')
timer.finish()
assert.equal(messages.length, 1)
assert.match(messages[0], /account=account-5/)
assert.match(messages[0], /command=\.anime/)
assert.match(messages[0], /identity=100ms/)
assert.match(messages[0], /storage=5ms/)
assert.match(messages[0], /moderation=100ms/)
assert.match(messages[0], /command=995ms/)
assert.match(messages[0], /send=700ms/)
assert(!messages[0].includes('Private'))
assert(!messages[0].includes('Title'))

// Ordinary group commands no longer need group admin metadata round trips.
const regular = { key:{ remoteJid:'group@g.us' }, message:{ conversation:'.menu' } }
const mentioned = { key:{ remoteJid:'group@g.us' }, message:{
  extendedTextMessage:{
    text:'hello',
    contextInfo:{ mentionedJid:['person@s.whatsapp.net'] },
  },
}}
assert.equal(groupMessageNeedsModeration(DEFAULT_GROUP_POLICY, regular), false)
assert.equal(groupMessageNeedsModeration(DEFAULT_GROUP_POLICY, mentioned), true)
assert.equal(groupMessageNeedsModeration({ ...DEFAULT_GROUP_POLICY, antiSpam:true }, regular), true)
assert.equal(groupMessageNeedsModeration({ ...DEFAULT_GROUP_POLICY, filters:[{ trigger:'bad', action:'warn' }] }, regular), true)

// Ineligible bot profiles must not trigger WhatsApp groupMetadata requests.
let checks = []
const accounts = ['A','Josia','Nami'].map(id=>({id, enabled:true, connected:true, sock:{}}))
const selected = await chooseGroupExecutor({
  groupJid:'group@g.us', capability:'general', accounts,
  scoreFor:id => id === 'Josia' ? 10 : null,
  isMember:async account=>{ checks.push(account.id); return true },
  getSticky:()=>'', setSticky:()=>{},
})
assert.equal(selected, 'Josia')
assert.deepEqual(checks, ['Josia'])

// A stalled identity lookup cannot delay .ping indefinitely and unresolved
// LIDs must never be treated as phone-number owner authentication.
const lid='lid123@lid'
const late = []
const before = performance.now()
const fast = await resolveIdentityWithDeadline({
  jid:lid, timeoutMs:200,
  lookup:()=>new Promise(()=>{}),
  onResolved:value=>late.push(value),
})
assert.equal(fast, lid)
assert(performance.now() - before < 1500)
assert.deepEqual(late, [])
assert.equal(await resolveIdentityWithDeadline({
  jid:lid, timeoutMs:200,
  lookup:async()=>({ phoneNumber:'234555@s.whatsapp.net' }),
  onResolved:value=>late.push(value),
}), '234555@s.whatsapp.net')
assert.deepEqual(late, ['234555@s.whatsapp.net'])
assert.equal(await resolveIdentityWithDeadline({ jid:lid, lookup:async()=>{ throw Error('timeout') } }), lid)

console.log('PASS command latency telemetry, bypass of unnecessary metadata, and bounded LID resolution')
