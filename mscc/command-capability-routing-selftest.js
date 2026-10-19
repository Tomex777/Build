import assert from 'node:assert/strict'
import { dispatchNamespacedCommand } from './command-registry.js'

const runLog = []
const replies = []
const movie = {
  name:'movie',
  capability:'movies',
  async run() { runLog.push('movie'); },
}
const apk = {
  name:'apk',
  capability:'android',
  async run() { runLog.push('apk'); },
}
const publicRegistry = {
  commands:new Map([['movie', movie], ['apk', apk], ['android', apk]]),
  canonical:[movie, apk],
}
const privateRegistry = { commands:new Map(), canonical:[] }
const profiles = [
  {
    id:'josiah',
    displayName:'Josiah',
    universal:true,
    basePriority:20,
    capabilities:[],
  },
  {
    id:'mimi',
    displayName:'MiMi',
    universal:false,
    basePriority:0,
    capabilities:[{ capability:'movies', priority:100 }, { capability:'tv', priority:100 }],
  },
  {
    id:'nami',
    displayName:'Nami',
    universal:false,
    basePriority:0,
    capabilities:[{ capability:'anime', priority:100 }, { capability:'manga', priority:100 }],
  },
  {
    id:'unassigned',
    displayName:'Unassigned',
    universal:false,
    basePriority:0,
    capabilities:[],
  },
]
const directContext = {
  publicPrefix:'.',
  publicCommandsEnabled:true,
  message:{ key:{ remoteJid:'user@s.whatsapp.net' } },
  botProfile:{ id:'unassigned', displayName:'Unassigned' },
  botProfiles:() => profiles,
  botAssignments:() => [
    { account_id:'B', profile_id:'josiah' },
    { account_id:'C', profile_id:'nami' },
    { account_id:'D', profile_id:'mimi' },
  ],
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
assert.match(replies[0], /MiMi/, 'movie/TV denial should name MiMi as the specialist')

// Android has no dedicated specialist by default, so the universal Josiah
// profile should be suggested without granting it execution on this account.
replies.length = 0
const androidHandled = await dispatchNamespacedCommand({
  privateRegistry,
  publicRegistry,
  rawText:'.android whatsapp',
  context:directContext,
})
assert.equal(androidHandled, true)
assert.equal(replies.length, 1)
assert.match(replies[0], /android/)
assert.match(replies[0], /Josiah/)
assert.deepEqual(runLog, [], 'a denied Android command must not execute')

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
