import assert from 'node:assert/strict'
import {
  addWarning,
  clearWarnings,
  enforceGroupMessage,
  groupPolicy,
  reconcileVerifiedWarnings,
  setGroupPolicy,
  setUserMentionMute,
  userMentionMuted,
  warningState,
  _test,
} from './group-policy.js'

const map = new Map()
const storage = {
  sharedGet:(ns,key) => map.get(ns + '|' + key) ?? null,
  sharedSet:(ns,key,value) => { map.set(ns + '|' + key, structuredClone(value)); return value },
  sharedDelete:(ns,key) => map.delete(ns + '|' + key) ? 1 : 0,
}

const group = '123@g.us'
assert.equal(groupPolicy(storage, group).antiLink, false)
setGroupPolicy(storage, group, { antiLink:true })
assert.equal(groupPolicy(storage, group).antiLink, true)

setUserMentionMute(storage, group, '2348012345111', '2348012345999', true)
assert.equal(userMentionMuted(storage, group, '2348012345111', '2348012345999'), true)

let deleted = 0
const resent = []
const mentionMsg = {
  key:{ remoteJid:group, participant:'2348012345999@s.whatsapp.net', id:'m1', fromMe:false },
  message:{
    extendedTextMessage:{
      text:'hey @2348012345111 and @2348012345222 check this',
      contextInfo:{ mentionedJid:['2348012345111@s.whatsapp.net','2348012345222@s.whatsapp.net'] },
    },
  },
}
const muted = await enforceGroupMessage({
  storage,
  msg:mentionMsg,
  senderPhone:'2348012345999',
  senderIsAdmin:false,
  botIsAdmin:true,
  resolvePhoneJid:async jid => jid,
  deleteMessage:async () => { deleted += 1 },
  resendQuoted:async (text, mentions, quoted) => resent.push({ text, mentions, quoted }),
})
assert.equal(muted.handled, true)
assert.equal(muted.reason, 'user-mention-mute')
assert.equal(deleted, 1)
assert.equal(resent.length, 1)
assert(!resent[0].text.includes('@2348012345111'))
assert(resent[0].text.includes('@2348012345222'))
assert.deepEqual(resent[0].mentions, ['2348012345222@s.whatsapp.net'])
assert.equal(resent[0].quoted, mentionMsg)

const linkMsg = {
  key:{ remoteJid:group, participant:'333@s.whatsapp.net', id:'m2', fromMe:false },
  message:{ conversation:'visit https://example.com now' },
}
await enforceGroupMessage({
  storage,
  msg:linkMsg,
  senderPhone:'333',
  senderIsAdmin:false,
  botIsAdmin:true,
  deleteMessage:async () => { deleted += 1 },
})
assert.equal(deleted, 2)

await enforceGroupMessage({
  storage,
  msg:{ ...linkMsg, key:{ ...linkMsg.key, id:'m3' } },
  senderPhone:'333',
  senderIsAdmin:true,
  botIsAdmin:true,
  deleteMessage:async () => { deleted += 1 },
})
assert.equal(deleted, 2)

const w1 = addWarning(storage, group, '333', 'spam', '2348012345111')
const w2 = addWarning(storage, group, '333', 'again', '2348012345111')
assert.equal(w1.count, 1)
assert.equal(w2.count, 2)
assert.equal(warningState(storage, group, '333').history.length, 2)
assert.equal(clearWarnings(storage, group, '333'), 1)
assert.equal(warningState(storage, group, '333').count, 0)

const opaquePolicy = await enforceGroupMessage({
  storage, msg:{...linkMsg,key:{...linkMsg.key,participant:'112233445566@lid'}},
  senderIdentity:'jid:112233445566@lid', senderPhone:'',
  botIsAdmin:true, senderIsAdmin:false,
  deleteMessage:async()=>{deleted++},
})
assert.equal(opaquePolicy.handled,true)
addWarning(storage,group,'jid:112233445566@lid','before','staff')
addWarning(storage,group,'2348012345678','after','staff')
reconcileVerifiedWarnings(storage,group,'2348012345678',
  ['2348012345678','jid:112233445566@lid'])
assert.equal(warningState(storage,group,'2348012345678').count,2)
assert(_test.URL_RE.test('example.com'))
assert(_test.GROUP_MENTION_RE.test('@everyone hello'))

// Three repeated messages 17s apart (within the promised 20s window) are spam.
assert.equal(_test.spamViolation('test@g.us', 'repeat', 'same', 100_000), '')
assert.equal(_test.spamViolation('test@g.us', 'repeat', 'same', 109_000), '')
assert.equal(_test.spamViolation('test@g.us', 'repeat', 'same', 117_000), 'repeated-message-spam')
assert.equal(_test.spamViolation('test@g.us', 'fresh', 'same', 100_000), '')
assert.equal(_test.spamViolation('test@g.us', 'fresh', 'same', 121_000), '')
// Thousands of one-time senders must not leak moderation history indefinitely.
for (let i = 0; i < 4500; i++) _test.spamViolation('large@g.us', 'member-' + i, 'hi', 300_000 + i)
assert(_test.spamWindowSize() <= 4096, 'anti-spam history grew without bounds')

console.log('PASS group moderation, bounded anti-spam history, warnings and mention mute')
