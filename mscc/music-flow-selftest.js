import { runSongCommand } from './music-flow.js'

const calls = []
const replies = []
let session = null
let commandReplyInput = ''

const ctx = {
  publicPrefix:'.',
  setCommandReplySession:value => { session = value },
  getCommandReplySession:() => session,
  clearCommandReplySession:() => { session = null },
  get commandReplyInput() { return commandReplyInput },
  reply:async value => { replies.push(String(value)); return value },
  executeSource:async ({ capability, pinnedSource, payload }) => {
    calls.push({ capability, pinnedSource, payload })
    if (payload.action === 'search') {
      return {
        status:'ok',
        source:{ id:'primary', name:'Primary Music' },
        result:{
          items:[
            { id:'a', title:'Song A', artist:'Artist 1', duration:'3:00' },
            { id:'b', title:'Song B', artist:'Artist 2', duration:'3:30' },
            { id:'c', title:'Song C', artist:'Artist 3', duration:'4:00' },
            { id:'d', title:'Song D', artist:'Artist 4', duration:'4:30' },
          ],
        },
      }
    }
    if (payload.action === 'download') {
      if (pinnedSource !== 'primary') throw new Error('Music download did not stay pinned to search source')
      return {
        status:'ok',
        source:{ id:'primary', name:'Primary Music' },
        result:{ text:'OK:' + payload.itemId },
      }
    }
    throw new Error('Unexpected music action ' + payload.action)
  },
}

await runSongCommand(ctx, { args:['hello'] })
if (!session || session.command !== 'song' || session.kind !== 'number-selection') {
  throw new Error('Song search did not create numeric selection session')
}
if (!replies.at(-1)?.includes('1. Song A') || !replies.at(-1)?.includes('1-4')) {
  throw new Error('Song result list or numeric reply hint missing')
}

replies.length = 0
commandReplyInput = '1,3-4'
await runSongCommand(ctx, { args:['~numbers'] })

const downloads = calls.filter(call => call.payload.action === 'download')
if (downloads.length !== 3) {
  throw new Error('Song numeric selection did not download exact chosen results')
}
if (downloads.map(call => call.payload.itemId).join('|') !== 'a|c|d') {
  throw new Error('Song selection order was not preserved')
}
if (!replies.some(value => value.includes('OK:a') && value.includes('OK:c') && value.includes('OK:d'))) {
  throw new Error('Song download confirmations missing')
}

console.log('PASS typed-number song search/download flow')


const instantLists = []
let instantSession = null
const instantCtx = {
  publicPrefix:'.',
  setCommandReplySession:value => { instantSession = value },
  getCommandReplySession:() => instantSession,
  clearCommandReplySession:() => { instantSession = null },
  reply:async value => value,
  replyList:async options => { instantLists.push(options); return options },
  executeSource:ctx.executeSource,
}
await runSongCommand(instantCtx, { args:['hello'] })
if (!instantLists.length) throw new Error('Song search did not expose Lyrics instant action')
if (instantLists[0].buttonText !== 'Lyrics') throw new Error('Song instant action is not labelled Lyrics')
if (!instantLists[0].rows?.[0]?.id?.startsWith('.lyrics ~track ')) {
  throw new Error('Song Lyrics instant action did not target exact track metadata')
}


const recoveryCalls = []
const recoveryReplies = []
let recoverySession = null
let recoveryInput = ''
const recoveryCtx = {
  publicPrefix:'.',
  setCommandReplySession:value => { recoverySession = value },
  getCommandReplySession:() => recoverySession,
  clearCommandReplySession:() => { recoverySession = null },
  get commandReplyInput() { return recoveryInput },
  reply:async value => { recoveryReplies.push(String(value)); return value },
  executeSource:async ({ pinnedSource, excludedSources = [], payload }) => {
    recoveryCalls.push({ pinnedSource, excludedSources:[...excludedSources], payload })
    if (payload.action === 'search' && !excludedSources.length) {
      return {
        status:'ok',
        source:{ id:'primary', name:'YouTube' },
        result:{ items:[{ id:'yt', title:'Same Song', artist:'Same Artist' }] },
      }
    }
    if (payload.action === 'download' && pinnedSource === 'primary') {
      return { status:'source-error', source:{ id:'primary', name:'YouTube' }, error:new Error('media expired') }
    }
    if (payload.action === 'search' && excludedSources.join('|') === 'primary') {
      return {
        status:'ok',
        source:{ id:'fallback-1', name:'Fallback One' },
        result:{ items:[{ id:'f1', title:'Same Song', artist:'Same Artist' }] },
      }
    }
    if (payload.action === 'download' && pinnedSource === 'fallback-1') {
      return { status:'source-error', source:{ id:'fallback-1', name:'Fallback One' }, error:new Error('media failed') }
    }
    if (payload.action === 'search' && excludedSources.join('|') === 'primary|fallback-1') {
      return {
        status:'ok',
        source:{ id:'fallback-2', name:'Fallback Two' },
        result:{ items:[{ id:'f2', title:'Same Song', artist:'Same Artist' }] },
      }
    }
    if (payload.action === 'download' && pinnedSource === 'fallback-2') {
      return {
        status:'ok',
        source:{ id:'fallback-2', name:'Fallback Two' },
        result:{ text:'RECOVERED:f2' },
      }
    }
    throw new Error('Unexpected recovery source call')
  },
}

await runSongCommand(recoveryCtx, { args:['same','song'] })
recoveryInput = '1'
await runSongCommand(recoveryCtx, { args:['~numbers'] })

if (!recoveryReplies.some(value => value.includes('RECOVERED:f2'))) {
  throw new Error('Music download did not recover through later managed fallbacks')
}
const recoverySearches = recoveryCalls
  .filter(call => call.payload.action === 'search')
  .map(call => call.excludedSources.join('|'))
if (recoverySearches.join(',') !== ',primary,primary|fallback-1') {
  throw new Error('Music fallback exclusions did not walk the managed chain: ' + recoverySearches.join(','))
}

console.log('PASS full managed music download fallback chain')
