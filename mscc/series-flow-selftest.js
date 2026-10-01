import { runSeriesCommand } from './series-flow.js'

const calls = []
const lists = []
const replies = []
let saved = null

const ctx = {
  publicPrefix:'.',
  listSources:() => [{ id:'alpha', name:'Alpha Anime' }],
  getSourceDefault:() => '',
  getDeliveryDefault:() => saved,
  reply:async value => replies.push(String(value)),
  replyList:async value => { lists.push(value); return value },
  executeSource:async ({ explicitSource, payload }) => {
    calls.push({ explicitSource, payload })
    if (payload.action === 'search') {
      return { status:'ok', source:{ id:'alpha', name:'Alpha Anime' }, result:{ items:[{ id:'bleach', title:'Bleach' }] }, fallback:false }
    }
    if (payload.action === 'browse') {
      return { status:'ok', source:{ id:'alpha', name:'Alpha Anime' }, result:{ items:[{ id:'bleach', title:'Bleach' }] }, fallback:false }
    }
    if (payload.action === 'episodes') {
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha Anime' },
        result:{
          title:'Bleach',
          episodes:[
            { id:'e1', number:1, title:'Episode One' },
            { id:'e2', number:2, title:'Episode Two' },
            { id:'e3', number:3, title:'Episode Three' },
          ],
        },
      }
    }
    if (payload.action === 'options') {
      return { status:'ok', source:{ id:'alpha', name:'Alpha Anime' }, result:{ qualities:['1080','720'], deliveries:['document','video'] } }
    }
    if (payload.action === 'download' || payload.action === 'downloadRange') {
      return { status:'ok', source:{ id:'alpha', name:'Alpha Anime' }, result:{ text:`OK:${payload.action}:${payload.quality}:${payload.delivery}` } }
    }
    throw new Error('Unexpected action ' + payload.action)
  },
}

await runSeriesCommand(ctx, { capability:'anime', commandName:'anime', args:['Bleach'] })
if (!lists.length) throw new Error('Anime search did not open an episode native-flow list')
const episodeList = lists.at(-1)
if (!episodeList.rows?.some(row => row.title === '📦 Download a range')) throw new Error('Episode list is missing range flow')
const episodeRow = episodeList.rows.find(row => row.title === 'Ep 1')
if (!episodeRow) throw new Error('Episode 1 row missing')

lists.length = 0
await runSeriesCommand(ctx, { capability:'anime', commandName:'anime', args:episodeRow.id.split(/\s+/).slice(1) })
const optionList = lists.at(-1)
if (!optionList?.sections || optionList.sections.length !== 2) throw new Error('Episode picker did not combine quality and delivery in one native-flow menu')
if (!optionList.sections.flatMap(section => section.rows).some(row => /1080p.*Document/.test(row.title))) throw new Error('Combined 1080p/document choice missing')

saved = { quality:'720', delivery:'document' }
replies.length = 0
await runSeriesCommand(ctx, { capability:'anime', commandName:'anime', args:episodeRow.id.split(/\s+/).slice(1) })
if (!replies.some(value => value.includes('OK:download:720:document'))) throw new Error('Saved delivery default was not applied')

saved = null
lists.length = 0
await runSeriesCommand(ctx, { capability:'anime', commandName:'anime', args:episodeList.rows[0].id.split(/\s+/).slice(1) })
const startList = lists.at(-1)
const startRow = startList.rows.find(row => row.title === 'Ep 1')
if (!startRow) throw new Error('Range start picker missing')

lists.length = 0
await runSeriesCommand(ctx, { capability:'anime', commandName:'anime', args:startRow.id.split(/\s+/).slice(1) })
const endList = lists.at(-1)
const endRow = endList.rows.find(row => row.title === 'Ep 3')
if (!endRow) throw new Error('Range end picker missing')

saved = { quality:'1080', delivery:'video' }
replies.length = 0
await runSeriesCommand(ctx, { capability:'anime', commandName:'anime', args:endRow.id.split(/\s+/).slice(1) })
if (!replies.some(value => value.includes('OK:downloadRange:1080:video'))) throw new Error('Range download did not use saved defaults')

console.log('MSCC anime native-flow self-test OK')


{
  const aliasCalls = []
  const aliasLists = []
  const aliasCtx = {
    publicPrefix:'.',
    botProfile:{ id:'nami', displayName:'Nami' },
    listSources:() => [{ id:'alpha', name:'Alpha Anime' }],
    getSourceDefault:() => '',
    getDeliveryDefault:() => null,
    reply:async value => String(value),
    replyList:async value => { aliasLists.push(value); return value },
    resolveAnimeTitles:async query => ({
      query,
      aliases:[query, 'Attack on Titan', 'Shingeki no Kyojin', '進撃の巨人'],
      matches:[{ id:16498, title:'Shingeki no Kyojin' }],
      source:'anilist',
    }),
    executeSource:async ({ explicitSource, payload }) => {
      aliasCalls.push({ explicitSource, payload })
      if (payload.action === 'search') {
        if (payload.query === 'AOT') {
          return { status:'ok', source:{ id:'alpha', name:'Alpha Anime' }, result:{ items:[] }, fallback:false }
        }
        if (payload.query === 'Attack on Titan') {
          return {
            status:'ok',
            source:{ id:'alpha', name:'Alpha Anime' },
            result:{ items:[{ id:'aot', title:'Attack on Titan' }] },
            fallback:false,
          }
        }
        return { status:'ok', source:{ id:'alpha', name:'Alpha Anime' }, result:{ items:[] }, fallback:false }
      }
      if (payload.action === 'episodes') {
        return {
          status:'ok',
          source:{ id:'alpha', name:'Alpha Anime' },
          result:{ title:'Attack on Titan', episodes:[{ id:'e1', number:1, title:'To You, in 2000 Years' }] },
        }
      }
      throw new Error('Unexpected alias action ' + payload.action)
    },
  }

  await runSeriesCommand(aliasCtx, { capability:'anime', commandName:'anime', args:['AOT'] })
  const queries = aliasCalls.filter(call => call.payload.action === 'search').map(call => call.payload.query)
  if (queries.join('|') !== 'AOT|Attack on Titan') {
    throw new Error('AniList alias retry did not preserve original-first search: ' + queries.join('|'))
  }
  if (!aliasLists.at(-1)?.rows?.some(row => row.title === 'Ep 1')) {
    throw new Error('AniList alias retry did not reach the resolved anime episode list')
  }
}

console.log('MSCC AniList alias retry self-test OK')
