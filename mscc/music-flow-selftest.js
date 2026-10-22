import { runSongCommand } from './music-flow.js'
import { musicFollowupReply } from './music-chat-context.js'
import { readFileSync } from 'node:fs'

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
            { id:'a', title:'Song A', artist:'Artist 1', duration:'3:00', cover:'https://example.test/song-a.jpg' },
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
if (!replies.at(-1)?.includes('🎵 *Song Search Results*') ||
    !replies.at(-1)?.includes('1. Song A — Artist 1 [3:00]') ||
    !replies.at(-1)?.includes('1-4') ||
    !replies.at(-1)?.includes('--doc')) {
  throw new Error('Locked song result menu or numeric/document hint missing')
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

const docCalls = []
let docSession = null
let docInput = ''
const docCtx = {
  publicPrefix:'.',
  setCommandReplySession:value => { docSession = value },
  getCommandReplySession:() => docSession,
  clearCommandReplySession:() => { docSession = null },
  get commandReplyInput() { return docInput },
  reply:async value => value,
  executeSource:async ({ payload, pinnedSource }) => {
    docCalls.push({ payload, pinnedSource })
    if (payload.action === 'search') {
      return {
        status:'ok',
        source:{ id:'primary', name:'YouTube' },
        result:{ items:[{ id:'doc-a', title:'Document Song', artist:'Artist', duration:'2:05' }] },
      }
    }
    if (payload.action === 'download') {
      if (payload.delivery !== 'document') throw new Error('--doc did not survive numeric selection')
      return { status:'ok', source:{ id:'primary', name:'YouTube' }, result:{ delivered:true } }
    }
    throw new Error('Unexpected document-mode action')
  },
}
await runSongCommand(docCtx, { args:['document','song','--doc'] })
if (docSession?.delivery !== 'document' || docSession?.query !== 'document song') {
  throw new Error('--doc was not removed from query and preserved in song session')
}
docInput = '1'
await runSongCommand(docCtx, { args:['~numbers'] })
if (!docCalls.some(call => call.payload.action === 'download' && call.payload.delivery === 'document')) {
  throw new Error('Document delivery was not forwarded to the selected music source')
}
console.log('PASS song --doc delivery session')

const instantLists = []
const instantImages = []
let instantSession = null
const instantCtx = {
  publicPrefix:'.',
  setCommandReplySession:value => { instantSession = value },
  getCommandReplySession:() => instantSession,
  clearCommandReplySession:() => { instantSession = null },
  reply:async value => value,
  replyList:async options => { instantLists.push(options); return options },
  sendImageUrl:async (url,caption) => { instantImages.push({ url:String(url), caption:String(caption) }); return true },
  executeSource:ctx.executeSource,
}
await runSongCommand(instantCtx, { args:['hello'] })
if (!instantLists.length) throw new Error('Song search did not expose Lyrics instant action')
if (instantLists[0].buttonText !== 'Lyrics') throw new Error('Song instant action is not labelled Lyrics')
if (instantLists[0].image?.url !== 'https://example.test/song-a.jpg') {
  throw new Error('Song search artwork missing from result message')
}
if (!instantLists[0].rows?.[0]?.id?.startsWith('.lyrics ~track ')) {
  throw new Error('Song Lyrics instant action did not target exact track metadata')
}



const fallbackReplies = []
let fallbackSession = null
await runSongCommand({
  publicPrefix:'.',
  reply:async value => { fallbackReplies.push(String(value)); return value },
  replyList:async () => { throw new Error('interactive delivery unavailable') },
  setCommandReplySession:value => { fallbackSession = value },
  executeSource:ctx.executeSource,
}, { args:['hello'] })
if (!fallbackReplies.at(-1)?.includes('🎵 *Song Search Results*') ||
    !fallbackReplies.at(-1)?.includes('1. Song A') ||
    fallbackSession?.kind !== 'number-selection') {
  throw new Error('Unavailable interactive lists must preserve song search text and numeric selection')
}
console.log('PASS song Lyrics instant action and graceful plain-text fallback')

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
// Search can broaden the original query into additional variants before
// falling back. Verify the managed source exclusions rather than assuming
// there is exactly one unrestricted search request.
if (!recoverySearches.length || recoverySearches[0] !== '' ||
    recoverySearches.filter(Boolean).join(',') !== 'primary,primary|fallback-1') {
  throw new Error('Music fallback exclusions did not walk the managed chain: ' + recoverySearches.join(','))
}

console.log('PASS full managed music download fallback chain')


// A provider returning "ok" without actually delivering any bytes must be
// treated as a failure. Users must get visible feedback and may retry 1.
const missingDelivery = []
const progressStatus = []
let retrySession = null
let canSend = false
const retryCtx = {
  publicPrefix:'.',
  setCommandReplySession:value => { retrySession = value },
  getCommandReplySession:() => retrySession,
  clearCommandReplySession:() => { retrySession = null },
  commandReplyInput:'1',
  reply:async text => { missingDelivery.push(String(text)); return text },
  progress:async text => {
    progressStatus.push('begin:' + text)
    return {
      fail:async message => { progressStatus.push('failed:' + message) },
      done:async message => { progressStatus.push('done:' + message) },
    }
  },
  executeSource:async ({ pinnedSource, excludedSources = [], payload }) => {
    if (payload.action === 'search' && !excludedSources.length) {
      return {
        status:'ok',
        source:{ id:'primary', name:'YouTube' },
        result:{ items:[{ id:'a', title:'abc', artist:'Artist' }] },
      }
    }
    if (payload.action === 'search' && excludedSources.includes('primary')) {
      return { status:'all-failed', error:new Error('no fallback'), sources:[] }
    }
    if (payload.action === 'download' && pinnedSource === 'primary') {
      return { status:'ok', source:{ id:'primary', name:'YouTube' }, result:canSend ? { delivered:true } : undefined }
    }
    throw new Error('Unexpected music request in retry test')
  },
}
await runSongCommand(retryCtx, { args:['abc'] })
if (!retrySession) throw new Error('Music menu must set retry session')
await runSongCommand(retryCtx, { args:['~numbers'] })
if (!retrySession || !progressStatus.some(row => row.startsWith('failed:'))) {
  throw new Error('Silent music delivery must report failure and preserve numeric selection')
}
if (progressStatus.some(row => row.startsWith('done:'))) {
  throw new Error('Music source with no confirmation must not report delivered')
}
canSend = true
await runSongCommand(retryCtx, { args:['~numbers'] })
if (!retrySession?.lastSentAt || !progressStatus.some(row => row.startsWith('done:'))) {
  throw new Error('Successful send must retain a brief retry session and report WhatsApp send, not recipient delivery')
}
if (!progressStatus.some(row => row.includes('Audio sent to WhatsApp'))) {
  throw new Error('Music send confirmation must not claim the recipient received the track')
}
console.log('PASS silent-music failure reporting and selection retry')

// Regression: an accidental spelling of the Pitch Perfect 2 song must not
// produce 25 unrelated adult-toy/movie results or rank a lyric upload above
// an official Jessie J recording.
let correctedQuery = ''
const rankedReplies = []
let rankedSession = null
await runSongCommand({
  publicPrefix:'.',
  reply:async value => { rankedReplies.push(String(value)); return value },
  setCommandReplySession:value => { rankedSession = value },
  executeSource:async ({ payload }) => {
    if (payload.action !== 'search') throw new Error('Unexpected music action')
    correctedQuery = payload.query
    return { status:'ok', source:{ id:'youtube', name:'YouTube' }, result:{ items:[
      { id:'a', title:'Jessie J - Flashlight (from Pitch Perfect 2) (Lyrics)', artist:'HopeLyrics' },
      { id:'b', title:'Jessie J - Flashlight (from Pitch Perfect 2) (Official Video)', artist:'Jessie J' },
      { id:'c', title:'The Best Fleshlight Reviewed', artist:'Unrelated uploader' },
      { id:'d', title:'Flashlight (from Pitch Perfect 2) live cover', artist:'Fan Channel' },
    ] } }
  },
}, { args:['fleshlight','by','pitch','perfect','2'] })
if (correctedQuery !== 'Flashlight by pitch perfect 2') {
  throw new Error('Pitch Perfect 2 track spelling was not normalized')
}
if (rankedSession?.entries?.[0]?.id !== 'b') {
  throw new Error('The official song did not outrank a lyrics repost')
}
if (rankedSession.entries.some(row => row.id === 'c')) {
  throw new Error('Unrelated explicit search result was not filtered')
}
if (!rankedReplies[0]?.includes('Showing matches for: *Flashlight by pitch perfect 2*')) {
  throw new Error('Search correction was not disclosed in the song menu')
}
console.log('PASS soundtrack typo, official recording ranking and unrelated-result pruning')

const followupSession = { kind:'number-selection', command:'song', entries:[{ number:'1' }] }
const lostAudio = musicFollowupReply("Where's the song", followupSession)
if (!lostAudio.includes('cannot confirm') || !lostAudio.includes('same song number')) {
  throw new Error('Missing song must be handled as an honest retry, not as AI banter')
}
if (!musicFollowupReply('Original', followupSession).includes('official artist')) {
  throw new Error('Original track follow-up must not make up a soundtrack or artist')
}
if (!musicFollowupReply('Fleshlight by Pitch Perfect 2', followupSession).includes('Flashlight by Jessie J')) {
  throw new Error('Soundtrack typo clarification missing')
}
if (musicFollowupReply('Original', null) || musicFollowupReply('Hello', followupSession)) {
  throw new Error('Music follow-up guard must not interrupt ordinary conversations')
}
const indexSource = readFileSync(new URL('./index.js', import.meta.url), 'utf8')
if (!indexSource.includes("import { musicFollowupReply } from './music-chat-context.js'") ||
    indexSource.indexOf('const musicGuidance = musicFollowupReply(') < 0 ||
    indexSource.indexOf('const musicGuidance = musicFollowupReply(') > indexSource.indexOf('if (await handleProfileAssistant(account, msg, authority, text)) return')) {
  throw new Error('Music follow-up must be handled before the generative personality')
}
console.log('PASS music post-search questions avoid invented delivery and repeated AI clarification')
