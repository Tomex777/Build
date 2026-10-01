import {
  namiDownloadStarted,
  namiExpiredSelection,
  namiNoResults,
  namiNoSources,
  namiSourceFailure,
} from './response-pools.js'
import { counterpartInstantRows } from './media-relations.js'

const token = value => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
const untoken = value => JSON.parse(Buffer.from(String(value || ''), 'base64url').toString('utf8'))

const DEFAULT_QUALITIES = ['source']
const DEFAULT_DELIVERIES = ['document']

const titleOf = item => String(item?.title || item?.name || 'Untitled')
const idOf = item => String(item?.id ?? item?.slug ?? item?.url ?? titleOf(item))

const normalizeManga = item => ({
  id:idOf(item),
  title:titleOf(item),
  description:String(item?.description || item?.year || item?.status || '').trim(),
  anilistId:Number(item?.anilistId || 0) || 0,
})

const normalizeChapter = (chapter, index) => ({
  id:String(chapter?.id ?? chapter?.number ?? chapter?.chapter ?? index + 1),
  number:String(chapter?.number ?? chapter?.chapter ?? index + 1),
  title:String(chapter?.title || chapter?.name || `Chapter ${chapter?.number ?? chapter?.chapter ?? index + 1}`),
})

function outcomeError(ctx, outcome) {
  const nami = ctx.botProfile?.id === 'nami'
  if (outcome?.status === 'source-error') {
    return ctx.reply(nami
      ? namiSourceFailure('manga', outcome.source?.name || '')
      : `${outcome.source?.name || 'The source'} could not complete that manga request.`)
  }
  if (outcome?.status === 'all-failed') {
    return ctx.reply(nami
      ? namiSourceFailure('manga', '', true)
      : 'All configured manga sources failed for that request.')
  }
  if (outcome?.status === 'no-sources') {
    return ctx.reply(nami ? namiNoSources('manga') : 'No manga sources are installed yet.')
  }
  return ctx.reply('Could not complete that manga request.')
}

function fallbackNote(outcome) {
  return outcome?.fallback
    ? `⚠️ Fallback: ${outcome.fallbackFrom?.name || 'your default source'} was unavailable, so ${outcome.source?.name || 'another source'} is being used.\n\n`
    : ''
}

function searchResultHasContent(result) {
  if (!result) return false
  if (Array.isArray(result.chapters) && result.chapters.length) return true
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

async function resolveMangaIdentity(ctx, query, explicitId = 0) {
  if (explicitId && typeof ctx.resolveAniListMedia === 'function') {
    return ctx.resolveAniListMedia(explicitId, 'MANGA')
  }
  if (!query || typeof ctx.resolveAniListTitles !== 'function') return null
  const found = await ctx.resolveAniListTitles(query, 'MANGA')
  const id = found?.matches?.[0]?.id
  if (!id || typeof ctx.resolveAniListMedia !== 'function') return found?.matches?.[0] || null
  return ctx.resolveAniListMedia(id, 'MANGA')
}

async function chooseSource(ctx, { query }) {
  const sources = ctx.listSources('manga')
  const prefix = ctx.publicPrefix || '.'
  if (!sources.length) return outcomeError(ctx, { status:'no-sources' })

  return ctx.replyList({
    title:'Choose manga source',
    text:query ? `Choose a source for “${query}”.` : 'Choose a manga source.',
    buttonText:'Choose source',
    footer:`Save a default: ${prefix}source manga <source>`,
    rows:sources.map(source => ({
      title:source.name,
      description:source.description || 'Use for this request',
      id:`${prefix}manga ~source ${source.id} ${token(query || '')}`,
    })),
  })
}

async function retryMangaAliases(ctx, { query, sourceId, firstOutcome }) {
  if (
    !query ||
    typeof ctx.resolveAniListTitles !== 'function' ||
    firstOutcome?.status !== 'ok' ||
    searchResultHasContent(firstOutcome.result)
  ) return firstOutcome

  const resolved = await ctx.resolveAniListTitles(query, 'MANGA')
  const aliases = uniqueQueries([query, ...(resolved?.aliases || [])])
    .filter(value => value.toLocaleLowerCase() !== String(query).toLocaleLowerCase())
    .slice(0, 5)

  for (const alias of aliases) {
    const outcome = await ctx.executeSource({
      capability:'manga',
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

async function executeSearch(ctx, { query, sourceId = '' }) {
  const action = query ? 'search' : 'browse'
  let outcome = await ctx.executeSource({
    capability:'manga',
    explicitSource:sourceId,
    payload:{ action, query },
  })

  if (outcome.status === 'choice-required') return chooseSource(ctx, { query })
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)

  if (action === 'search') {
    outcome = await retryMangaAliases(ctx, { query, sourceId, firstOutcome:outcome })
  }

  const result = outcome.result || {}
  if (Array.isArray(result.chapters)) {
    const manga = normalizeManga(result)
    return showChapters(ctx, {
      sourceId:outcome.source.id,
      manga,
      chapters:result.chapters,
      note:fallbackNote(outcome),
    })
  }

  const items = Array.isArray(result.items)
    ? result.items.map(normalizeManga)
    : result.item
      ? [normalizeManga(result.item)]
      : []

  if (!items.length) {
    return ctx.reply(ctx.botProfile?.id === 'nami'
      ? namiNoResults('manga', query)
      : `No manga results found${query ? ` for “${query}”` : ''}.`)
  }

  if (items.length === 1) {
    return loadChapters(ctx, {
      sourceId:outcome.source.id,
      manga:items[0],
      note:fallbackNote(outcome),
    })
  }

  const prefix = ctx.publicPrefix || '.'
  return ctx.replyList({
    title:'Choose manga',
    text:`${fallbackNote(outcome)}${outcome.titleAliasUsed ? `Matched “${query}” through “${outcome.titleAliasUsed}”.\n\n` : ''}Found ${items.length} matches${query ? ` for “${query}”` : ''}.`,
    buttonText:'Choose manga',
    footer:`Source: ${outcome.source.name}`,
    rows:items.slice(0, 1000).map(item => ({
      title:item.title,
      description:item.description || 'Open chapter list',
      id:`${prefix}manga ~title ${outcome.source.id} ${token(item)}`,
    })),
  })
}

async function loadChapters(ctx, { sourceId, manga, note = '' }) {
  let resolvedManga = { ...manga }
  if (!resolvedManga.anilistId && typeof ctx.resolveAniListTitles === 'function') {
    const identity = await ctx.resolveAniListTitles(resolvedManga.title, 'MANGA')
    const match = identity?.matches?.[0]
    if (match?.id) resolvedManga.anilistId = match.id
  }

  const outcome = await ctx.executeSource({
    capability:'manga',
    explicitSource:sourceId,
    payload:{ action:'chapters', itemId:resolvedManga.id, item:resolvedManga },
  })
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)

  const result = outcome.result || {}
  const chapters = (result.chapters || result.items || []).map(normalizeChapter)
  return showChapters(ctx, {
    sourceId,
    manga:{ ...resolvedManga, title:String(result.title || resolvedManga.title) },
    chapters,
    note,
  })
}

async function animeRelationRows(ctx, manga) {
  if (!manga.anilistId || typeof ctx.resolveAniListMedia !== 'function') return []
  const media = await ctx.resolveAniListMedia(manga.anilistId, 'MANGA')
  return counterpartInstantRows(media, {
    fromType:'MANGA',
    prefix:ctx.publicPrefix || '.',
    max:3,
  }).map(({ mediaId, mediaType, relationType, ...row }) => row)
}

async function showChapters(ctx, { sourceId, manga, chapters, note = '' }) {
  if (!chapters.length) return ctx.reply(`No chapters found for ${manga.title}.`)

  const prefix = ctx.publicPrefix || '.'
  const relationRows = await animeRelationRows(ctx, manga)
  const reserved = relationRows.length + 1
  const maxChapterRows = Math.max(0, Math.min(chapters.length, 1000 - reserved))

  const rows = chapters.slice(0, maxChapterRows).map(chapter => ({
    title:`Ch ${chapter.number}`,
    description:chapter.title,
    id:`${prefix}manga ~chapter ${sourceId} ${token(manga)} ${token(chapter)}`,
  }))

  rows.unshift({
    title:'📦 Download a range',
    description:'Choose a start chapter, then an end chapter',
    id:`${prefix}manga ~range ${sourceId} ${token(manga)}`,
  })
  if (relationRows.length) rows.unshift(...relationRows)

  const saved = ctx.getDeliveryDefault('manga')
  const savedText = saved ? ` • default: ${saved.quality}/${saved.delivery}` : ''
  return ctx.replyList({
    title:manga.title,
    text:`${note}${manga.title} — ${chapters.length} chapter${chapters.length === 1 ? '' : 's'}.`,
    buttonText:'Choose chapter',
    footer:`Tap a chapter to download${savedText}`,
    rows,
  })
}

async function fetchChapterList(ctx, sourceId, manga) {
  const outcome = await ctx.executeSource({
    capability:'manga',
    explicitSource:sourceId,
    payload:{ action:'chapters', itemId:manga.id, item:manga },
  })
  if (outcome.status !== 'ok') return { outcome, chapters:[] }
  return {
    outcome,
    chapters:(outcome.result?.chapters || outcome.result?.items || []).map(normalizeChapter),
  }
}

async function showRangeStart(ctx, { sourceId, manga }) {
  const { outcome, chapters } = await fetchChapterList(ctx, sourceId, manga)
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)

  const prefix = ctx.publicPrefix || '.'
  return ctx.replyList({
    title:`${manga.title} — range`,
    text:'Choose the first chapter.',
    buttonText:'Start chapter',
    rows:chapters.slice(0,1000).map(chapter => ({
      title:`Ch ${chapter.number}`,
      description:chapter.title,
      id:`${prefix}manga ~range-start ${sourceId} ${token(manga)} ${token(chapter)}`,
    })),
  })
}

async function showRangeEnd(ctx, { sourceId, manga, start }) {
  const { outcome, chapters } = await fetchChapterList(ctx, sourceId, manga)
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)

  const startIndex = Math.max(0, chapters.findIndex(chapter => chapter.id === start.id))
  const eligible = chapters.slice(startIndex)
  const prefix = ctx.publicPrefix || '.'
  return ctx.replyList({
    title:`${manga.title} — range`,
    text:`Start: Chapter ${start.number}. Choose the last chapter.`,
    buttonText:'End chapter',
    rows:eligible.slice(0,1000).map(chapter => ({
      title:`Ch ${chapter.number}`,
      description:chapter.title,
      id:`${prefix}manga ~range-end ${sourceId} ${token(manga)} ${token(start)} ${token(chapter)}`,
    })),
  })
}

async function getOptions(ctx, { sourceId, manga, chapter = null, range = null }) {
  const payload = chapter
    ? { action:'options', itemId:manga.id, chapterId:chapter.id, item:manga, chapter }
    : { action:'options', itemId:manga.id, item:manga, range }

  const outcome = await ctx.executeSource({
    capability:'manga',
    explicitSource:sourceId,
    payload,
  })

  if (outcome.status !== 'ok') {
    return { qualities:DEFAULT_QUALITIES, deliveries:DEFAULT_DELIVERIES }
  }

  const result = outcome.result || {}
  return {
    qualities:Array.isArray(result.qualities) && result.qualities.length
      ? result.qualities.map(String)
      : DEFAULT_QUALITIES,
    deliveries:Array.isArray(result.deliveries) && result.deliveries.length
      ? result.deliveries.map(String)
      : DEFAULT_DELIVERIES,
  }
}

function optionSections(ctx, { sourceId, manga, chapter = null, range = null, qualities, deliveries }) {
  const prefix = ctx.publicPrefix || '.'
  const action = chapter ? '~download' : '~download-range'
  const base = chapter
    ? `${prefix}manga ${action} ${sourceId} ${token(manga)} ${token(chapter)}`
    : `${prefix}manga ${action} ${sourceId} ${token(manga)} ${token(range.start)} ${token(range.end)}`

  return deliveries.map(delivery => ({
    title:String(delivery).toLowerCase() === 'document' ? 'Document' : String(delivery),
    rows:qualities.map(quality => ({
      title:`${String(quality).toLowerCase() === 'source' ? 'Source quality' : quality} • ${delivery}`,
      description:chapter ? `Download chapter ${chapter.number}` : `Download chapters ${range.start.number}–${range.end.number}`,
      id:`${base} ${quality} ${delivery}`,
    })),
  }))
}

async function chooseOptions(ctx, params) {
  const options = await getOptions(ctx, params)
  const prefix = ctx.publicPrefix || '.'
  return ctx.replyList({
    title:'Download options',
    text:params.chapter
      ? `${params.manga.title} — Chapter ${params.chapter.number}`
      : `${params.manga.title} — Chapters ${params.range.start.number}–${params.range.end.number}`,
    buttonText:'Format & delivery',
    footer:`Save a default: ${prefix}delivery manga source document`,
    sections:optionSections(ctx, { ...params, ...options }),
  })
}

async function deliver(ctx, {
  sourceId,
  manga,
  chapter = null,
  range = null,
  quality = 'source',
  delivery = 'document',
}) {
  const outcome = await ctx.executeSource({
    capability:'manga',
    explicitSource:sourceId,
    payload:chapter
      ? {
          action:'download',
          itemId:manga.id,
          chapterId:chapter.id,
          item:manga,
          chapter,
          quality,
          delivery,
        }
      : {
          action:'downloadRange',
          itemId:manga.id,
          item:manga,
          range,
          quality,
          delivery,
        },
  })
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)

  const result = outcome.result
  if (result?.delivered === true) return true
  if (typeof result === 'string') return ctx.reply(result)
  if (result?.text) return ctx.reply(String(result.text))

  if (ctx.botProfile?.id === 'nami') {
    return ctx.reply(namiDownloadStarted({
      title:manga.title,
      episode:chapter ? chapter.number : '',
      range:range ? `${range.start.number}–${range.end.number}` : '',
      quality,
      delivery,
      unit:'Chapter',
    }))
  }

  return ctx.reply(chapter
    ? `Download started: ${manga.title} — Chapter ${chapter.number} (${quality}, ${delivery}).`
    : `Range download started: ${manga.title} — Chapters ${range.start.number}–${range.end.number} (${quality}, ${delivery}).`)
}

async function chapterSelected(ctx, params) {
  const saved = ctx.getDeliveryDefault('manga')
  if (saved) return deliver(ctx, { ...params, quality:saved.quality, delivery:saved.delivery })
  return chooseOptions(ctx, params)
}

export async function runMangaCommand(ctx, { args = [] } = {}) {
  const first = String(args[0] || '')

  try {
    if (first === '~anilist') {
      const media = await resolveMangaIdentity(ctx, '', Number(args[1]))
      if (!media?.id) {
        return ctx.reply('I could not resolve that manga anymore. Run the manga search again. ✦')
      }
      return executeSearch(ctx, {
        query:media.title || media.aliases?.[0] || '',
        sourceId:'',
      })
    }

    if (first === '~source') {
      return executeSearch(ctx, {
        sourceId:String(args[1] || ''),
        query:untoken(args[2] || ''),
      })
    }

    if (first === '~title') {
      return loadChapters(ctx, {
        sourceId:String(args[1] || ''),
        manga:untoken(args[2]),
      })
    }

    if (first === '~chapter') {
      return chapterSelected(ctx, {
        sourceId:String(args[1] || ''),
        manga:untoken(args[2]),
        chapter:untoken(args[3]),
      })
    }

    if (first === '~range') {
      return showRangeStart(ctx, {
        sourceId:String(args[1] || ''),
        manga:untoken(args[2]),
      })
    }

    if (first === '~range-start') {
      return showRangeEnd(ctx, {
        sourceId:String(args[1] || ''),
        manga:untoken(args[2]),
        start:untoken(args[3]),
      })
    }

    if (first === '~range-end') {
      const params = {
        sourceId:String(args[1] || ''),
        manga:untoken(args[2]),
        range:{ start:untoken(args[3]), end:untoken(args[4]) },
      }
      const saved = ctx.getDeliveryDefault('manga')
      if (saved) return deliver(ctx, { ...params, quality:saved.quality, delivery:saved.delivery })
      return chooseOptions(ctx, params)
    }

    if (first === '~download') {
      return deliver(ctx, {
        sourceId:String(args[1] || ''),
        manga:untoken(args[2]),
        chapter:untoken(args[3]),
        quality:String(args[4] || 'source'),
        delivery:String(args[5] || 'document'),
      })
    }

    if (first === '~download-range') {
      return deliver(ctx, {
        sourceId:String(args[1] || ''),
        manga:untoken(args[2]),
        range:{ start:untoken(args[3]), end:untoken(args[4]) },
        quality:String(args[5] || 'source'),
        delivery:String(args[6] || 'document'),
      })
    }
  } catch {
    return ctx.reply(ctx.botProfile?.id === 'nami'
      ? namiExpiredSelection()
      : 'That manga selection is invalid or expired. Run the command again.')
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

  return executeSearch(ctx, {
    query:clean.join(' ').trim(),
    sourceId,
  })
}
