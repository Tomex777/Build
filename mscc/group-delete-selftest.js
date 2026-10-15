import assert from 'node:assert/strict'
import { deleteQuotedGroupMessage } from './group-delete.js'
import command from './commands/admin/delete.js'

const chat = '1203630000000@g.us'
const commandKey = { remoteJid:chat, id:'command', participant:'admin@s.whatsapp.net', fromMe:false }
const quote = { stanzaId:'quoted', participant:'sender@lid' }

let sent = []
let suppressed = []
const base = {
  chat, commandKey, quote, accountId:'bot-1',
  sendDelete:async key => { sent.push(key) },
  suppressDelete:(jid, id) => { suppressed.push([jid, id]) },
}
let result = await deleteQuotedGroupMessage(base)
assert.deepEqual(result, { deleted:1, commandDeleted:true })
assert.deepEqual(sent, [
  { remoteJid:chat, id:'quoted', fromMe:false, participant:'sender@lid' },
  commandKey,
])
assert.deepEqual(suppressed, [[chat,'quoted'],[chat,'command']])

sent = []
result = await deleteQuotedGroupMessage({
  ...base,
  quote:{ stanzaId:'bot-reply' },
  cached:{ accountId:'bot-1', message:{ key:{ id:'bot-reply', fromMe:true } } },
})
assert.equal(result.deleted, 1)
assert.equal(sent[0].fromMe, true)
assert.equal(sent[0].id, 'bot-reply')
assert.equal(sent.length, 2)

sent = []
result = await deleteQuotedGroupMessage({ ...base, isOwnParticipant:async jid => jid === 'sender@lid' })
assert.equal(sent[0].fromMe, true)

sent = []
result = await deleteQuotedGroupMessage({ ...base, quote:{} })
assert.equal(result.deleted, 0)
assert.equal(sent.length, 0)
assert.match(result.error, /Reply to a message/)

result = await deleteQuotedGroupMessage({ ...base, quote:{ stanzaId:'no-participant' } })
assert.equal(result.deleted, 0)
assert.equal(sent.length, 0)

result = await deleteQuotedGroupMessage({ ...base, quote:{ ...quote, remoteJid:'another@g.us' } })
assert.equal(result.deleted, 0)
assert.equal(sent.length, 0)

sent = []
result = await deleteQuotedGroupMessage({
  ...base,
  sendDelete:async key => { sent.push(key); throw new Error('network') },
})
assert.equal(result.deleted, 0)
assert.equal(sent.length, 1) // Never delete the command when the target failed.

sent = []
result = await deleteQuotedGroupMessage({
  ...base,
  sendDelete:async key => { sent.push(key); if (key.id === 'command') throw new Error('network') },
})
assert.equal(result.deleted, 1)
assert.equal(result.commandDeleted, false)
assert.equal(sent.length, 2)

// Prove the public handler routes bare .delete into Nexia's reply mode.
let replyCalls = 0
let countCalls = 0
let responses = []
const ctx = {
  groupKey:chat, args:[], publicPrefix:'.',
  reply:async text => { responses.push(text) },
  deleteRepliedMessage:async () => { replyCalls++; return { deleted:1 } },
  deleteRecentMessages:async n => { countCalls++; return { deleted:n, botAdmin:true } },
}
await command.run(ctx)
assert.equal(replyCalls, 1)
assert.equal(countCalls, 0)
assert.deepEqual(responses, [])
await command.run({ ...ctx, args:['3'] })
assert.equal(countCalls, 1)
await command.run({ ...ctx, args:[], deleteRepliedMessage:async () => ({ deleted:0, error:'Reply to a message.' }) })
assert.deepEqual(responses, ['Reply to a message.'])

console.log('PASS Nexia-style .delete reply and count behavior')
