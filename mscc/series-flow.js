import {
  namiDownloadStarted,
  namiExpiredSelection,
  namiNoResults,
  namiNoSources,
  namiSourceFailure,
} from './response-pools.js'

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

async function executeSearch(ctx, { capability, commandName, query, sourceId = '' }) {
  const action = query ? 'search' : 'browse'
  const outcome = await ctx.executeSource({
    capability,
    explicitSource:sourceId,
    payload:{ action, query },
  })

  if (outcome.status === 'choice-required') return chooseSource(ctx, { capability, commandName, query })
  if (outcome.status !== 'ok') return outcomeError(ctx, capability, outcome)

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
    text:`${fallbackNote(outcome)}Found ${items.length} matches${query ? ` for “${query}”` : ''}.`,
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
  const outcome = await ctx.executeSource({
    capability,
    explicitSource:sourceId,
    payload:{ action:'episodes', itemId:series.id, item:series },
  })
  if (outcome.status !== 'ok') return outcomeError(ctx, capability, outcome)
  const result = outcome.result || {}
  const episodes = (result.episodes || result.items || []).map(normalizeEpisode)
  return showEpisodes(ctx, {
    capability,
    commandName,
    sourceId,
    series:{ ...series, title:String(result.title || series.title) },
    episodes,
    note,
  })
}

async function showEpisodes(ctx, { capability, commandName, sourceId, series, episodes, note = '' }) {
  if (!episodes.length) return ctx.reply(`No episodes found for ${series.title}.`)
  const prefix = ctx.publicPrefix || '.'
  const maxEpisodeRows = episodes.length >= 1000 ? 1000 : episodes.length
  const rows = episodes.slice(0,maxEpisodeRows).map(episode => ({
    title:`Ep ${episode.number}`,
    description:episode.title,
    id:`${prefix}${commandName} ~episode ${sourceId} ${token(series)} ${token(episode)}`,
  }))

  // Keep the verified 1000-row ceiling. When there is room, expose range
  // selection directly in the same list.
  if (rows.length < 1000) {
    rows.unshift({
      title:'📦 Download a range',
      description:'Choose a start episode, then an end episode',
      id:`${prefix}${commandName} ~range ${sourceId} ${token(series)}`,
    })
  }

  const saved = ctx.getDeliveryDefault(capability)
  const savedText = saved ? ` • default: ${saved.quality}/${saved.delivery}` : ''
  return ctx.replyList({
    title:series.title,
    text:`${note}${series.title} — ${episodes.length} episode${episodes.length === 1 ? '' : 's'}.`,
    buttonText:'Choose episode',
    footer:`Tap an episode to download${savedText}`,
    rows,
  })
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

async function deliver(ctx, { capability, sourceId, series, episode = null, range = null, quality, delivery }) {
  const outcome = await ctx.executeSource({
    capability,
    explicitSource:sourceId,
    payload:episode
      ? { action:'download', itemId:series.id, episodeId:episode.id, item:series, episode, quality, delivery }
      : { action:'downloadRange', itemId:series.id, item:series, range, quality, delivery },
  })
  if (outcome.status !== 'ok') return outcomeError(ctx, capability, outcome)
  const result = outcome.result
  if (result?.delivered === true) return true
  if (typeof result === 'string') return ctx.reply(result)
  if (result?.text) return ctx.reply(String(result.text))
  if (ctx.botProfile?.id === 'nami') {
    return ctx.reply(namiDownloadStarted({
      title:series.title,
      episode:episode?.number || '',
      range:range ? `${range.start.number}–${range.end.number}` : '',
      quality,
      delivery,
    }))
  }
  return ctx.reply(episode
    ? `Download started: ${series.title} — Episode ${episode.number} (${quality}, ${delivery}).`
    : `Range download started: ${series.title} — Episodes ${range.start.number}–${range.end.number} (${quality}, ${delivery}).`)
}

async function episodeSelected(ctx, params) {
  const saved = ctx.getDeliveryDefault(params.capability)
  if (saved) return deliver(ctx, { ...params, quality:saved.quality, delivery:saved.delivery })
  return chooseOptions(ctx, params)
}

export async function runSeriesCommand(ctx, { capability, commandName, args = [] } = {}) {
  const first = String(args[0] || '')

  try {
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
