import assert from 'node:assert/strict'
import { dispatchCommand } from './command-registry.js'
import { counterpartInstantRows, screenCounterpartInstantRows } from './media-relations.js'
import { commandText } from './utils/whatsapp/messages.js'
import { lyricsInstantRows } from './lyrics-flow.js'
import { createWhatsAppUi } from './utils/whatsapp/ui.js'

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
assert.equal(animeRows[0].title, 'Manga')

const mangaRows = counterpartInstantRows(manga, { fromType:'MANGA', prefix:'.' })
assert.equal(mangaRows[0].id, '.anime ~anilist 1')
assert.equal(mangaRows[0].title, 'Anime')

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
const lyricsCommand = {
  name:'lyrics',
  capability:'music',
  async run(ctx) { seen.push({ command:'lyrics', args:ctx.args }) },
}
const registry = {
  commands:new Map([
    ['manga', mangaCommand],
    ['anime', animeCommand],
    ['lyrics', lyricsCommand],
  ]),
  canonical:[mangaCommand, animeCommand, lyricsCommand],
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

const lyricRows = lyricsInstantRows([{
  title:'Example Song',
  artist:'Example Artist',
  album:'Example Album',
  duration:'3:30',
}], { prefix:'.' })
assert.equal(lyricRows.length, 1)
assert.ok(lyricRows[0].id.startsWith('.lyrics ~track '))
const lyricRaw = commandText(nativeReply(lyricRows[0].id))
assert.equal(lyricRaw, lyricRows[0].id)
assert.equal(await dispatchCommand(registry, lyricRaw, context, { scope:'public', prefix:'.' }), true)
assert.equal(seen.at(-1).command, 'lyrics')
assert.equal(seen.at(-1).args[0], '~track')
assert.ok(seen.at(-1).args[1])


const screenRows = screenCounterpartInstantRows([
  { title:'Example Series', tmdbId:44, type:'tv' },
], { fromType:'movie', prefix:'.' })
assert.equal(screenRows[0].title, 'TV Series')
assert.equal(screenRows[0].id, '.tv ~tmdb 44')

const sentPayloads = []
const sock = {
  async sendMessage(chat, payload) {
    sentPayloads.push({ chat, payload })
    return { key:{ id:'sent-' + sentPayloads.length } }
  },
}
const ui = createWhatsAppUi({
  sock,
  chat:'123@s.whatsapp.net',
  prefix:'.',
})

await ui.instantReplies({
  text:'Example Anime',
  actions:[
    { title:'Manga', id:'.manga ~anilist 22' },
    { title:'Add to Library', id:'.anime ~library-add 1' },
  ],
})

const instantButtons = sentPayloads.at(-1).payload.interactiveButtons
assert.equal(instantButtons.length, 2)
assert.equal(instantButtons[0].name, 'quick_reply')
assert.deepEqual(JSON.parse(instantButtons[0].buttonParamsJson), {
  display_text:'Manga',
  id:'.manga ~anilist 22',
})
assert.deepEqual(JSON.parse(instantButtons[1].buttonParamsJson), {
  display_text:'Add to Library',
  id:'.anime ~library-add 1',
})

await ui.interactive({
  text:'Example Movie',
  actions:[{ title:'TV Series', id:'.tv ~tmdb 44' }],
  selectors:[{
    text:'Download options',
    rows:[{ title:'720p • Document', id:'.movie ~download test' }],
  }],
})
const mixedButtons = sentPayloads.at(-1).payload.interactiveButtons
assert.equal(mixedButtons[0].name, 'quick_reply')
assert.equal(mixedButtons[1].name, 'single_select')

console.log('PASS instant cross-media replies and WhatsApp quick_reply payloads')
