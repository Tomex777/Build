import { runSeriesCommand } from './series-flow.js'

const calls = []
const lists = []
const replies = []
let saved = null
let session = null
let commandReplyInput = ''

const ctx = {
  publicPrefix:'.',
  botProfile:{ id:'nami', displayName:'Nami' },
  listSources:() => [{ id:'alpha', name:'Alpha Anime' }],
  getSourceDefault:() => '',
  getDeliveryDefault:() => saved,
  setCommandReplySession:value => { session = value },
  getCommandReplySession:() => session,
  clearCommandReplySession:() => { session = null },
  get commandReplyInput() { return commandReplyInput },
  reply:async value => { replies.push(String(value)); return value },
  replyList:async value => { lists.push(value); return value },
  resolveAniListTitles:async query => ({
    query,
    aliases:[query],
    matches:[{ id:1, title:query, type:'ANIME' }],
    source:'anilist',
  }),
  resolveAniListMedia:async () => null,
  executeSource:async ({ explicitSource, payload }) => {
    calls.push({ explicitSource, payload })
    if (payload.action === 'search' || payload.action === 'browse') {
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha Anime' },
        result:{ items:[{ id:'bleach', title:'Bleach' }] },
        fallback:false,
      }
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
            { id:'e4', number:4, title:'Episode Four' },
          ],
        },
      }
    }
    if (payload.action === 'options') {
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha Anime' },
        result:{ qualities:['1080','720'], deliveries:['document','video'] },
      }
    }
    if (payload.action === 'download' || payload.action === 'downloadRange') {
      return {
        status:'ok',
        source:{ id:'alpha', name:'Alpha Anime' },
        result:{ text:`OK:${payload.action}:${payload.quality}:${payload.delivery}` },
      }
    }
    throw new Error('Unexpected action ' + payload.action)
  },
}

await runSeriesCommand(ctx, { capability:'anime', commandName:'anime', args:['Bleach'] })
if (!session || session.kind !== 'number-selection') {
  throw new Error('Anime flow did not create a numeric reply session')
}
if (!replies.some(value => value.includes('1-10') && value.includes('1,3,4,7'))) {
  throw new Error('Anime flow did not explain typed-number selection')
}

lists.length = 0
commandReplyInput = '1-2'
await runSeriesCommand(ctx, { capability:'anime', commandName:'anime', args:['~numbers'] })
if (session?.kind !== 'media-download-options') {
  throw new Error('Anime numeric range did not enter download options')
}
const optionList = lists.at(-1)
if (!optionList?.sections?.flatMap(section => section.rows).some(row => /1080p.*Document/.test(row.title))) {
  throw new Error('Anime range options are missing 1080p/document')
}
const rangeOption = optionList.sections.flatMap(section => section.rows)
  .find(row => row.id.includes('~selection-download 1080 document'))
if (!rangeOption) throw new Error('Anime numeric range download action missing')

replies.length = 0
await runSeriesCommand(ctx, {
  capability:'anime',
  commandName:'anime',
  args:rangeOption.id.split(/\s+/).slice(1),
})
if (!replies.some(value => value.includes('OK:downloadRange:1080:document'))) {
  throw new Error('Contiguous anime numeric range did not use downloadRange')
}

await runSeriesCommand(ctx, { capability:'anime', commandName:'anime', args:['Bleach'] })
saved = { quality:'720', delivery:'document' }
commandReplyInput = '1,3,4'
calls.length = 0
replies.length = 0
await runSeriesCommand(ctx, { capability:'anime', commandName:'anime', args:['~numbers'] })
const individual = calls.filter(call => call.payload.action === 'download')
if (individual.length !== 3) {
  throw new Error('Non-contiguous anime number selection did not download each selected episode')
}
if (!individual.every(call => call.payload.quality === '720' && call.payload.delivery === 'document')) {
  throw new Error('Saved anime delivery default was not used for typed numbers')
}
saved = null

{
  const aliasCalls = []
  const aliasReplies = []
  let aliasSession = null
  const aliasCtx = {
    publicPrefix:'.',
    botProfile:{ id:'nami', displayName:'Nami' },
    listSources:() => [{ id:'alpha', name:'Alpha Anime' }],
    getSourceDefault:() => '',
    getDeliveryDefault:() => null,
    setCommandReplySession:value => { aliasSession = value },
    getCommandReplySession:() => aliasSession,
    clearCommandReplySession:() => { aliasSession = null },
    commandReplyInput:'',
    reply:async value => { aliasReplies.push(String(value)); return value },
    replyList:async value => value,
    resolveAniListTitles:async query => ({
      query,
      aliases:[query, 'Attack on Titan', 'Shingeki no Kyojin', '進撃の巨人'],
      matches:[{ id:16498, title:'Shingeki no Kyojin' }],
      source:'anilist',
    }),
    resolveAniListMedia:async () => null,
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
  if (aliasSession?.kind !== 'number-selection') {
    throw new Error('AniList alias retry did not enter typed episode selection')
  }
  if (!aliasReplies.some(value => value.includes('Reply with the episode number'))) {
    throw new Error('AniList alias result did not show typed episode prompt')
  }
}

console.log('PASS anime typed-number/range/alias flow')
