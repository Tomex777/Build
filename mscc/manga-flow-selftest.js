import { runMangaCommand } from './manga-flow.js'

const calls = []
const lists = []
const replies = []
let saved = null

const ctx = {
  publicPrefix:'.',
  botProfile:{ id:'nami', displayName:'Nami' },
  listSources:() => [{ id:'alpha', name:'Alpha Manga' }],
  getDeliveryDefault:() => saved,
  reply:async value => { replies.push(String(value)); return value },
  replyList:async value => { lists.push(value); return value },
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
    if (payload.action === 'search') {
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha Manga' },
        result:{ items:[{ id:'frieren', title:'Frieren: Beyond Journey’s End' }] },
        fallback:false,
      }
    }
    if (payload.action === 'browse') {
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
if (!lists.length) throw new Error('Manga search did not open a chapter list')
const chapterList = lists.at(-1)
if (!chapterList.rows?.some(row => row.title === '📦 Download a range')) {
  throw new Error('Manga chapter list is missing range flow')
}
if (!chapterList.rows?.some(row => row.id === '.anime ~anilist 154587')) {
  throw new Error('Manga chapter list is missing instant anime adaptation reply')
}
const chapterRow = chapterList.rows.find(row => row.title === 'Ch 1')
if (!chapterRow) throw new Error('Chapter 1 row missing')

lists.length = 0
await runMangaCommand(ctx, { args:chapterRow.id.split(/\s+/).slice(1) })
const optionList = lists.at(-1)
if (!optionList?.sections || optionList.sections.length !== 2) {
  throw new Error('Chapter picker did not expose manga delivery options')
}
if (!optionList.sections.flatMap(section => section.rows).some(row => /Source quality.*document/i.test(row.title))) {
  throw new Error('Manga source-quality/document option missing')
}

saved = { quality:'source', delivery:'cbz' }
replies.length = 0
await runMangaCommand(ctx, { args:chapterRow.id.split(/\s+/).slice(1) })
if (!replies.some(value => value.includes('OK:download:source:cbz'))) {
  throw new Error('Saved manga delivery default was not applied')
}

saved = null
lists.length = 0
const rangeRow = chapterList.rows.find(row => row.title === '📦 Download a range')
await runMangaCommand(ctx, { args:rangeRow.id.split(/\s+/).slice(1) })
const startList = lists.at(-1)
const startRow = startList.rows.find(row => row.title === 'Ch 1')
if (!startRow) throw new Error('Manga range start picker missing')

lists.length = 0
await runMangaCommand(ctx, { args:startRow.id.split(/\s+/).slice(1) })
const endList = lists.at(-1)
const endRow = endList.rows.find(row => row.title === 'Ch 3')
if (!endRow) throw new Error('Manga range end picker missing')

saved = { quality:'source', delivery:'document' }
replies.length = 0
await runMangaCommand(ctx, { args:endRow.id.split(/\s+/).slice(1) })
if (!replies.some(value => value.includes('OK:downloadRange:source:document'))) {
  throw new Error('Manga range download did not use saved defaults')
}

lists.length = 0
saved = null
await runMangaCommand(ctx, { args:['~anilist','30013'] })
if (!lists.at(-1)?.rows?.some(row => row.title === 'Ch 1')) {
  throw new Error('Anime-to-manga instant handoff did not enter full chapter flow')
}

console.log('PASS full manga chapter/range/instant-anime flow')
