import { runMangaCommand } from './manga-flow.js'

const calls = []
const lists = []
const instants = []
const replies = []
let saved = null
let session = null
let commandReplyInput = ''

const ctx = {
  publicPrefix:'.',
  botProfile:{ id:'nami', displayName:'Nami' },
  listSources:() => [{ id:'alpha', name:'Alpha Manga' }],
  getDeliveryDefault:() => saved,
  setCommandReplySession:value => { session = value },
  getCommandReplySession:() => session,
  clearCommandReplySession:() => { session = null },
  get commandReplyInput() { return commandReplyInput },
  reply:async value => { replies.push(String(value)); return value },
  replyList:async value => { lists.push(value); return value },
  replyInstant:async value => { instants.push(value); return value },
  resolveAniListTitles:async (query, type) => ({
    query,
    aliases:[query, 'Sousou no Frieren', 'Frieren: Beyond Journey’s End'],
    matches:[{ id:30013, title:'Sousou no Frieren', type }],
    source:'anilist',
  }),
  resolveAniListMedia:async (id, type) => {
    if (String(type) === 'MANGA') {
      return {
        id:Number(id),
        type:'MANGA',
        title:'Sousou no Frieren',
        format:'MANGA',
        relations:[
          {
            relationType:'ADAPTATION',
            node:{
              id:154587,
              type:'ANIME',
              format:'TV',
              title:'Frieren: Beyond Journey’s End',
            },
          },
        ],
      }
    }
    return {
      id:Number(id),
      type:'ANIME',
      title:'Frieren: Beyond Journey’s End',
      format:'TV',
      relations:[],
    }
  },
  executeSource:async ({ explicitSource, payload }) => {
    calls.push({ explicitSource, payload })
    if (payload.action === 'search' || payload.action === 'browse') {
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha Manga' },
        result:{ items:[{ id:'frieren', title:'Frieren: Beyond Journey’s End' }] },
        fallback:false,
      }
    }
    if (payload.action === 'chapters') {
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha Manga' },
        result:{
          title:'Frieren: Beyond Journey’s End',
          chapters:[
            { id:'c1', number:1, title:'The Journey’s End' },
            { id:'c2', number:2, title:'The Priest’s Lie' },
            { id:'c3', number:3, title:'Blue-Moon Weed' },
            { id:'c4', number:4, title:'The Mage’s Secret' },
          ],
        },
      }
    }
    if (payload.action === 'options') {
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha Manga' },
        result:{ qualities:['source'], deliveries:['document','cbz'] },
      }
    }
    if (payload.action === 'download' || payload.action === 'downloadRange') {
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha Manga' },
        result:{ text:`OK:${payload.action}:${payload.quality}:${payload.delivery}` },
      }
    }
    throw new Error('Unexpected manga action ' + payload.action)
  },
}

await runMangaCommand(ctx, { args:['Frieren'] })
if (!session || session.kind !== 'number-selection' || session.command !== 'manga') {
  throw new Error('Manga flow did not create a numeric reply session')
}
if (!instants.at(-1)?.actions?.some(row => row.id === '.anime ~anilist 154587' && row.title === 'Anime')) {
  throw new Error('Manga flow is missing instant anime adaptation reply')
}
if (!instants.at(-1)?.text?.includes('1-10') || !instants.at(-1)?.text?.includes('1,3,4,7')) {
  throw new Error('Manga flow did not explain the same typed-number syntax as anime')
}
if (instants.at(-1)?.actions?.some(row => /Download a range/i.test(row.title))) {
  throw new Error('Manga still exposes the old range picker instead of typed numbers')
}

lists.length = 0
commandReplyInput = '1-2'
await runMangaCommand(ctx, { args:['~numbers'] })
if (session?.kind !== 'media-download-options') {
  throw new Error('Manga numeric range did not enter download options')
}
const optionList = lists.at(-1)
const rangeOption = optionList?.sections?.flatMap(section => section.rows)
  .find(row => row.id.includes('~selection-download source document'))
if (!rangeOption) throw new Error('Manga numeric range download option missing')

replies.length = 0
await runMangaCommand(ctx, { args:rangeOption.id.split(/\s+/).slice(1) })
if (!replies.some(value => value.includes('OK:downloadRange:source:document'))) {
  throw new Error('Contiguous manga numeric range did not use downloadRange')
}

await runMangaCommand(ctx, { args:['Frieren'] })
saved = { quality:'source', delivery:'cbz' }
commandReplyInput = '1,3,4'
calls.length = 0
replies.length = 0
await runMangaCommand(ctx, { args:['~numbers'] })
const individual = calls.filter(call => call.payload.action === 'download')
if (individual.length !== 3) {
  throw new Error('Non-contiguous manga number selection did not download each selected chapter')
}
if (!individual.every(call => call.payload.quality === 'source' && call.payload.delivery === 'cbz')) {
  throw new Error('Saved manga delivery default was not used for typed chapters')
}
saved = null

lists.length = 0
instants.length = 0
replies.length = 0
await runMangaCommand(ctx, { args:['~anilist','30013'] })
if (session?.kind !== 'number-selection') {
  throw new Error('Anime-to-manga instant handoff did not enter typed chapter selection')
}
const handoffView = instants.at(-1) || lists.at(-1)
if (handoffView && !handoffView.text?.includes('Reply with the chapter number')) {
  throw new Error('Anime-to-manga handoff did not show the typed chapter prompt')
}
if (!handoffView && !replies.some(value => value.includes('Reply with the chapter number'))) {
  throw new Error('Anime-to-manga handoff did not show the typed chapter prompt')
}

console.log('PASS manga mirrors anime typed-number/range/instant-anime flow')
