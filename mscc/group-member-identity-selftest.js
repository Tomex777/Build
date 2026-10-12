import assert from 'node:assert/strict'
import {
  findGroupParticipant,
  groupMemberLabel,
  groupTargetJid,
  memberIdentityKey,
  participantIds,
  updateSucceeded,
} from './group-member-identity.js'
import promote from './commands/admin/promote.js'
import demote from './commands/admin/demote.js'
import kick from './commands/admin/kick.js'
import warn from './commands/admin/warn.js'
import muteuser from './commands/group/muteuser.js'
import unmuteuser from './commands/group/unmuteuser.js'

const PN = '2348012345678@s.whatsapp.net'
const LID = '1122334455@lid'
const NAME = '𝙏𝞗𝞛𝞢𝞦⁩'
const member = { id:LID, phoneNumber:PN, notify:NAME, admin:null }
const participants = [member, { id:'220011@s.whatsapp.net', admin:'admin' }]

assert.equal(groupTargetJid('@' + NAME, { mentionedJid:[LID] }), LID)
assert.equal(groupTargetJid('', { participant:LID, stanzaId:'some-message' }), LID)
assert.equal(groupTargetJid('+234 801 234 5678'), PN)
assert.equal(groupTargetJid('@unknown', { mentionedJid:[] }), '')

const fromLid = await findGroupParticipant(participants, LID)
assert.equal(fromLid.participant, member)
assert.equal(fromLid.jid, LID)
assert.equal((await findGroupParticipant(participants, PN)).jid, LID)
assert.equal(participantIds(member).length, 2)
assert.equal(groupMemberLabel({ participant:member, jid:LID }).mentionText, '@' + NAME)

// An identical numeric component in two different namespaces does not prove identity.
assert.equal(await findGroupParticipant([{ id:'123456789@lid' }], '123456789@s.whatsapp.net'), null)
assert.equal(
  (await findGroupParticipant([{ id:'56565656@lid' }], '2348012345678@s.whatsapp.net',
    async jid => jid === '56565656@lid' ? PN : jid))?.jid,
  '56565656@lid',
)

assert.equal(memberIdentityKey(PN, LID), '2348012345678')
assert.equal(memberIdentityKey(LID, LID), 'jid:' + LID)
assert.equal(groupMemberLabel({ participant:{ notify:'WhatsApp user' }, jid:LID }).mentionText, '@1122334455')
assert.equal(groupMemberLabel({ participant:{}, historyNames:[NAME], jid:LID }).mentionText, '@' + NAME)
assert.equal(updateSucceeded([{ status:'200' }]), true)
assert.equal(updateSucceeded([{ status:'403' }]), false)
assert.equal(updateSucceeded([]), null)

const mentioned = []
const target = { jid:LID, mentionText:'@' + NAME }
const ctx = {
  groupKey:'111@g.us',
  args:['@' + NAME],
  groupParticipantAction:async () => target,
  reply:async text => { throw new Error('Expected actual mention, got plain text: ' + text) },
  replyWithMentions:async (text, jids) => mentioned.push({ text, jids }),
  groupWarn:async () => ({ target, state:{ count:1 } }),
  groupWarnClear:async () => ({ target, cleared:1 }),
  groupWarningState:async () => ({ target, state:{ count:1 } }),
  setMentionMute:async () => target,
}
for (const command of [promote,demote,kick,warn,muteuser,unmuteuser]) {
  await command.run(ctx)
  const { text, jids } = mentioned.pop()
  assert.ok(text.includes('@' + NAME), command.name + ' must display actual name')
  assert.ok(!text.includes('WhatsApp user'), command.name + ' must not use generic placeholder')
  assert.deepEqual(jids, [LID], command.name + ' must include tappable mention JID')
}
console.log('PASS MSCC group identity resolution, LID safety and command mentions')
