import {
  namiDownloadStarted,
  namiExpiredSelection,
  namiNoResults,
  namiNoSources,
  namiSourceFailure,
} from './response-pools.js'
import { counterpartInstantRows } from './media-relations.js'
import { addCanonicalLibraryItem, addToLibraryAction, libraryStatusLine } from './media-library.js'
import { parseNumberSelection } from './number-selection.js'

const token = value => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
const untoken = value => JSON.parse(Buffer.from(String(value || ''), 'base64url').toString('utf8'))

const DEFAULT_QUALITIES = ['source','1080','720','480','360']
const DEFAULT_DELIVERIES = ['document','video']

const titleOf = item => String(item?.title || item?.name || 'Untitled')
const idOf = item => String(item?.id ?? item?.slug ?? item?.url ?? titleOf(item))

const normalizeSeries = item => ({
  id:idOf(item),
  title:titleOf(item),
  description:String(item?.description || item?.year || item?.status || '').trim(),
})

const normalizeEpisode = (episode, index) => ({
  id:String(episode?.id ?? episode?.number ?? episode?.episode ?? index + 1),
  number:String(episode?.number ?? episode?.episode ?? index + 1),
  title:String(episode?.title || episode?.name || `Episode ${episode?.number ?? episode?.episode ?? index + 1}`),
})

function outcomeError(ctx, capability, outcome) {
  const nami = ctx.botProfile?.id === 'nami'
  if (outcome.status === 'source-error') {
    return ctx.reply(nami
      ? namiSourceFailure(capability, outcome.source?.name || '')
      : `${outcome.source?.name || 'The source'} could not complete that ${capability} request.`)
  }
  if (outcome.status === 'all-failed') {
    return ctx.reply(nami
      ? namiSourceFailure(capability, '', true)
      : `All configured ${capability} sources failed for that request.`)
  }
  if (outcome.status === 'no-sources') {
    return ctx.reply(nami
      ? namiNoSources(capability)
      : `No ${capability} sources are installed yet.`)
  }
  return ctx.reply(`Could not complete that ${capability} request.`)
}

function fallbackNote(outcome) {
  return outcome?.fallback
    ? `⚠️ Fallback: ${outcome.fallbackFrom?.name || 'your default source'} was unavailable, so ${outcome.source?.name || 'another source'} is being used.\n\n`
    : ''
}

async function chooseSource(ctx, { capability, commandName, query }) {
  const sources = ctx.listSources(capability)
  const prefix = ctx.publicPrefix || '.'
  if (!sources.length) return ctx.reply(ctx.botProfile?.id === 'nami'
    ? namiNoSources(capability)
    : `No ${capability} sources are installed yet.`)
  return ctx.replyList({
    title:`Choose ${capability} source`,
    text:`Choose a source for ${query ? `“${query}”` : capability}.`,
    buttonText:'Choose source',
    footer:`Save a default: ${prefix}source ${capability} <source>\nList sources: ${prefix}sources ${capability}`,
    rows:sources.map(source => ({
      title:source.name,
      description:source.description || 'Use for this request',
      id:`${prefix}${commandName} ~source ${source.id} ${token(query || '')}`,
    })),
  })
}

function searchResultHasContent(result) {
  if (!result) return false
  if (Array.isArray(result.episodes) && result.episodes.length) return true
  if (Array.isArray(result.items) && result.items.length) return true
  return Boolean(result.item)
}

function uniqueQueries(values = []) {
  const seen = new Set()
  const out = []
  for (const value of values) {
    const query = String(value || '').trim()
    if (!query) continue
    const key = query.toLocaleLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    out.push(query)
  }
  return out
}

function titleTokens(value) {
  const stop = new Set(['the','and','for','with','part','season','tv','series','anime','dub','sub'])
  return new Set(String(value || '')
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase()
    .split(/[^a-z0-9]+/)
    .filter(word => word.length > 1 && !stop.has(word)))
}

function titleMatchScore(candidate, targets) {
  const candidateTokens = titleTokens(titleOf(candidate))
  if (!candidateTokens.size) return 0
  let best = 0
  for (const target of targets) {
    const targetTokens = titleTokens(target)
    if (targetTokens.size < 2) continue
    let common = 0
    for (const token of candidateTokens) if (targetTokens.has(token)) common += 1
    const union = new Set([...candidateTokens, ...targetTokens]).size
    const jaccard = union ? common / union : 0
    const targetCoverage = common / targetTokens.size
    const candidateCoverage = common / candidateTokens.size
    best = Math.max(best, jaccard, targetCoverage * 0.9, candidateCoverage * 0.65)
  }
  return best
}

async function retryAnimeAliases(ctx, {
  capability,
  query,
  sourceId,
  firstOutcome,
}) {
  const resolveTitles = typeof ctx.resolveAniListTitles === 'function'
    ? value => ctx.resolveAniListTitles(value, 'ANIME')
    : typeof ctx.resolveAnimeTitles === 'function'
      ? value => ctx.resolveAnimeTitles(value)
      : null

  if (
    capability !== 'anime' ||
    !query ||
    !resolveTitles ||
    firstOutcome?.status !== 'ok' ||
    searchResultHasContent(firstOutcome.result)
  ) {
    return firstOutcome
  }

  const resolved = await resolveTitles(query)
  const aliases = uniqueQueries([query, ...(resolved?.aliases || [])])
    .filter(value => value.toLocaleLowerCase() !== String(query).toLocaleLowerCase())
    .slice(0, 5)

  for (const alias of aliases) {
    const outcome = await ctx.executeSource({
      capability,
      explicitSource:sourceId || firstOutcome.source?.id || '',
      payload:{
        action:'search',
        query:alias,
        originalQuery:query,
        aliases:resolved?.aliases || [],
        anilist:resolved?.matches?.[0] || null,
      },
    })
    if (outcome.status !== 'ok') continue
    if (searchResultHasContent(outcome.result)) {
      return {
        ...outcome,
        titleAliasUsed:alias,
        titleResolvedBy:'anilist',
        originalQuery:query,
      }
    }
  }

  return firstOutcome
}

async function executeSearch(ctx, { capability, commandName, query, sourceId = '' }) {
  const action = query ? 'search' : 'browse'
  let outcome = await ctx.executeSource({
    capability,
    explicitSource:sourceId,
    payload:{ action, query },
  })

  if (outcome.status === 'choice-required') return chooseSource(ctx, { capability, commandName, query })
  if (outcome.status !== 'ok') return outcomeError(ctx, capability, outcome)

  if (action === 'search') {
    outcome = await retryAnimeAliases(ctx, {
      capability,
      query,
      sourceId,
      firstOutcome:outcome,
    })
  }

  const result = outcome.result || {}
  if (Array.isArray(result.episodes)) {
    const series = normalizeSeries(result)
    return showEpisodes(ctx, { capability, commandName, sourceId:outcome.source.id, series, episodes:result.episodes, note:fallbackNote(outcome) })
  }

  const items = Array.isArray(result.items) ? result.items.map(normalizeSeries) : result.item ? [normalizeSeries(result.item)] : []
  if (!items.length) return ctx.reply(ctx.botProfile?.id === 'nami'
    ? namiNoResults(capability, query)
    : `No ${capability} results found${query ? ` for “${query}”` : ''}.`)
  if (items.length === 1) return loadEpisodes(ctx, { capability, commandName, sourceId:outcome.source.id, series:items[0], note:fallbackNote(outcome) })

  const prefix = ctx.publicPrefix || '.'
  return ctx.replyList({
    title:`Choose ${capability}`,
    text:`${fallbackNote(outcome)}${outcome.titleAliasUsed ? `Matched “${query}” through “${outcome.titleAliasUsed}”.\n\n` : ''}Found ${items.length} matches${query ? ` for “${query}”` : ''}.`,
    buttonText:`Choose ${capability}`,
    footer:`Source: ${outcome.source.name}`,
    rows:items.slice(0,1000).map(item => ({
      title:item.title,
      description:item.description || 'Open episode list',
      id:`${prefix}${commandName} ~title ${outcome.source.id} ${token(item)}`,
    })),
  })
}

async function loadEpisodes(ctx, { capability, commandName, sourceId, series, note = '' }) {
  let resolvedSeries = { ...series }
  let identity = null
  if (capability === 'anime' && typeof ctx.resolveAniListTitles === 'function') {
    identity = await ctx.resolveAniListTitles(resolvedSeries.title, 'ANIME').catch(() => null)
    const match = identity?.matches?.[0]
    if (!resolvedSeries.anilistId && match?.id) resolvedSeries.anilistId = match.id
  }

  const outcome = await ctx.executeSource({
    capability,
    explicitSource:sourceId,
    payload:{ action:'episodes', itemId:resolvedSeries.id, item:resolvedSeries },
  })
  // Anime catalog providers can fail to load a title's episodes even when the
  // earlier search succeeded. Treat that the same as an empty episode list and
  // attempt alternate providers before reporting the primary source error.
  if (outcome.status !== 'ok' && capability !== 'anime') {
    return outcomeError(ctx, capability, outcome)
  }
  let result = outcome.status === 'ok' ? (outcome.result || {}) : {}
  let episodes = outcome.status === 'ok'
    ? (result.episodes || result.items || []).map(normalizeEpisode)
    : []
  let activeSourceId = sourceId
  let activeSeries = { ...resolvedSeries, title:String(result.title || resolvedSeries.title) }
  let activeNote = note

  // Some catalog results are valid title pages but have no playable episode links.
  // Don't strand the user on "No episodes found": search other installed anime
  // providers using the selected title and AniList aliases, then verify that a
  // candidate actually exposes episodes before switching the command session.
  if (capability === 'anime' && episodes.length === 0) {
    const queries = uniqueQueries([
      resolvedSeries.title,
      ...(identity?.aliases || []),
    ]).slice(0, 3)
    const alternatives = (typeof ctx.listSources === 'function' ? ctx.listSources('anime') : [])
      .filter(source => source?.id && source.id !== sourceId)
      .slice(0, 4)

    fallbackSearch:
    for (const alternative of alternatives) {
      for (const query of queries) {
        let searchOutcome
        try {
          searchOutcome = await ctx.executeSource({
            capability:'anime',
            explicitSource:alternative.id,
            payload:{ action:'search', query, originalQuery:resolvedSeries.title },
          })
        } catch {
          continue
        }
        if (searchOutcome?.status !== 'ok') continue
        const searchResult = searchOutcome.result || {}
        const targets = uniqueQueries([resolvedSeries.title, ...(identity?.aliases || [])])
          .filter(value => titleTokens(value).size >= 2)
        const candidates = (Array.isArray(searchResult.items) ? searchResult.items : searchResult.item ? [searchResult.item] : [])
          .map((candidate, index) => ({ candidate, index, score:titleMatchScore(candidate, targets) }))
          .sort((a, b) => b.score - a.score || a.index - b.index)
          .slice(0, 5)
          .map(row => row.candidate)
        for (const candidate of candidates) {
          let episodeOutcome
          try {
            episodeOutcome = await ctx.executeSource({
              capability:'anime',
              explicitSource:alternative.id,
              payload:{ action:'episodes', itemId:idOf(candidate), item:candidate },
            })
          } catch {
            continue
          }
          if (episodeOutcome?.status !== 'ok') continue
          const candidateResult = episodeOutcome.result || {}
          const candidateEpisodes = (candidateResult.episodes || candidateResult.items || []).map(normalizeEpisode)
          if (!candidateEpisodes.length) continue
          result = candidateResult
          episodes = candidateEpisodes
          activeSourceId = alternative.id
          activeSeries = {
            ...normalizeSeries(candidate),
            anilistId:resolvedSeries.anilistId,
            title:String(candidateResult.title || titleOf(candidate)),
          }
          activeNote = `⚠️ ${sourceName(sourceId, ctx)} had no playable episodes for that title; switched to ${alternative.name || alternative.id}.\n\n`
          break fallbackSearch
        }
      }
    }
  }

  if (!episodes.length && activeSourceId === sourceId && outcome.status !== 'ok') {
    return outcomeError(ctx, capability, outcome)
  }

  return showEpisodes(ctx, {
    capability,
    commandName,
    sourceId:activeSourceId,
    series:activeSeries,
    episodes,
    note:activeNote,
  })
}

function sourceName(sourceId, ctx) {
  return (typeof ctx.listSources === 'function' ? ctx.listSources('anime') : [])
    .find(source => source?.id === sourceId)?.name || sourceId || 'The selected source'
}

async function showEpisodes(ctx, { capability, commandName, sourceId, series, episodes, note = '' }) {
  if (!episodes.length) return ctx.reply(`No episodes found for ${series.title}.`)

  const prefix = ctx.publicPrefix || '.'
  let relationRows = []
  if (series.anilistId && typeof ctx.resolveAniListMedia === 'function') {
    const media = await ctx.resolveAniListMedia(series.anilistId, 'ANIME')
    relationRows = counterpartInstantRows(media, { fromType:'ANIME', prefix, max:1 })
      .filter(row => ['MANGA','ONE_SHOT'].includes(String(
        media?.relations?.find(edge => edge?.node?.id === row.mediaId)?.node?.format || ''
      )))
      .map(({ mediaId, mediaType, relationType, ...row }) => row)
  }

  const libraryAction = addToLibraryAction(ctx, 'anime', series, { prefix })
  const instantActions = [...relationRows, libraryAction].filter(Boolean)

  ctx.setCommandReplySession?.({
    kind:'number-selection',
    command:commandName,
    capability,
    sourceId,
    item:series,
    entries:episodes,
    unit:'episode',
    expiresAt:Date.now() + 30 * 60000,
  })

  const numbers = episodes.map(episode => Number(episode.number)).filter(Number.isFinite)
  const min = numbers.length ? Math.min(...numbers) : 1
  const max = numbers.length ? Math.max(...numbers) : episodes.length
  const status = libraryStatusLine(ctx, 'anime', series)
  const prompt = [
    `${note}${series.title} — ${episodes.length} episode${episodes.length === 1 ? '' : 's'}.`,
    status,
    '',
    'Reply with the episode number(s) you want.',
    'Examples: 1-10   •   1,3,4,7   •   1-10,13,15-18',
    `Available: ${min}–${max}`,
  ].filter((line, index, rows) => line !== '' || rows[index - 1] !== '').join('\n')

  if (instantActions.length && typeof ctx.replyInstant === 'function') {
    return ctx.replyInstant({
      title:series.title,
      text:prompt,
      footer:'Type the episode numbers directly in chat.',
      actions:instantActions,
    })
  }
  return ctx.reply(prompt)
}

async function fetchEpisodeList(ctx, capability, sourceId, series) {
  const outcome = await ctx.executeSource({
    capability,
    explicitSource:sourceId,
    payload:{ action:'episodes', itemId:series.id, item:series },
  })
  if (outcome.status !== 'ok') return { outcome, episodes:[] }
  return {
    outcome,
    episodes:(outcome.result?.episodes || outcome.result?.items || []).map(normalizeEpisode),
  }
}

async function showRangeStart(ctx, { capability, commandName, sourceId, series }) {
  const { outcome, episodes } = await fetchEpisodeList(ctx, capability, sourceId, series)
  if (outcome.status !== 'ok') return outcomeError(ctx, capability, outcome)
  const prefix = ctx.publicPrefix || '.'
  return ctx.replyList({
    title:`${series.title} — range`,
    text:'Choose the first episode.',
    buttonText:'Start episode',
    rows:episodes.slice(0,1000).map(episode => ({
      title:`Ep ${episode.number}`,
      description:episode.title,
      id:`${prefix}${commandName} ~range-start ${sourceId} ${token(series)} ${token(episode)}`,
    })),
  })
}

async function showRangeEnd(ctx, { capability, commandName, sourceId, series, start }) {
  const { outcome, episodes } = await fetchEpisodeList(ctx, capability, sourceId, series)
  if (outcome.status !== 'ok') return outcomeError(ctx, capability, outcome)
  const startIndex = Math.max(0, episodes.findIndex(ep => ep.id === start.id))
  const eligible = episodes.slice(startIndex)
  const prefix = ctx.publicPrefix || '.'
  return ctx.replyList({
    title:`${series.title} — range`,
    text:`Start: Episode ${start.number}. Choose the last episode.`,
    buttonText:'End episode',
    rows:eligible.slice(0,1000).map(episode => ({
      title:`Ep ${episode.number}`,
      description:episode.title,
      id:`${prefix}${commandName} ~range-end ${sourceId} ${token(series)} ${token(start)} ${token(episode)}`,
    })),
  })
}

function selectionIsContiguous(entries = []) {
  if (entries.length < 2) return true
  const numbers = entries.map(entry => Number(entry.number))
  if (numbers.some(value => !Number.isFinite(value))) return false
  for (let i = 1; i < numbers.length; i += 1) {
    if (numbers[i] !== numbers[i - 1] + 1) return false
  }
  return true
}

async function getSelectionOptions(ctx, { capability, sourceId, series, selection }) {
  const outcome = await ctx.executeSource({
    capability,
    explicitSource:sourceId,
    payload:{
      action:'options',
      itemId:series.id,
      item:series,
      selection,
    },
  })
  if (outcome.status !== 'ok') return { qualities:DEFAULT_QUALITIES, deliveries:DEFAULT_DELIVERIES }
  const result = outcome.result || {}
  return {
    qualities:Array.isArray(result.qualities) && result.qualities.length ? result.qualities.map(String) : DEFAULT_QUALITIES,
    deliveries:Array.isArray(result.deliveries) && result.deliveries.length ? result.deliveries.map(String) : DEFAULT_DELIVERIES,
  }
}

async function chooseSelectionOptions(ctx, { capability, commandName, sourceId, series, selection, spec }) {
  const options = await getSelectionOptions(ctx, { capability, sourceId, series, selection })
  ctx.setCommandReplySession?.({
    kind:'media-download-options',
    command:commandName,
    capability,
    sourceId,
    item:series,
    selected:selection,
    selectionSpec:spec,
    unit:'episode',
    expiresAt:Date.now() + 30 * 60000,
  })
  const prefix = ctx.publicPrefix || '.'
  return ctx.replyList({
    title:'Download options',
    text:`${series.title} — Episodes ${spec}`,
    buttonText:'Quality & delivery',
    footer:'Your number selection is locked in.',
    sections:options.deliveries.map(delivery => ({
      title:delivery === 'document' ? 'Document (no WhatsApp video compression)' : 'Video in chat',
      rows:options.qualities.map(quality => ({
        title:`${quality === 'source' ? 'Source quality' : quality + 'p'} • ${delivery === 'document' ? 'Document' : 'Video'}`,
        description:`Episodes ${spec}`,
        id:`${prefix}${commandName} ~selection-download ${quality} ${delivery}`,
      })),
    })),
  })
}

async function deliverEpisodeSelection(ctx, { capability, sourceId, series, selection, spec, quality, delivery }) {
  if (!selection.length) return ctx.reply('That episode selection is empty. Run the anime command again. ✦')

  if (selection.length === 1) {
    return deliver(ctx, {
      capability,
      sourceId,
      series,
      episode:selection[0],
      quality,
      delivery,
    })
  }

  if (selectionIsContiguous(selection)) {
    return deliver(ctx, {
      capability,
      sourceId,
      series,
      range:{ start:selection[0], end:selection.at(-1) },
      quality,
      delivery,
    })
  }

  return startMediaDelivery(ctx, {
    capability,
    sourceId,
    series,
    selection,
    spec,
    quality,
    delivery,
  })
}

async function handleNumberSelection(ctx, { capability, commandName }) {
  const session = ctx.getCommandReplySession?.()
  if (
    !session ||
    session.kind !== 'number-selection' ||
    session.command !== commandName ||
    !Array.isArray(session.entries)
  ) {
    return ctx.reply('That episode selection expired. Run the anime command again. ✦')
  }

  const spec = String(ctx.commandReplyInput || '').trim()
  const parsed = parseNumberSelection(spec, session.entries, {
    numberOf:episode => episode?.number,
    maxSelected:250,
  })
  if (!parsed.ok) {
    const reason = parsed.error === 'too-many'
      ? 'That selects too many episodes at once.'
      : 'I could not match those episode numbers.'
    return ctx.reply(`${reason}\n\nTry: 1-10 or 1,3,4,7 or 1-10,13,15-18`)
  }

  const saved = ctx.getDeliveryDefault(capability)
  if (saved) {
    ctx.clearCommandReplySession?.()
    return deliverEpisodeSelection(ctx, {
      capability,
      sourceId:session.sourceId,
      series:session.item,
      selection:parsed.selected,
      spec,
      quality:saved.quality,
      delivery:saved.delivery,
    })
  }

  return chooseSelectionOptions(ctx, {
    capability,
    commandName,
    sourceId:session.sourceId,
    series:session.item,
    selection:parsed.selected,
    spec,
  })
}

async function getOptions(ctx, { capability, sourceId, series, episode = null, range = null }) {
  const payload = episode
    ? { action:'options', itemId:series.id, episodeId:episode.id, item:series, episode }
    : { action:'options', itemId:series.id, item:series, range }
  const outcome = await ctx.executeSource({ capability, explicitSource:sourceId, payload })
  if (outcome.status !== 'ok') return { qualities:DEFAULT_QUALITIES, deliveries:DEFAULT_DELIVERIES }

  const result = outcome.result || {}
  const qualities = Array.isArray(result.qualities) && result.qualities.length ? result.qualities.map(String) : DEFAULT_QUALITIES
  const deliveries = Array.isArray(result.deliveries) && result.deliveries.length ? result.deliveries.map(String) : DEFAULT_DELIVERIES
  return { qualities, deliveries }
}

function optionSections(ctx, { capability, commandName, sourceId, series, episode = null, range = null, qualities, deliveries }) {
  const prefix = ctx.publicPrefix || '.'
  const action = episode ? '~download' : '~download-range'
  const base = episode
    ? `${prefix}${commandName} ${action} ${sourceId} ${token(series)} ${token(episode)}`
    : `${prefix}${commandName} ${action} ${sourceId} ${token(series)} ${token(range.start)} ${token(range.end)}`

  return deliveries.map(delivery => ({
    title:delivery === 'document' ? 'Document (no WhatsApp video compression)' : 'Video in chat',
    rows:qualities.map(quality => ({
      title:`${quality === 'source' ? 'Source quality' : quality + 'p'} • ${delivery === 'document' ? 'Document' : 'Video'}`,
      description:delivery === 'document' ? 'Send as a file' : 'Play inline in WhatsApp',
      id:`${base} ${quality} ${delivery}`,
    })),
  }))
}

async function chooseOptions(ctx, params) {
  const { capability, commandName } = params
  const options = await getOptions(ctx, params)
  const prefix = ctx.publicPrefix || '.'
  return ctx.replyList({
    title:'Download options',
    text:params.episode
      ? `${params.series.title} — Episode ${params.episode.number}`
      : `${params.series.title} — Episodes ${params.range.start.number}–${params.range.end.number}`,
    buttonText:'Quality & delivery',
    footer:`Save a default: ${prefix}delivery ${capability} 720 document`,
    sections:optionSections(ctx, { ...params, ...options }),
  })
}

function safeMediaError(error) {
  let message = String(error?.message || error || 'Unknown media delivery error')
    .replace(/https?:\/\/[^\s"'<>]+/gi, value => {
      try {
        const url = new URL(value)
        return url.origin + url.pathname
      } catch {
        return '[media URL]'
      }
    })
    .replace(/\s+/g, ' ')
    .trim()
  if (message.length > 200) message = message.slice(0, 197) + '...'
  return message
}

function deliveryOutcomeError(ctx, capability, outcome) {
  if (outcome?.status === 'no-sources') {
    return ctx.botProfile?.id === 'nami'
      ? namiNoSources(capability)
      : `No ${capability} sources are installed yet.`
  }

  if (outcome?.status === 'source-error') {
    const base = ctx.botProfile?.id === 'nami'
      ? namiSourceFailure(capability, outcome.source?.name || '')
      : `${outcome.source?.name || 'The source'} could not retrieve or send that ${capability} media.`
    const detail = safeMediaError(outcome.error)
    return detail ? `${base}\nReason: ${detail}` : base
  }

  if (outcome?.status === 'all-failed') {
    const base = ctx.botProfile?.id === 'nami'
      ? namiSourceFailure(capability, '', true)
      : `All configured ${capability} sources failed for that request.`
    const detail = safeMediaError(outcome.error)
    return detail ? `${base}\nReason: ${detail}` : base
  }

  return `Could not complete that ${capability} media request.`
}

function deliveryLabel(details) {
  if (details.episode) return `Episode ${details.episode.number}`
  if (details.range) return `Episodes ${details.range.start.number}–${details.range.end.number}`
  if (Array.isArray(details.selection)) return `${details.selection.length} selected episodes`
  return 'the selected episodes'
}

function mediaTitleScore(candidate, targets) {
  const candidateTokens = titleTokens(titleOf(candidate))
  if (!candidateTokens.size) return 0
  let best = 0
  for (const target of targets) {
    const targetTokens = titleTokens(target)
    if (!targetTokens.size) continue
    let common = 0
    for (const word of candidateTokens) if (targetTokens.has(word)) common += 1
    const union = new Set([...candidateTokens, ...targetTokens]).size
    const jaccard = union ? common / union : 0
    const targetCoverage = common / targetTokens.size
    const candidateCoverage = common / candidateTokens.size
    best = Math.max(best, jaccard, targetCoverage * 0.9, candidateCoverage * 0.65)
  }
  return best
}

function episodeWithNumber(episodes, wantedNumber) {
  const wanted = Number(wantedNumber)
  return episodes.find(row => {
    const number = Number(row?.number ?? row?.episode)
    return Number.isFinite(number) && number === wanted
  }) || null
}

// Episode IDs are provider-specific. If the selected catalog can no longer
// produce the media, search alternate providers, match the same title, then
// resolve that episode number again before attempting the send.
async function retryAnimeEpisodeDelivery(ctx, {
  capability, sourceId, series, episode, quality, delivery,
}) {
  if (capability !== 'anime' || !Array.isArray(ctx.listSources)) return null

  let identity = null
  if (typeof ctx.resolveAniListTitles === 'function') {
    try {
      identity = await ctx.resolveAniListTitles(series.title, 'ANIME')
    } catch {}
  }

  const targets = uniqueQueries([
    series.title,
    ...(Array.isArray(identity?.aliases) ? identity.aliases : []),
  ]).slice(0, 3)
  const sources = ctx.listSources('anime')
    .filter(source => source?.id && source.id !== sourceId)
    .slice(0, 3)

  for (const alternative of sources) {
    const candidatesById = new Map()
    for (const query of targets) {
      let searched
      try {
        searched = await ctx.executeSource({
          capability:'anime',
          explicitSource:alternative.id,
          payload:{ action:'search', query, originalQuery:series.title },
        })
      } catch {
        continue
      }
      if (searched?.status !== 'ok') continue
      const result = searched.result || {}
      const candidates = Array.isArray(result.items)
        ? result.items
        : result.item ? [result.item] : []
      for (const candidate of candidates) {
        const score = mediaTitleScore(candidate, targets)
        const id = idOf(candidate)
        if (!id) continue
        const previous = candidatesById.get(id)
        if (!previous || score > previous.score) candidatesById.set(id, { candidate, score })
      }
      if (candidatesById.size) break
    }

    const candidates = [...candidatesById.values()]
      .filter(row => row.score >= 0.35)
      .sort((a, b) => b.score - a.score)
      .slice(0, 2)

    for (const { candidate } of candidates) {
      let episodeList
      try {
        episodeList = await ctx.executeSource({
          capability:'anime',
          explicitSource:alternative.id,
          payload:{ action:'episodes', itemId:idOf(candidate), item:candidate },
        })
      } catch {
        continue
      }
      if (episodeList?.status !== 'ok') continue
      const result = episodeList.result || {}
      const rows = (result.episodes || result.items || []).map(normalizeEpisode)
      const matchedEpisode = episodeWithNumber(rows, episode.number)
      if (!matchedEpisode) continue

      let delivered
      try {
        delivered = await ctx.executeSource({
          capability:'anime',
          explicitSource:alternative.id,
          payload:{
            action:'download',
            itemId:idOf(candidate),
            episodeId:matchedEpisode.id,
            item:candidate,
            episode:matchedEpisode,
            quality,
            delivery,
          },
        })
      } catch {
        continue
      }

      if (delivered?.status === 'ok' && delivered.result?.delivered === true) {
        return {
          source:delivered.source || alternative,
          result:delivered.result,
          fallbackFrom:sourceName(sourceId, ctx),
        }
      }
    }
  }
  return null
}

async function performSingleEpisodeDelivery(ctx, {
  capability, sourceId, series, episode, quality, delivery,
}) {
  let outcome
  try {
    outcome = await ctx.executeSource({
      capability,
      explicitSource:sourceId,
      payload:{
        action:'download',
        itemId:series.id,
        episodeId:episode.id,
        item:series,
        episode,
        quality,
        delivery,
      },
    })
  } catch (error) {
    outcome = { status:'source-error', source:{ id:sourceId, name:sourceName(sourceId, ctx) }, error }
  }

  if (outcome.status === 'ok' && (outcome.result?.delivered === true || typeof outcome.result === 'string' || outcome.result?.text)) {
    return { ok:true, source:outcome.source, message:outcome.result?.delivered === true ? '' : String(outcome.result?.text || outcome.result) }
  }

  const fallback = await retryAnimeEpisodeDelivery(ctx, {
    capability, sourceId, series, episode, quality, delivery,
  })
  if (fallback) {
    return {
      ok:true,
      source:fallback.source,
      message:`✅ Fallback: ${fallback.fallbackFrom} could not deliver Episode ${episode.number}; ${fallback.source.name || fallback.source.id} was used. ✦`,
    }
  }

  if (outcome.status !== 'ok') {
    return { ok:false, message:deliveryOutcomeError(ctx, capability, outcome) }
  }
  return {
    ok:false,
    message:`⚠️ ${sourceName(sourceId, ctx)} finished the request for *${series.title}* but did not confirm media delivery. No file was confirmed as sent.`,
  }
}

async function performMediaDelivery(ctx, details) {
  const { capability, sourceId, series, episode = null, range = null, quality, delivery } = details

  if (Array.isArray(details.selection)) {
    let completed = 0
    for (const selectedEpisode of details.selection) {
      const episodeResult = await performSingleEpisodeDelivery(ctx, {
        capability,
        sourceId,
        series,
        episode:selectedEpisode,
        quality,
        delivery,
      })
      if (!episodeResult.ok) return episodeResult
      completed += 1
    }
    return {
      ok:true,
      message:`✅ Sent ${completed} selected episodes from *${series.title}* (${details.spec || completed}). ✦`,
    }
  }

  if (episode) {
    const episodeResult = await performSingleEpisodeDelivery(ctx, {
      capability,
      sourceId,
      series,
      episode,
      quality,
      delivery,
    })
    if (!episodeResult.ok) return episodeResult
    if (episodeResult.message) return { ...episodeResult, message:episodeResult.message }
    return {
      ok:true,
      message:`✅ *${series.title}* — Episode ${episode.number} was sent as ${delivery}. ✦`,
    }
  }

  const outcome = await ctx.executeSource({
    capability,
    explicitSource:sourceId,
    payload:{ action:'downloadRange', itemId:series.id, item:series, range, quality, delivery },
  })
  if (outcome.status !== 'ok') {
    return { ok:false, message:deliveryOutcomeError(ctx, capability, outcome) }
  }

  const result = outcome.result
  if (result?.delivered === true) {
    return {
      ok:true,
      message:episode
        ? `✅ *${series.title}* — Episode ${episode.number} was sent as ${delivery}. ✦`
        : `✅ *${series.title}* — Episodes ${range.start.number}–${range.end.number} were sent as ${delivery}. ✦`,
    }
  }
  if (typeof result === 'string') return { ok:true, message:result }
  if (result?.text) return { ok:true, message:String(result.text) }

  return {
    ok:false,
    message:`⚠️ ${sourceName(sourceId, ctx)} finished the request for *${series.title}* but did not confirm media delivery. No file was confirmed as sent.`,
  }
}

// Acknowledge first and run long episode retrieval outside the inbound command
// handler. A file may take several minutes to resolve, remux and upload; the
// progress message is updated only after Baileys confirms the send or an error.
async function startMediaDelivery(ctx, details) {
  const label = deliveryLabel(details)
  const opening = `⏳ Preparing *${details.series.title}* — ${label}. I'll send the media here when it's ready. ✦`

  // Keep the no-progress fallback for offline self-tests/alternate consumers.
  if (typeof ctx.progress !== 'function') {
    const result = await performMediaDelivery(ctx, details)
    return ctx.reply(result.message)
  }

  let progress = null
  try { progress = await ctx.progress(opening) } catch {}
  if (!progress) {
    try { await ctx.reply(opening) } catch {}
  }

  const report = async (message, failed = false) => {
    const update = failed ? progress?.fail : progress?.done
    if (typeof update === 'function') {
      try { await update.call(progress, message); return } catch {}
    }
    if (typeof progress?.update === 'function') {
      try { await progress.update(message); return } catch {}
    }
    try { await ctx.reply(message) } catch {}
  }

  setImmediate(() => {
    void (async () => {
      try {
        const result = await performMediaDelivery(ctx, details)
        await report(result.message, !result.ok)
      } catch (error) {
        const detail = safeMediaError(error)
        const message = `❌ *${details.series.title}* media delivery failed${detail ? `: ${detail}` : '.'}`
        await report(message, true)
      }
    })().catch(() => {})
  })
  return true
}

async function deliver(ctx, details) {
  return startMediaDelivery(ctx, details)
}

async function episodeSelected(ctx, params) {
  const saved = ctx.getDeliveryDefault(params.capability)
  if (saved) return deliver(ctx, { ...params, quality:saved.quality, delivery:saved.delivery })
  return chooseOptions(ctx, params)
}

export async function runSeriesCommand(ctx, { capability, commandName, args = [] } = {}) {
  const first = String(args[0] || '')

  try {
    if (first === '~library-add') {
      if (capability !== 'anime') return ctx.reply('That Library action is unavailable here.')
      const result = await addCanonicalLibraryItem(ctx, 'anime', args[1])
      if (!result.ok) return ctx.reply('I could not add that anime to Library. Run the anime search again.')
      return ctx.reply(result.added
        ? `Added *${result.item.title}* to Library.`
        : `*${result.item.title}* is already in Library.`)
    }

    if (first === '~numbers') {
      return handleNumberSelection(ctx, { capability, commandName })
    }

    if (first === '~selection-download') {
      const session = ctx.getCommandReplySession?.()
      if (
        !session ||
        session.kind !== 'media-download-options' ||
        session.command !== commandName ||
        !Array.isArray(session.selected)
      ) {
        return ctx.reply('That download selection expired. Run the anime command again. ✦')
      }
      ctx.clearCommandReplySession?.()
      return deliverEpisodeSelection(ctx, {
        capability,
        sourceId:session.sourceId,
        series:session.item,
        selection:session.selected,
        spec:session.selectionSpec || '',
        quality:String(args[1] || 'source'),
        delivery:String(args[2] || 'document'),
      })
    }

    if (first === '~anilist') {
      const media = typeof ctx.resolveAniListMedia === 'function'
        ? await ctx.resolveAniListMedia(Number(args[1]), 'ANIME')
        : null
      if (!media?.id) return ctx.reply('I could not resolve that anime anymore. Run the anime search again. ✦')
      return executeSearch(ctx, {
        capability,
        commandName,
        query:media.title || media.aliases?.[0] || '',
        sourceId:'',
      })
    }

    if (first === '~source') {
      const sourceId = String(args[1] || '')
      const query = untoken(args[2] || '')
      return executeSearch(ctx, { capability, commandName, query, sourceId })
    }

    if (first === '~title') {
      return loadEpisodes(ctx, {
        capability, commandName,
        sourceId:String(args[1] || ''),
        series:untoken(args[2]),
      })
    }

    if (first === '~episode') {
      return episodeSelected(ctx, {
        capability, commandName,
        sourceId:String(args[1] || ''),
        series:untoken(args[2]),
        episode:untoken(args[3]),
      })
    }

    if (first === '~range') {
      return showRangeStart(ctx, {
        capability, commandName,
        sourceId:String(args[1] || ''),
        series:untoken(args[2]),
      })
    }

    if (first === '~range-start') {
      return showRangeEnd(ctx, {
        capability, commandName,
        sourceId:String(args[1] || ''),
        series:untoken(args[2]),
        start:untoken(args[3]),
      })
    }

    if (first === '~range-end') {
      const params = {
        capability, commandName,
        sourceId:String(args[1] || ''),
        series:untoken(args[2]),
        range:{ start:untoken(args[3]), end:untoken(args[4]) },
      }
      const saved = ctx.getDeliveryDefault(capability)
      if (saved) return deliver(ctx, { ...params, quality:saved.quality, delivery:saved.delivery })
      return chooseOptions(ctx, params)
    }

    if (first === '~download') {
      return deliver(ctx, {
        capability,
        sourceId:String(args[1] || ''),
        series:untoken(args[2]),
        episode:untoken(args[3]),
        quality:String(args[4] || 'source'),
        delivery:String(args[5] || 'document'),
      })
    }

    if (first === '~download-range') {
      return deliver(ctx, {
        capability,
        sourceId:String(args[1] || ''),
        series:untoken(args[2]),
        range:{ start:untoken(args[3]), end:untoken(args[4]) },
        quality:String(args[5] || 'source'),
        delivery:String(args[6] || 'document'),
      })
    }
  } catch {
    return ctx.reply(ctx.botProfile?.id === 'nami'
      ? namiExpiredSelection()
      : 'That menu selection is invalid or expired. Run the command again.')
  }

  let sourceId = ''
  const clean = []
  for (let i = 0; i < args.length; i++) {
    const value = String(args[i] || '')
    if (value === '--source' && args[i + 1]) {
      sourceId = String(args[++i] || '').trim().toLowerCase()
      continue
    }
    if (value.startsWith('--source=')) {
      sourceId = value.slice(9).trim().toLowerCase()
      continue
    }
    clean.push(value)
  }

  return executeSearch(ctx, {
    capability,
    commandName,
    query:clean.join(' ').trim(),
    sourceId,
  })
}
