import assert from 'node:assert/strict'
import {
  addWarning,
  clearWarnings,
  enforceGroupMessage,
  groupPolicy,
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

setUserMentionMute(storage, group, '111', '999', true)
assert.equal(userMentionMuted(storage, group, '111', '999'), true)

let deleted = 0
const resent = []
const mentionMsg = {
  key:{ remoteJid:group, participant:'999@s.whatsapp.net', id:'m1', fromMe:false },
  message:{
    extendedTextMessage:{
      text:'hey @111 and @222 check this',
      contextInfo:{ mentionedJid:['111@s.whatsapp.net','222@s.whatsapp.net'] },
    },
  },
}
const muted = await enforceGroupMessage({
  storage,
  msg:mentionMsg,
  senderPhone:'999',
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
assert(!resent[0].text.includes('@111'))
assert(resent[0].text.includes('@222'))
assert.deepEqual(resent[0].mentions, ['222@s.whatsapp.net'])
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

const w1 = addWarning(storage, group, '333', 'spam', '111')
const w2 = addWarning(storage, group, '333', 'again', '111')
assert.equal(w1.count, 1)
assert.equal(w2.count, 2)
assert.equal(warningState(storage, group, '333').history.length, 2)
assert.equal(clearWarnings(storage, group, '333'), 1)
assert.equal(warningState(storage, group, '333').count, 0)

assert(_test.URL_RE.test('example.com'))
assert(_test.GROUP_MENTION_RE.test('@everyone hello'))

console.log('PASS group moderation, warnings, anti-link, and personal mention mute reconstruction')
