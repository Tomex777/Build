const token = value => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
const untoken = value => JSON.parse(Buffer.from(String(value || ''), 'base64url').toString('utf8'))

const DEFAULT_QUALITIES = ['source','1080','720','480','360']
const DEFAULT_DELIVERIES = ['document','video']

const titleOf = item => String(item?.title || item?.name || 'Untitled')
const idOf = item => String(item?.id ?? item?.slug ?? item?.url ?? titleOf(item))

const normalizeMovie = item => ({
  id:idOf(item),
  title:titleOf(item),
  description:String(item?.description || item?.year || item?.status || '').trim(),
  tmdbId:Number(item?.tmdbId || 0) || 0,
})

function hasContent(result) {
  if (!result) return false
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

async function chooseSource(ctx, query) {
  const sources = ctx.listSources('movies')
  const prefix = ctx.publicPrefix || '.'
  if (!sources.length) return ctx.reply('No movie sources are installed yet.')
  return ctx.replyList({
    title:'Choose movie source',
    text:query ? `Choose a source for “${query}”.` : 'Choose a movie source.',
    buttonText:'Choose source',
    footer:`Save a default: ${prefix}source movies <source>`,
    rows:sources.map(source => ({
      title:source.name,
      description:source.description || 'Use for this request',
      id:`${prefix}movie ~source ${source.id} ${token(query || '')}`,
    })),
  })
}

function outcomeError(ctx, outcome) {
  if (outcome?.status === 'no-sources') return ctx.reply('No movie sources are installed yet.')
  if (outcome?.status === 'source-error') return ctx.reply(`${outcome.source?.name || 'The source'} could not complete that movie request.`)
  if (outcome?.status === 'all-failed') return ctx.reply('All configured movie sources failed for that request.')
  return ctx.reply('Could not complete that movie request.')
}

async function retryAliases(ctx, { query, sourceId, firstOutcome }) {
  if (!query || typeof ctx.resolveTmdbTitles !== 'function' || firstOutcome?.status !== 'ok' || hasContent(firstOutcome.result)) {
    return firstOutcome
  }
  const resolved = await ctx.resolveTmdbTitles(query, 'movie')
  const aliases = uniqueQueries([query, ...(resolved?.aliases || [])])
    .filter(value => value.toLocaleLowerCase() !== String(query).toLocaleLowerCase())
    .slice(0, 5)

  for (const alias of aliases) {
    const outcome = await ctx.executeSource({
      capability:'movies',
      explicitSource:sourceId || firstOutcome.source?.id || '',
      payload:{
        action:'search',
        query:alias,
        originalQuery:query,
        aliases:resolved?.aliases || [],
        tmdb:resolved?.matches?.[0] || null,
      },
    })
    if (outcome.status === 'ok' && hasContent(outcome.result)) {
      return { ...outcome, titleAliasUsed:alias, originalQuery:query, titleResolvedBy:'tmdb' }
    }
  }
  return firstOutcome
}

async function executeSearch(ctx, { query, sourceId = '' }) {
  let outcome = await ctx.executeSource({
    capability:'movies',
    explicitSource:sourceId,
    payload:{ action:query ? 'search' : 'browse', query },
  })

  if (outcome.status === 'choice-required') return chooseSource(ctx, query)
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)
  if (query) outcome = await retryAliases(ctx, { query, sourceId, firstOutcome:outcome })

  const result = outcome.result || {}
  const items = Array.isArray(result.items)
    ? result.items.map(normalizeMovie)
    : result.item
      ? [normalizeMovie(result.item)]
      : []

  if (!items.length) return ctx.reply(`No movie results found${query ? ` for “${query}”` : ''}.`)
  if (items.length === 1) return selectMovie(ctx, { sourceId:outcome.source.id, movie:items[0] })

  const prefix = ctx.publicPrefix || '.'
  return ctx.replyList({
    title:'Choose movie',
    text:`${outcome.titleAliasUsed ? `Matched “${query}” through “${outcome.titleAliasUsed}”.\n\n` : ''}Found ${items.length} matches${query ? ` for “${query}”` : ''}.`,
    buttonText:'Choose movie',
    footer:`Source: ${outcome.source.name}`,
    rows:items.slice(0, 1000).map(item => ({
      title:item.title,
      description:item.description || 'Open download options',
      id:`${prefix}movie ~title ${outcome.source.id} ${token(item)}`,
    })),
  })
}

async function withTmdbIdentity(ctx, movie) {
  if (movie.tmdbId || typeof ctx.resolveTmdbTitles !== 'function') return movie
  const found = await ctx.resolveTmdbTitles(movie.title, 'movie')
  const match = found?.matches?.[0]
  return match?.id ? { ...movie, tmdbId:match.id } : movie
}

async function getOptions(ctx, sourceId, movie) {
  const outcome = await ctx.executeSource({
    capability:'movies',
    explicitSource:sourceId,
    payload:{ action:'options', itemId:movie.id, item:movie },
  })
  if (outcome.status !== 'ok') return { qualities:DEFAULT_QUALITIES, deliveries:DEFAULT_DELIVERIES }
  const result = outcome.result || {}
  return {
    qualities:Array.isArray(result.qualities) && result.qualities.length ? result.qualities.map(String) : DEFAULT_QUALITIES,
    deliveries:Array.isArray(result.deliveries) && result.deliveries.length ? result.deliveries.map(String) : DEFAULT_DELIVERIES,
  }
}

async function deliver(ctx, { sourceId, movie, quality, delivery }) {
  const outcome = await ctx.executeSource({
    capability:'movies',
    explicitSource:sourceId,
    payload:{ action:'download', itemId:movie.id, item:movie, quality, delivery },
  })
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)
  const result = outcome.result
  if (result?.delivered === true) return true
  if (typeof result === 'string') return ctx.reply(result)
  if (result?.text) return ctx.reply(String(result.text))
  return ctx.reply(`Download started: ${movie.title} (${quality}, ${delivery}).`)
}

async function selectMovie(ctx, { sourceId, movie }) {
  const resolved = await withTmdbIdentity(ctx, movie)
  const saved = ctx.getDeliveryDefault('movies')
  if (saved) return deliver(ctx, { sourceId, movie:resolved, quality:saved.quality, delivery:saved.delivery })

  const options = await getOptions(ctx, sourceId, resolved)
  const prefix = ctx.publicPrefix || '.'
  return ctx.replyList({
    title:resolved.title,
    text:'Choose quality and delivery.',
    buttonText:'Download options',
    footer:`Save a default: ${prefix}delivery movies 720 document`,
    sections:options.deliveries.map(delivery => ({
      title:delivery === 'document' ? 'Document (no WhatsApp video compression)' : 'Video in chat',
      rows:options.qualities.map(quality => ({
        title:`${quality === 'source' ? 'Source quality' : quality + 'p'} • ${delivery === 'document' ? 'Document' : 'Video'}`,
        description:'Download movie',
        id:`${prefix}movie ~download ${sourceId} ${token(resolved)} ${quality} ${delivery}`,
      })),
    })),
  })
}

export async function runMovieCommand(ctx, { args = [] } = {}) {
  const first = String(args[0] || '')
  try {
    if (first === '~tmdb') {
      const media = typeof ctx.resolveTmdbMedia === 'function'
        ? await ctx.resolveTmdbMedia(Number(args[1]), 'movie')
        : null
      if (!media?.id) return ctx.reply('That movie selection expired. Run the movie search again.')
      return executeSearch(ctx, { query:media.title || media.aliases?.[0] || '' })
    }

    if (first === '~source') {
      return executeSearch(ctx, {
        sourceId:String(args[1] || ''),
        query:untoken(args[2] || ''),
      })
    }

    if (first === '~title') {
      return selectMovie(ctx, {
        sourceId:String(args[1] || ''),
        movie:untoken(args[2]),
      })
    }

    if (first === '~download') {
      return deliver(ctx, {
        sourceId:String(args[1] || ''),
        movie:untoken(args[2]),
        quality:String(args[3] || 'source'),
        delivery:String(args[4] || 'document'),
      })
    }
  } catch {
    return ctx.reply('That movie selection is invalid or expired. Run the movie command again.')
  }

  let sourceId = ''
  const clean = []
  for (let i = 0; i < args.length; i += 1) {
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

  return executeSearch(ctx, { query:clean.join(' ').trim(), sourceId })
}
