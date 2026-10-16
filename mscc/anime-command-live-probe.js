import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import animeCommand from './commands/anime/anime.js'
import animeSogo from './sources/anime/animesogo.js'
import kayoAnime from './sources/anime/kayoanime.js'

const sources = new Map([
  ['animesogo', animeSogo],
  ['kayoanime', kayoAnime],
])
const sourceId = String(process.argv[2] || '').trim().toLowerCase()
const source = sources.get(sourceId)
if (!source) throw new Error('Usage: node anime-command-live-probe.js <animesogo|kayoanime>')
// The important regression is Bleach: KayoAnime can return a title page with no
// episodes, so the public command must recover to AnimeSogo and fetch a real file.
const query = String(process.env.MSCC_COMMAND_LIVE_QUERY || 'Bleach').trim()
assert.ok(query, 'MSCC_COMMAND_LIVE_QUERY must be non-empty')
const mustRetrieveRealMedia = source.id === 'kayoanime' && /bleach/i.test(query)

const sourceChoices = [...sources.values()].map(row => ({
  id:row.id, name:row.name, description:row.description,
}))
const lists = []
const replies = []
const instantReplies = []
const sourceCalls = []
const downloads = []
const mediaProofs = []
let replySession = null
let commandReplyInput = ''

function must(value, message) {
  if (!value) throw new Error(message)
}

function runTool(command, args, timeout = 120000) {
  const result = spawnSync(command, args, {
    encoding:'utf8',
    timeout,
    maxBuffer:8 * 1024 * 1024,
  })
  return {
    ok:result.status === 0,
    status:result.status,
    signal:result.signal || '',
    stdout:String(result.stdout || '').trim(),
    stderr:String(result.stderr || '').trim().slice(-6000),
  }
}

function ffHeaders(headers = {}) {
  const ua = headers['user-agent'] || headers['User-Agent']
    || 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36'
  const rest = Object.entries(headers)
    .filter(([key, value]) => key.toLowerCase() !== 'user-agent' && value != null && String(value).trim())
    .map(([key, value]) => `${key}: ${String(value).trim()}`)
    .join('\r\n')
  const args = ['-user_agent', String(ua)]
  if (rest) args.push('-headers', rest + '\r\n')
  return args
}

async function verifyDeliveredMedia(payload, providerName) {
  const media = payload?.document || payload?.video
  const target = typeof media === 'string' ? media : media?.url
  must(target, `${providerName} .anime returned no media target to send`)
  const headers = typeof media === 'object' && media?.headers ? media.headers : {}
  const remote = /^https?:\/\//i.test(String(target))
  const transportArgs = remote ? ffHeaders(headers) : []
  const probe = runTool('ffprobe', [
    '-v','error',
    ...transportArgs,
    '-show_entries','format=format_name,duration,size:stream=index,codec_type,codec_name,width,height',
    '-of','json',
    String(target),
  ])
  must(probe.ok, `${providerName} .anime media ffprobe failed: ${probe.stderr}`)
  let json = {}
  try { json = JSON.parse(probe.stdout) } catch {}
  must(Array.isArray(json?.streams) && json.streams.some(row => row.codec_type === 'video'),
    `${providerName} .anime did not retrieve a file with a video stream`)
  const duration = Number(json?.format?.duration || 0)
  must(duration > 0, `${providerName} .anime media has no positive duration`)
  const decode = runTool('ffmpeg', [
    '-v','error',
    ...transportArgs,
    '-i',String(target),
    '-t','5',
    '-map','0:v:0',
    '-f','null','-',
  ])
  must(decode.ok, `${providerName} .anime media decode failed: ${decode.stderr}`)
  return {
    verified:true,
    targetKind:remote ? 'remote-url' : 'local-file',
    format:json?.format?.format_name || '',
    durationSeconds:Number(duration.toFixed(2)),
    sizeBytes:Number(json?.format?.size || 0),
    videoStreams:json.streams.filter(row => row.codec_type === 'video').map(row => ({
      codec:row.codec_name || '',
      width:Number(row.width || 0),
      height:Number(row.height || 0),
    })),
    decodedSeconds:5,
  }
}

const ctx = {
  args:[],
  publicPrefix:'.',
  botProfile:{ id:'josiah', displayName:'Josiah' },
  listSources:capability => capability === 'anime' ? sourceChoices : [],
  getSourceDefault:() => '',
  getDeliveryDefault:() => null,
  getCommandReplySession:() => replySession,
  setCommandReplySession:value => { replySession = value },
  clearCommandReplySession:() => { replySession = null },
  get commandReplyInput() { return commandReplyInput },
  reply:async value => { replies.push(String(value)); return value },
  replyList:async value => { lists.push(value); return value },
  replyInstant:async value => { instantReplies.push(value); return value },
  executeSource:async ({ capability, explicitSource = '', excludedSources = [], payload = {} }) => {
    sourceCalls.push({ capability, explicitSource, payload })
    if (capability !== 'anime') return { status:'no-sources', capability, sources:[] }
    const available = sourceChoices.filter(row => !excludedSources.includes(row.id))
    if (!explicitSource) return { status:'choice-required', capability, sources:available }
    const chosen = sources.get(String(explicitSource).toLowerCase())
    if (!chosen || !available.some(row => row.id === chosen.id)) {
      return { status:'unknown-source', capability, sourceId:String(explicitSource), sources:available }
    }
    if (payload.action === 'download' || payload.action === 'downloadRange') {
      const record = { sourceId:chosen.id, payload, result:null, mediaProof:null }
      downloads.push(record)
      // The KayoAnime -> AnimeSogo Bleach fallback must actually retrieve and
      // decode the episode selected through .anime, not merely list episodes.
      if (mustRetrieveRealMedia) {
        try {
          const result = await chosen.run({
            ...payload,
            context:{
              send:async mediaPayload => {
                record.mediaProof = await verifyDeliveredMedia(mediaPayload, chosen.name)
                mediaProofs.push({ source:chosen.id, episode:payload.episode?.number, ...record.mediaProof })
              },
            },
            source:chosen,
          })
          record.result = result
          if (result?.delivered !== true || !record.mediaProof) {
            throw new Error('Provider returned without completing a verified media send')
          }
          return { status:'ok', capability, source:chosen, result, fallback:false, fallbackFrom:null }
        } catch (error) {
          record.error = String(error?.stack || error)
          return { status:'source-error', capability, source:chosen, error }
        }
      }
      // For the direct AnimeSogo command case, the adjacent live provider probe
      // already validates media bytes and decoding. Avoid a duplicate full file.
      return {
        status:'ok', capability, source:chosen,
        result:{ delivered:true, commandProbeIntercepted:true },
        fallback:false, fallbackFrom:null,
      }
    }
    try {
      const result = await chosen.run({ ...payload, context:ctx, source:chosen })
      return { status:'ok', capability, source:chosen, result, fallback:false, fallbackFrom:null }
    } catch (error) {
      return { status:'source-error', capability, source:chosen, error }
    }
  },
}

function argsFromRow(row) {
  must(row && row.id, 'Interactive row has no command ID')
  const match = /^\.anime(?:\s+([\s\S]+))?$/.exec(String(row.id).trim())
  must(match, 'Interactive row does not route back to .anime: ' + row.id)
  return String(match[1] || '').trim().split(/\s+/).filter(Boolean)
}

async function runText(text) {
  ctx.args = String(text || '').trim().split(/\s+/).filter(Boolean)
  return animeCommand.run(ctx)
}

async function runRow(row) {
  ctx.args = argsFromRow(row)
  return animeCommand.run(ctx)
}

// Start with the real public command and ensure an unset default creates a source menu.
await runText(query)
const chooser = [...lists].reverse().find(menu => /choose anime source/i.test(String(menu.title || '')))
must(chooser, '.anime did not present a source-choice menu')
const providerRow = (chooser.rows || []).find(row => String(row.id).includes('~source ' + source.id + ' '))
must(providerRow, '.anime source menu omitted ' + source.name)

// Select the provider by replaying the command ID from its interactive row.
const listCountBeforeSearch = lists.length
await runRow(providerRow)

// Multiple results need a second interactive selection; a unique match should
// enter the episode prompt directly.
if (replySession?.kind !== 'number-selection') {
  const resultMenu = [...lists.slice(listCountBeforeSearch)].reverse().find(menu =>
    Array.isArray(menu.rows) && menu.rows.some(row => String(row.id).includes('~title ')))
  must(resultMenu, source.name + ': search returned neither an episode prompt nor a title list; calls='
    + JSON.stringify(sourceCalls.map(call => ({ source:call.explicitSource, action:call.payload.action, query:call.payload.query })))
    + '; replies=' + replies.join(' | ')
    + '; lists=' + JSON.stringify(lists.map(menu => ({ title:menu.title, rows:(menu.rows || []).map(row => row.title) }))))
  const wanted = query.toLowerCase().split(/\s+/).filter(token => token.length > 2)
  const titleRow = (source.id === 'kayoanime' && /bleach/i.test(query)
    ? resultMenu.rows.find(row => /bleach/i.test(String(row.title || ''))
      && /thousand.year blood war|calamity/i.test(String(row.title || '')))
    : null) || resultMenu.rows.find(row => {
      const title = String(row.title || '').toLowerCase()
      return title.includes(query.toLowerCase()) || wanted.some(token => title.includes(token))
    })
  must(titleRow, source.name + ': no matching search result for "' + query + '"')
  await runRow(titleRow)
}

const episodeSession = replySession
assert.equal(episodeSession?.kind, 'number-selection',
  source.name + ': title selection did not open episode selection')
assert.ok(Array.isArray(episodeSession.entries) && episodeSession.entries.length > 0,
  source.name + ': command returned an empty episode list')
const episode = episodeSession.entries.find(row => Number.isFinite(Number(row.number)))
assert.ok(episode, source.name + ': episode list had no selectable numeric episode')
const episodePrompt = [...instantReplies].reverse()[0]?.text || replies.at(-1) || ''
assert.ok(/episode/i.test(String(episodePrompt)), source.name + ': episode prompt was not displayed')

// The fallback test must prove the command switched away from the broken KayoAnime title.
if (mustRetrieveRealMedia) {
  assert.equal(episodeSession.sourceId, 'animesogo',
    'Bleach had no KayoAnime episodes, but .anime did not switch to AnimeSogo')
  assert.ok(/switched to AnimeSogo/i.test(String(episodePrompt)),
    'The .anime reply did not tell the user the fallback source was selected')
}

// Type an episode number and verify the command presents delivery choices.
commandReplyInput = String(episode.number)
await runText('~numbers')
const options = [...lists].reverse().find(menu => /download options/i.test(String(menu.title || '')))
assert.ok(options, source.name + ': episode-number input did not open download options')
const optionRows = [
  ...(options.rows || []),
  ...(options.sections || []).flatMap(section => section.rows || []),
]
assert.ok(optionRows.some(row => /document/i.test(String(row.title || ''))),
  source.name + ': no document-delivery option was offered')
assert.ok(optionRows.some(row => /video/i.test(String(row.title || ''))),
  source.name + ': no in-chat video option was offered')
const chosenOption = optionRows.find(row => /source quality.*document/i.test(String(row.title || '')))
  || optionRows.find(row => /document/i.test(String(row.title || '')))
assert.ok(chosenOption, source.name + ': no selectable document option')

// Replay the final menu command. For the Bleach regression this downloads and
// decodes the real media produced by the selected episode through the real source.
await runRow(chosenOption)
assert.equal(downloads.length, 1, source.name + ': option did not dispatch exactly one download')
const dispatched = downloads[0]
assert.equal(dispatched.sourceId, episodeSession.sourceId, 'Download was dispatched to the wrong source')
assert.equal(dispatched.payload.action, 'download', 'Single-episode selection should use download')
assert.equal(dispatched.payload.episode?.id, episode.id, 'Selected episode ID changed before download')
const optionArgs = argsFromRow(chosenOption)
assert.equal(String(dispatched.payload.quality), String(optionArgs.at(-2)), 'Selected quality was not preserved')
assert.equal(String(dispatched.payload.delivery), String(optionArgs.at(-1)), 'Selected delivery was not preserved')

if (mustRetrieveRealMedia) {
  assert.equal(dispatched.sourceId, 'animesogo', 'Bleach episode retrieval used the wrong provider')
  assert.equal(dispatched.result?.delivered, true, 'The real anime provider did not deliver the selected episode')
  assert.ok(dispatched.mediaProof?.verified, 'The selected episode was not verified as playable media')
  assert.equal(mediaProofs.length, 1, 'Expected exactly one verified episode media delivery')
}

console.log(JSON.stringify({
  verdict:'PASS',
  probe:'MSCC .anime command integration',
  source:source.name,
  command:'.anime ' + query,
  sourceSelection:'PASS',
  titleSelection:'PASS',
  episodeCount:episodeSession.entries.length,
  selectedEpisode:{ number:episode.number, title:episode.title },
  episodeProvider:episodeSession.sourceId,
  options:optionRows.map(row => row.title),
  downloadDispatch:{
    source:dispatched.sourceId,
    action:dispatched.payload.action,
    quality:dispatched.payload.quality,
    delivery:dispatched.payload.delivery,
  },
  retrievedMedia:dispatched.mediaProof || null,
  note:mustRetrieveRealMedia
    ? 'PASS: the real .anime command switched from KayoAnime, downloaded the selected AnimeSogo episode, verified a video stream, and decoded five seconds.'
    : 'Final transfer intercepted here; the adjacent live AnimeSogo source probe validates actual media delivery and decoding.',
}, null, 2))
