import { runTvCommand } from './tv-flow.js'

const calls = []
const lists = []
const replies = []
let session = null
let commandReplyInput = ''
let saved = null

const ctx = {
  publicPrefix:'.',
  listSources:() => [{ id:'alpha', name:'Alpha TV' }],
  getDeliveryDefault:() => saved,
  setCommandReplySession:value => { session = value },
  getCommandReplySession:() => session,
  clearCommandReplySession:() => { session = null },
  get commandReplyInput() { return commandReplyInput },
  reply:async value => { replies.push(String(value)); return value },
  replyList:async value => { lists.push(value); return value },
  resolveTmdbTitles:async query => ({
    query,
    aliases:[query, 'Breaking Bad'],
    matches:[{ id:1396, title:'Breaking Bad', type:'tv' }],
    source:'tmdb',
  }),
  resolveTmdbMedia:async id => ({
    id:Number(id),
    type:'tv',
    title:'Breaking Bad',
    aliases:['Breaking Bad'],
    seasons:[
      { id:3572, number:1, name:'Season 1', episodeCount:4 },
      { id:3573, number:2, name:'Season 2', episodeCount:3 },
    ],
  }),
  resolveTmdbSeason:async (_id, seasonNumber) => ({
    seasonNumber,
    episodes:[
      { id:1, number:'1', seasonNumber, title:'Pilot' },
      { id:2, number:'2', seasonNumber, title:'Episode Two' },
      { id:3, number:'3', seasonNumber, title:'Episode Three' },
      { id:4, number:'4', seasonNumber, title:'Episode Four' },
    ],
  }),
  executeSource:async ({ explicitSource, payload }) => {
    calls.push({ explicitSource, payload })
    if (payload.action === 'search') {
      if (payload.query === 'BrBa') {
        return { status:'ok', source:{ id:'alpha', name:'Alpha TV' }, result:{ items:[] } }
      }
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha TV' },
        result:{ items:[{ id:'bb', title:'Breaking Bad' }] },
      }
    }
    if (payload.action === 'seasons') {
      return { status:'ok', source:{ id:'alpha', name:'Alpha TV' }, result:{ seasons:[] } }
    }
    if (payload.action === 'episodes') {
      return { status:'ok', source:{ id:'alpha', name:'Alpha TV' }, result:{ episodes:[] } }
    }
    if (payload.action === 'options') {
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha TV' },
        result:{ qualities:['1080','720'], deliveries:['document','video'] },
      }
    }
    if (payload.action === 'download' || payload.action === 'downloadRange') {
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha TV' },
        result:{ text:`OK:${payload.action}:${payload.quality}:${payload.delivery}:S${payload.seasonNumber}` },
      }
    }
    throw new Error('Unexpected TV action ' + payload.action)
  },
}

await runTvCommand(ctx, { args:['BrBa'] })
const searches = calls.filter(call => call.payload.action === 'search').map(call => call.payload.query)
if (searches.join('|') !== 'BrBa|Breaking Bad') {
  throw new Error('TV TMDB alias retry failed: ' + searches.join('|'))
}

const seasonList = lists.at(-1)
const seasonOne = seasonList?.rows?.find(row => row.title === 'Season 1')
if (!seasonOne) throw new Error('TV season picker missing Season 1')

replies.length = 0
await runTvCommand(ctx, { args:seasonOne.id.split(/\s+/).slice(1) })
if (!session || session.kind !== 'number-selection' || session.command !== 'tv') {
  throw new Error('TV season did not enter typed episode selection')
}
if (!replies.some(value => value.includes('1-10') && value.includes('1,3,4,7'))) {
  throw new Error('TV flow did not explain typed episode syntax')
}

lists.length = 0
commandReplyInput = '1-3'
await runTvCommand(ctx, { args:['~numbers'] })
if (session?.kind !== 'media-download-options') {
  throw new Error('TV numeric range did not enter download options')
}
const option = lists.at(-1)?.sections?.flatMap(section => section.rows)
  .find(row => row.id.includes('~selection-download 1080 document'))
if (!option) throw new Error('TV 1080/document download option missing')

replies.length = 0
await runTvCommand(ctx, { args:option.id.split(/\s+/).slice(1) })
if (!replies.some(value => value.includes('OK:downloadRange:1080:document:S1'))) {
  throw new Error('Contiguous TV number range did not use downloadRange')
}

await runTvCommand(ctx, { args:['Breaking','Bad'] })
const latestSeason = lists.at(-1)?.rows?.find(row => row.title === 'Season 1')
await runTvCommand(ctx, { args:latestSeason.id.split(/\s+/).slice(1) })
saved = { quality:'720', delivery:'video' }
commandReplyInput = '1,3,4'
calls.length = 0
replies.length = 0
await runTvCommand(ctx, { args:['~numbers'] })
const individual = calls.filter(call => call.payload.action === 'download')
if (individual.length !== 3) {
  throw new Error('Non-contiguous TV selection did not download exact episodes')
}
if (!individual.every(call => call.payload.quality === '720' && call.payload.delivery === 'video')) {
  throw new Error('Saved TV delivery default was not applied')
}

console.log('PASS TV season/typed-episode/TMDB/download flow')
