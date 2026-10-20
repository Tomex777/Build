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


{
  const fallbackCalls = []
  const fallbackReplies = []
  const fallbackLists = []
  let fallbackSession = null
  const fallbackCtx = {
    publicPrefix:'.',
    botProfile:{ id:'nami', displayName:'Nami' },
    listSources:() => [
      { id:'kayoanime', name:'KayoAnime' },
      { id:'animesogo', name:'AnimeSogo' },
    ],
    getSourceDefault:() => '',
    getDeliveryDefault:() => null,
    setCommandReplySession:value => { fallbackSession = value },
    getCommandReplySession:() => fallbackSession,
    clearCommandReplySession:() => { fallbackSession = null },
    reply:async value => { fallbackReplies.push(String(value)); return value },
    replyList:async value => { fallbackLists.push(value); return value },
    resolveAniListTitles:async query => ({
      query,
      aliases:[query, 'Bleach: Thousand-Year Blood War'],
      matches:[{ id:269, title:'Bleach: Thousand-Year Blood War' }],
    }),
    resolveAniListMedia:async () => null,
    executeSource:async ({ explicitSource, payload }) => {
      fallbackCalls.push({ explicitSource, payload })
      if (explicitSource === 'kayoanime' && payload.action === 'search') {
        return {
          status:'ok', source:{ id:'kayoanime', name:'KayoAnime' },
          result:{ items:[{ id:'kayo-bleach', title:'Bleach: Thousand-Year Blood War - The Calamity' }] },
        }
      }
      if (explicitSource === 'kayoanime' && payload.action === 'episodes') {
        return {
          status:'source-error',
          source:{ id:'kayoanime', name:'KayoAnime' },
          error:new Error('KayoAnime returned no episodes.'),
        }
      }
      if (explicitSource === 'animesogo' && payload.action === 'search') {
        return {
          status:'ok', source:{ id:'animesogo', name:'AnimeSogo' },
          result:{ items:[{ id:'sogo-bleach', title:'Bleach: Thousand-Year Blood War' }] },
        }
      }
      if (explicitSource === 'animesogo' && payload.action === 'episodes') {
        return {
          status:'ok', source:{ id:'animesogo', name:'AnimeSogo' },
          result:{ title:'Bleach: Thousand-Year Blood War', episodes:[
            { id:'sogo-e1', number:1, title:'Episode 1' },
            { id:'sogo-e2', number:2, title:'Episode 2' },
          ] },
        }
      }
      throw new Error('Unexpected fallback action: ' + explicitSource + '/' + payload.action)
    },
  }

  await runSeriesCommand(fallbackCtx, {
    capability:'anime',
    commandName:'anime',
    args:['~source', 'kayoanime', Buffer.from(JSON.stringify('Bleach')).toString('base64url')],
  })
  if (fallbackSession?.kind !== 'number-selection') {
    throw new Error('Anime command did not recover from a title with no episodes on the selected source')
  }
  if (fallbackSession.sourceId !== 'animesogo') {
    throw new Error('Anime empty-episode recovery did not switch the session to the working provider')
  }
  if (fallbackSession.entries.length !== 2) {
    throw new Error('Anime empty-episode recovery did not load the alternate provider episode list')
  }
  if (!fallbackReplies.some(value => value.includes('switched to AnimeSogo'))) {
    throw new Error('Anime fallback did not explain the provider switch to the user')
  }
  if (!fallbackCalls.some(call => call.explicitSource === 'animesogo' && call.payload.action === 'episodes')) {
    throw new Error('Anime fallback never verified episodes on the alternate provider')
  }
}

{
  const progressRuns = []
  let deliveryMode = 'held'
  let releaseHeldSource
  let signalHeldSourceStarted
  const heldSourceStarted = new Promise(resolve => { signalHeldSourceStarted = resolve })
  const heldSource = new Promise(resolve => { releaseHeldSource = resolve })

  const progressCtx = {
    publicPrefix:'.',
    botProfile:{ id:'nami', displayName:'Nami' },
    listSources:() => [
      { id:'animepahe', name:'AnimePahe' },
      { id:'animesogo', name:'AnimeSogo' },
    ],
    reply:async value => String(value),
    progress:async initial => {
      let finish
      const finished = new Promise(resolve => { finish = resolve })
      const row = { initial:String(initial), updates:[], finished }
      const update = async value => {
        row.updates.push(String(value))
        finish(String(value))
        return value
      }
      row.progress = { update, done:update, fail:update }
      progressRuns.push(row)
      return row.progress
    },
    executeSource:async ({ payload }) => {
      if (payload.action !== 'download') throw new Error('Unexpected delivery test action: ' + payload.action)
      if (deliveryMode === 'held') {
        signalHeldSourceStarted()
        return heldSource
      }
      return {
        status:'source-error',
        source:{ id:'animepahe', name:'AnimePahe' },
        error:new Error('Media HTTP 403 for https://media.example/episode.m3u8?token=secret-value'),
      }
    },
  }

  const encode = value => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
  const args = [
    '~download',
    'animepahe',
    encode({ id:'bleach', title:'Bleach' }),
    encode({ id:'episode-1', number:1, title:'Episode 1' }),
    '720',
    'document',
  ]

  const returned = await Promise.race([
    runSeriesCommand(progressCtx, { capability:'anime', commandName:'anime', args }).then(() => true),
    new Promise(resolve => setTimeout(() => resolve(false), 1000)),
  ])
  if (!returned) throw new Error('Anime download command waited for the media source instead of acknowledging immediately')
  await Promise.race([
    heldSourceStarted,
    new Promise((_, reject) => setTimeout(() => reject(new Error('Background anime download never reached the source')), 1000)),
  ])
  if (!progressRuns[0]?.initial.includes('Preparing') || !progressRuns[0]?.initial.includes('Bleach')) {
    throw new Error('Anime media delivery did not acknowledge the selected episode')
  }
  if (progressRuns[0].updates.length) {
    throw new Error('Anime media delivery reported completion while the source was still held')
  }

  releaseHeldSource({
    status:'ok',
    source:{ id:'animepahe', name:'AnimePahe' },
    result:{ delivered:true },
  })
  const successful = await Promise.race([
    progressRuns[0].finished,
    new Promise((_, reject) => setTimeout(() => reject(new Error('Anime delivery completion was not reported')), 1500)),
  ])
  if (!successful.includes('Episode 1 was sent')) {
    throw new Error('Anime delivery completion did not confirm that media was sent')
  }

  deliveryMode = 'failed'
  const failedReturn = await runSeriesCommand(progressCtx, { capability:'anime', commandName:'anime', args })
  if (failedReturn !== true) throw new Error('Failed anime delivery did not return after queuing')
  const failed = await Promise.race([
    progressRuns[1].finished,
    new Promise((_, reject) => setTimeout(() => reject(new Error('Anime source failure was not reported')), 1500)),
  ])
  if (!failed.includes('HTTP 403') || failed.includes('secret-value')) {
    throw new Error('Anime media failure details were not reported safely: ' + failed)
  }
}

console.log('PASS anime typed-number/range/alias flow')
