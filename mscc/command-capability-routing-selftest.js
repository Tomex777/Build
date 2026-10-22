import assert from 'node:assert/strict'
import { dispatchNamespacedCommand } from './command-registry.js'

const runLog = []
const replies = []
const movie = {
  name:'movie',
  capability:'movies',
  async run() { runLog.push('movie'); },
}
const publicRegistry = {
  commands:new Map([['movie', movie]]),
  canonical:[movie],
}
const privateRegistry = { commands:new Map(), canonical:[] }

const directContext = {
  publicPrefix:'.',
  publicCommandsEnabled:true,
  message:{ key:{ remoteJid:'user@s.whatsapp.net' } },
  botProfile:{ id:'unassigned', displayName:'Unassigned' },
  shouldExecutePublicCommand:async () => false,
  reply:async value => { replies.push(String(value)); return value },
}
const directHandled = await dispatchNamespacedCommand({
  privateRegistry,
  publicRegistry,
  rawText:'.movie The Matrix',
  context:directContext,
})
assert.equal(directHandled, true, 'direct-message capability denial should be handled visibly')
assert.deepEqual(runLog, [], 'a mismatched profile must not execute a specialist command')
assert.equal(replies.length, 1, 'direct-message denial should explain why no command ran')
assert.match(replies[0], /Unassigned/)
assert.match(replies[0], /movies/)
assert.match(replies[0], /bot account assigned to that capability/)

// Never send the same diagnostic from every linked account in a group.
replies.length = 0
const groupHandled = await dispatchNamespacedCommand({
  privateRegistry,
  publicRegistry,
  rawText:'.movie The Matrix',
  context:{
    ...directContext,
    message:{ key:{ remoteJid:'test-group@g.us' } },
  },
})
assert.equal(groupHandled, false, 'wrong specialist should stay silent in groups')
assert.deepEqual(replies, [], 'group capability mismatch must not spam the chat')
assert.deepEqual(runLog, [])

// A correctly assigned profile must execute normally with no diagnostic.
const allowedHandled = await dispatchNamespacedCommand({
  privateRegistry,
  publicRegistry,
  rawText:'.movie The Matrix',
  context:{
    ...directContext,
    botProfile:{ id:'mimi', displayName:'MiMi' },
    shouldExecutePublicCommand:async () => true,
  },
})
assert.equal(allowedHandled, true)
assert.deepEqual(runLog, ['movie'])
assert.deepEqual(replies, [])

console.log('PASS direct-message specialist mismatch diagnostics and unchanged capability enforcement')
