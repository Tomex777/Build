import assert from 'node:assert/strict'
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
const query = String(process.env.MSCC_LIVE_QUERY || 'Bleach').trim()
assert.ok(query, 'MSCC_LIVE_QUERY must be non-empty')

const sourceChoices = [...sources.values()].map(row => ({
  id:row.id, name:row.name, description:row.description,
}))
const lists = []
const replies = []
const instantReplies = []
const sourceCalls = []
const downloads = []
let replySession = null
let commandReplyInput = ''

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
    // The sibling live probe validates actual media resolution and decoding.
    // Intercept the final send here so the same episode is not downloaded twice.
    if (payload.action === 'download' || payload.action === 'downloadRange') {
      downloads.push({ sourceId:chosen.id, payload })
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
  assert.ok(row && row.id, 'Interactive row has no command ID')
  const match = /^\.anime(?:\s+([\s\S]+))?$/.exec(String(row.id).trim())
  assert.ok(match, 'Interactive row does not route back to .anime: ' + row.id)
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
assert.ok(chooser, '.anime did not present a source-choice menu')
const providerRow = (chooser.rows || []).find(row => String(row.id).includes('~source ' + source.id + ' '))
assert.ok(providerRow, '.anime source menu omitted ' + source.name)

// Select the provider by replaying the command ID from its interactive row.
const listCountBeforeSearch = lists.length
await runRow(providerRow)

// Multiple results need a second interactive selection; a unique match should
// enter the episode prompt directly.
if (replySession?.kind !== 'number-selection') {
  const resultMenu = [...lists.slice(listCountBeforeSearch)].reverse().find(menu =>
    Array.isArray(menu.rows) && menu.rows.some(row => String(row.id).includes('~title ')))
  assert.ok(resultMenu, source.name + ': search returned neither an episode prompt nor a title list; calls=' + JSON.stringify(sourceCalls.map(call => ({ source:call.explicitSource, action:call.payload.action, query:call.payload.query }))) + '; replies=' + replies.join(' | ') + '; lists=' + JSON.stringify(lists.map(menu => ({ title:menu.title, rows:(menu.rows || []).map(row => row.title) }))))
  const wanted = query.toLowerCase().split(/\s+/).filter(token => token.length > 2)
  const titleRow = resultMenu.rows.find(row => {
    const title = String(row.title || '').toLowerCase()
    return title.includes(query.toLowerCase()) || wanted.some(token => title.includes(token))
  })
  assert.ok(titleRow, source.name + ': no search result matched "' + query + '"')
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

// Replay the final menu command. Assert the flow retains the selected source,
// episode, quality and delivery all the way to the download adapter call.
await runRow(chosenOption)
assert.equal(downloads.length, 1, source.name + ': option did not dispatch exactly one download')
const dispatched = downloads[0]
assert.equal(dispatched.sourceId, source.id, 'Download was dispatched to the wrong source')
assert.equal(dispatched.payload.action, 'download', 'Single-episode selection should use download')
assert.equal(dispatched.payload.episode?.id, episode.id, 'Selected episode ID changed before download')
const optionArgs = argsFromRow(chosenOption)
assert.equal(String(dispatched.payload.quality), String(optionArgs.at(-2)), 'Selected quality was not preserved')
assert.equal(String(dispatched.payload.delivery), String(optionArgs.at(-1)), 'Selected delivery was not preserved')

console.log(JSON.stringify({
  verdict:'PASS',
  probe:'MSCC .anime command integration',
  source:source.name,
  command:'.anime ' + query,
  sourceSelection:'PASS',
  titleSelection:'PASS',
  episodeCount:episodeSession.entries.length,
  selectedEpisode:episode.number,
  options:optionRows.map(row => row.title),
  downloadDispatch:{
    source:dispatched.sourceId,
    action:dispatched.payload.action,
    quality:dispatched.payload.quality,
    delivery:dispatched.payload.delivery,
  },
  note:'Final download was intercepted to avoid a duplicate transfer; the adjacent source probe validates real provider media delivery and decoding.',
}, null, 2))
