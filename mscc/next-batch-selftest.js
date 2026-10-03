import assert from 'node:assert/strict'
import { loadCommands } from './command-registry.js'
import { runAlbumCommand } from './album-flow.js'
import { setAfk, getAfk, addAfkEvent, clearAfk } from './afk-state.js'
import { enforceGroupMessage, groupPolicy, setGroupPolicy } from './group-policy.js'
import { validatePublicUrl } from './read-content.js'
import shipCommand from './commands/fun/ship.js'

const registry = await loadCommands(new URL('./commands/', import.meta.url), {
  capabilityFromDirectory:true,
})

for (const name of [
  'album','afk','rules','setrules','antispam','filter',
  'remindreply','rr','shorten','linkinfo','fact','roast',
  'ship','trailer','chapter','request',
]) {
  assert.ok(registry.commands.has(name), 'Missing Night command: ' + name)
}

const memory = new Map()
const storage = {
  sharedGet:(ns,key) => memory.get(ns + '|' + key) ?? null,
  sharedSet:(ns,key,value) => { memory.set(ns + '|' + key, structuredClone(value)); return value },
  sharedDelete:(ns,key) => memory.delete(ns + '|' + key) ? 1 : 0,
}

setAfk(storage, 'g@g.us', '111', { reason:'eating', displayName:'Kai', at:1000 })
assert.equal(getAfk(storage, 'g@g.us', '111').reason, 'eating')
addAfkEvent(storage, 'g@g.us', '111', {
  senderPhone:'222',
  senderName:'Mia',
  text:'where are you?',
  kind:'mention',
  at:2000,
})
assert.equal(getAfk(storage, 'g@g.us', '111').events.length, 1)
assert.equal(clearAfk(storage, 'g@g.us', '111').events[0].senderPhone, '222')
assert.equal(getAfk(storage, 'g@g.us', '111'), null)

setGroupPolicy(storage, 'filters@g.us', {
  rulesText:'1. Be respectful',
  filters:[{ trigger:'hello night', action:'reply', response:'Hey 👋' }],
})
assert.equal(groupPolicy(storage, 'filters@g.us').rulesText, '1. Be respectful')
let autoReply = ''
const filterResult = await enforceGroupMessage({
  storage,
  msg:{
    key:{ remoteJid:'filters@g.us', participant:'333@s.whatsapp.net', id:'f1', fromMe:false },
    message:{ conversation:'hello night' },
  },
  senderPhone:'333',
  senderIsAdmin:false,
  botIsAdmin:true,
  replyMessage:async value => { autoReply = String(value) },
})
assert.equal(filterResult.reason, 'filter-reply')
assert.equal(autoReply, 'Hey 👋')

setGroupPolicy(storage, 'spam@g.us', { antiSpam:true })
let spamDeletes = 0
for (let i=0;i<3;i+=1) {
  await enforceGroupMessage({
    storage,
    msg:{
      key:{ remoteJid:'spam@g.us', participant:'444@s.whatsapp.net', id:'s' + i, fromMe:false },
      message:{ conversation:'same spam' },
    },
    senderPhone:'444',
    senderIsAdmin:false,
    botIsAdmin:true,
    deleteMessage:async () => { spamDeletes += 1 },
  })
}
assert.equal(spamDeletes, 1)

await assert.rejects(
  () => validatePublicUrl('http://127.0.0.1/private'),
  /Private-network/,
)

const originalFetch = globalThis.fetch
let session = null
let caption = ''
let downloadCount = 0
globalThis.fetch = async input => {
  const url = String(input)
  if (url.includes('/search?')) {
    return {
      ok:true,
      async json() {
        return {
          resultCount:1,
          results:[{
            wrapperType:'collection',
            collectionId:77,
            collectionName:'Test Album',
            artistName:'Test Artist',
            artworkUrl100:'https://img.test/100x100.jpg',
            trackCount:2,
            releaseDate:'2026-01-01T00:00:00Z',
            primaryGenreName:'Test',
          }],
        }
      },
    }
  }
  if (url.includes('/lookup?')) {
    return {
      ok:true,
      async json() {
        return {
          results:[
            {
              wrapperType:'collection',
              collectionId:77,
              collectionName:'Test Album',
              artistName:'Test Artist',
              artworkUrl100:'https://img.test/100x100.jpg',
              trackCount:2,
              releaseDate:'2026-01-01T00:00:00Z',
              primaryGenreName:'Test',
            },
            {
              wrapperType:'track', kind:'song', trackId:1, trackNumber:1,
              trackName:'One', artistName:'Test Artist', collectionName:'Test Album', trackTimeMillis:180000,
            },
            {
              wrapperType:'track', kind:'song', trackId:2, trackNumber:2,
              trackName:'Two', artistName:'Test Artist', collectionName:'Test Album', trackTimeMillis:200000,
            },
          ],
        }
      },
    }
  }
  throw new Error('Unexpected fetch ' + url)
}

const albumCtx = {
  publicPrefix:'.',
  setCommandReplySession:value => { session = value },
  getCommandReplySession:() => session,
  clearCommandReplySession:() => { session = null },
  sendImageUrl:async (_url, text) => { caption = String(text) },
  reply:async value => String(value),
  executeSource:async ({ payload }) => {
    if (payload.action === 'search') {
      return {
        status:'ok',
        source:{ id:'youtube', name:'YouTube' },
        result:{ items:[{ id:'video123456', title:payload.query, artist:'Test Artist' }] },
      }
    }
    if (payload.action === 'download') {
      downloadCount += 1
      return { status:'ok', source:{ id:'youtube' }, result:{ delivered:true } }
    }
    throw new Error('Unexpected source action ' + payload.action)
  },
}

try {
  await runAlbumCommand(albumCtx, { args:['Test','Album'] })
  assert.equal(session?.kind, 'album-selection')
  assert.equal(session?.entries?.length, 2)
  assert.match(caption, /Reply all to download the whole album/i)

  albumCtx.commandReplyInput = 'all'
  await runAlbumCommand(albumCtx, { args:['~selection'] })
  assert.equal(downloadCount, 2)
} finally {
  globalThis.fetch = originalFetch
}

let sentShip = null
await shipCommand.run({
  groupKey:'ship@g.us',
  message:{
    message:{
      extendedTextMessage:{
        text:'.ship @111 @222',
        contextInfo:{ mentionedJid:['111@s.whatsapp.net','222@s.whatsapp.net'] },
      },
    },
  },
  account:{
    sock:{
      groupMetadata:async () => ({
        participants:[
          { id:'111@s.whatsapp.net' },
          { id:'222@s.whatsapp.net' },
          { id:'333@s.whatsapp.net' },
        ],
      }),
      sendMessage:async (_jid,payload) => { sentShip = payload },
    },
  },
  reply:async value => value,
})
assert.deepEqual(sentShip.mentions, ['111@s.whatsapp.net','222@s.whatsapp.net'])

console.log('PASS Night album-all, AFK, rules/filter/anti-spam, safe-link, ship, and new command batch')
