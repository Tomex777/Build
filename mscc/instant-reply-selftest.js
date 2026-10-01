import assert from 'node:assert/strict'
import { dispatchCommand } from './command-registry.js'
import { counterpartInstantRows } from './media-relations.js'
import { commandText } from './utils/whatsapp/messages.js'

const anime = {
  id:1,
  type:'ANIME',
  title:'Example Anime',
  relations:[
    {
      relationType:'SOURCE',
      node:{ id:22, type:'MANGA', format:'MANGA', title:'Example Manga' },
    },
  ],
}
const manga = {
  id:22,
  type:'MANGA',
  title:'Example Manga',
  relations:[
    {
      relationType:'ADAPTATION',
      node:{ id:1, type:'ANIME', format:'TV', title:'Example Anime' },
    },
  ],
}

const animeRows = counterpartInstantRows(anime, { fromType:'ANIME', prefix:'.' })
assert.equal(animeRows[0].id, '.manga ~anilist 22')
assert(animeRows[0].title.includes('Example Manga'))

const mangaRows = counterpartInstantRows(manga, { fromType:'MANGA', prefix:'.' })
assert.equal(mangaRows[0].id, '.anime ~anilist 1')
assert(mangaRows[0].title.includes('Example Anime'))

function nativeReply(id) {
  return {
    interactiveResponseMessage:{
      nativeFlowResponseMessage:{
        paramsJson:JSON.stringify({ id }),
      },
    },
  }
}

const seen = []
const mangaCommand = {
  name:'manga',
  capability:'manga',
  async run(ctx) { seen.push({ command:'manga', args:ctx.args }) },
}
const animeCommand = {
  name:'anime',
  capability:'anime',
  async run(ctx) { seen.push({ command:'anime', args:ctx.args }) },
}
const registry = {
  commands:new Map([
    ['manga', mangaCommand],
    ['anime', animeCommand],
  ]),
  canonical:[mangaCommand, animeCommand],
}
const context = {
  publicCommandsEnabled:true,
  reply:async () => {},
}

const mangaRaw = commandText(nativeReply('.manga ~anilist 22'))
assert.equal(mangaRaw, '.manga ~anilist 22')
assert.equal(await dispatchCommand(registry, mangaRaw, context, { scope:'public', prefix:'.' }), true)
assert.deepEqual(seen.at(-1), { command:'manga', args:['~anilist','22'] })

const animeRaw = commandText(nativeReply('.anime ~anilist 1'))
assert.equal(animeRaw, '.anime ~anilist 1')
assert.equal(await dispatchCommand(registry, animeRaw, context, { scope:'public', prefix:'.' }), true)
assert.deepEqual(seen.at(-1), { command:'anime', args:['~anilist','1'] })

console.log('PASS anime/manga instant cross-command replies')
