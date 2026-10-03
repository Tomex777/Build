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

const DEFAULT_QUALITIES = ['source']
const DEFAULT_DELIVERIES = ['document']

const titleOf = item => String(item?.title || item?.name || 'Untitled')
const idOf = item => String(item?.id ?? item?.slug ?? item?.url ?? titleOf(item))

const normalizeManga = item => ({
  id:idOf(item),
  title:titleOf(item),
  description:String(item?.description || item?.year || item?.status || '').trim(),
  cover:String(
    item?.cover ||
    item?.coverImage ||
    item?.coverUrl ||
    item?.image ||
    item?.imageUrl ||
    item?.thumbnail ||
    item?.poster ||
    ''
  ).trim(),
  anilistId:Number(item?.anilistId || 0) || 0,
})

const normalizeChapter = (chapter, index) => ({
  id:String(chapter?.id ?? chapter?.number ?? chapter?.chapter ?? index + 1),
  number:String(chapter?.number ?? chapter?.chapter ?? index + 1),
  title:String(chapter?.title || chapter?.name || `Chapter ${chapter?.number ?? chapter?.chapter ?? index + 1}`),
})

function plainChoiceText(title, text, entries = []) {
  const lines = [title, '', text, '']
  entries.forEach((entry, index) => {
    lines.push(`${index + 1}. ${entry.title}${entry.description ? ' — ' + entry.description : ''}`)
  })
  lines.push('', 'Reply with the number or the option name.')
  return lines.join('\n')
}

function beginChoice(ctx, { choiceKind, entries, extra = {} }) {
  ctx.setCommandReplySession?.({
    kind:'choice-selection',
    command:'manga',
    capability:'manga',
    choiceKind,
    entries,
    expiresAt:Date.now() + 30 * 60000,
    ...extra,
  })
}

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
  const entries = sources.map(source => ({ id:source.id, title:source.name, description:source.description || 'Use for this request' }))
  beginChoice(ctx, { choiceKind:'source', entries, extra:{ query:query || '' } })
  return ctx.reply(plainChoiceText(
    'Manga source',
    query ? `Choose a source for “${query}”.\n\nSave a default with ${prefix}source manga <source>.` : 'Choose a manga source.',
    entries,
  ))
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

  const entries = items.slice(0, 100).map(item => ({
    item,
    title:item.title,
    description:item.description || 'Open chapter list',
  }))
  beginChoice(ctx, { choiceKind:'title', entries, extra:{ sourceId:outcome.source.id } })
  return ctx.reply(plainChoiceText(
    'Manga results',
    `${fallbackNote(outcome)}${outcome.titleAliasUsed ? `Matched “${query}” through “${outcome.titleAliasUsed}”.\n\n` : ''}Found ${items.length} matches${query ? ` for “${query}”` : ''}. Source: ${outcome.source.name}`,
    entries,
  ))
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
    max:1,
  }).map(({ mediaId, mediaType, relationType, ...row }) => row)
}

async function showChapters(ctx, { sourceId, manga, chapters, note = '' }) {
  if (!chapters.length) return ctx.reply(`No chapters found for ${manga.title}.`)

  const prefix = ctx.publicPrefix || '.'
  const relationRows = await animeRelationRows(ctx, manga)
  const libraryAction = addToLibraryAction(ctx, 'manga', manga, { prefix })
  const instantActions = [...relationRows, libraryAction].filter(Boolean)

  ctx.setCommandReplySession?.({
    kind:'number-selection',
    command:'manga',
    capability:'manga',
    sourceId,
    item:manga,
    entries:chapters,
    unit:'chapter',
    expiresAt:Date.now() + 30 * 60000,
  })

  const numbers = chapters.map(chapter => Number(chapter.number)).filter(Number.isFinite)
  const min = numbers.length ? Math.min(...numbers) : 1
  const max = numbers.length ? Math.max(...numbers) : chapters.length
  const status = libraryStatusLine(ctx, 'manga', manga)

  if (manga.cover && typeof ctx.sendImageUrl === 'function') {
    try {
      await ctx.sendImageUrl(manga.cover, [
        `*${manga.title}*`,
        status || '',
        `Chapters: ${chapters.length}`,
      ].filter(Boolean).join('\n'))
    } catch {}
  }

  const prompt = [
    `${note}${manga.title} — ${chapters.length} chapter${chapters.length === 1 ? '' : 's'}.`,
    status,
    '',
    'Reply with the chapter number(s) you want.',
    'Examples: 1-10   •   1,3,4,7   •   1-10,13,15-18',
    `Available: ${min}–${max}`,
  ].filter((line, index, rows) => line !== '' || rows[index - 1] !== '').join('\n')

  return ctx.reply(instantActions.length
    ? [prompt, '', relationRows.length ? 'Anime adaptation: ask .anime ' + manga.title : '', libraryAction ? 'Library: use the Library command to save this title.' : ''].filter(Boolean).join('\n')
    : prompt)
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

function selectionIsContiguous(entries = []) {
  if (entries.length < 2) return true
  const numbers = entries.map(entry => Number(entry.number))
  if (numbers.some(value => !Number.isFinite(value))) return false
  for (let i = 1; i < numbers.length; i += 1) {
    if (numbers[i] !== numbers[i - 1] + 1) return false
  }
  return true
}

async function getSelectionOptions(ctx, { sourceId, manga, selection }) {
  const outcome = await ctx.executeSource({
    capability:'manga',
    explicitSource:sourceId,
    payload:{
      action:'options',
      itemId:manga.id,
      item:manga,
      selection,
    },
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

async function chooseSelectionOptions(ctx, { sourceId, manga, selection, spec }) {
  const options = await getSelectionOptions(ctx, { sourceId, manga, selection })
  ctx.setCommandReplySession?.({
    kind:'media-download-options',
    command:'manga',
    capability:'manga',
    sourceId,
    item:manga,
    selected:selection,
    selectionSpec:spec,
    unit:'chapter',
    expiresAt:Date.now() + 30 * 60000,
  })

  const entries = options.deliveries.flatMap(delivery => options.qualities.map(quality => ({
    quality,
    delivery,
    title:`${String(quality).toLowerCase() === 'source' ? 'Source quality' : quality} • ${delivery}`,
    description:`Chapters ${spec}`,
  })))
  beginChoice(ctx, { choiceKind:'selection-download', entries, extra:{ sourceId, item:manga, selected:selection, selectionSpec:spec } })
  return ctx.reply(plainChoiceText('Download options', `${manga.title} — Chapters ${spec}`, entries))
}

async function deliverChapterSelection(ctx, { sourceId, manga, selection, spec, quality, delivery }) {
  if (!selection.length) {
    return ctx.reply('That chapter selection is empty. Run the manga command again. ✦')
  }

  if (selection.length === 1) {
    return deliver(ctx, {
      sourceId,
      manga,
      chapter:selection[0],
      quality,
      delivery,
    })
  }

  if (selectionIsContiguous(selection)) {
    return deliver(ctx, {
      sourceId,
      manga,
      range:{ start:selection[0], end:selection.at(-1) },
      quality,
      delivery,
    })
  }

  let completed = 0
  for (const chapter of selection) {
    const outcome = await ctx.executeSource({
      capability:'manga',
      explicitSource:sourceId,
      payload:{
        action:'download',
        itemId:manga.id,
        chapterId:chapter.id,
        item:manga,
        chapter,
        quality,
        delivery,
      },
    })
    if (outcome.status !== 'ok') return outcomeError(ctx, outcome)
    completed += 1
  }

  return ctx.reply(`Started ${completed} selected chapters from *${manga.title}* (${spec}). ✦`)
}

async function handleNumberSelection(ctx) {
  const session = ctx.getCommandReplySession?.()
  if (
    !session ||
    session.kind !== 'number-selection' ||
    session.command !== 'manga' ||
    !Array.isArray(session.entries)
  ) {
    return ctx.reply('That chapter selection expired. Run the manga command again. ✦')
  }

  const spec = String(ctx.commandReplyInput || '').trim()
  const parsed = parseNumberSelection(spec, session.entries, {
    numberOf:chapter => chapter?.number,
    maxSelected:250,
  })

  if (!parsed.ok) {
    const reason = parsed.error === 'too-many'
      ? 'That selects too many chapters at once.'
      : 'I could not match those chapter numbers.'
    return ctx.reply(`${reason}\n\nTry: 1-10 or 1,3,4,7 or 1-10,13,15-18`)
  }

  const saved = ctx.getDeliveryDefault('manga')
  if (saved) {
    ctx.clearCommandReplySession?.()
    return deliverChapterSelection(ctx, {
      sourceId:session.sourceId,
      manga:session.item,
      selection:parsed.selected,
      spec,
      quality:saved.quality,
      delivery:saved.delivery,
    })
  }

  return chooseSelectionOptions(ctx, {
    sourceId:session.sourceId,
    manga:session.item,
    selection:parsed.selected,
    spec,
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
  const entries = options.deliveries.flatMap(delivery => options.qualities.map(quality => ({
    quality,
    delivery,
    title:`${String(quality).toLowerCase() === 'source' ? 'Source quality' : quality} • ${delivery}`,
    description:params.chapter ? `Download chapter ${params.chapter.number}` : `Download chapters ${params.range.start.number}–${params.range.end.number}`,
  })))
  beginChoice(ctx, {
    choiceKind:params.chapter ? 'chapter-download' : 'range-download',
    entries,
    extra:{ sourceId:params.sourceId, manga:params.manga, chapter:params.chapter || null, range:params.range || null },
  })
  return ctx.reply(plainChoiceText('Download options', params.chapter
    ? `${params.manga.title} — Chapter ${params.chapter.number}`
    : `${params.manga.title} — Chapters ${params.range.start.number}–${params.range.end.number}`, entries))
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

async function handleChoiceSelection(ctx) {
  const session = ctx.getCommandReplySession?.()
  if (!session || session.kind !== 'choice-selection' || session.command !== 'manga') return ctx.reply('That selection expired. Run the manga command again.')
  const input = String(ctx.commandReplyInput || '').trim()
  let index = /^\d+$/.test(input) ? Number(input) - 1 : -1
  if (index < 0) {
    const wanted = input.toLocaleLowerCase()
    index = session.entries.findIndex(entry => String(entry.title || '').trim().toLocaleLowerCase() === wanted || String(entry.id || '').trim().toLocaleLowerCase() === wanted)
  }
  const entry = session.entries[index]
  if (!entry) return ctx.reply('I could not match that choice. Reply with one of the displayed numbers.')
  ctx.clearCommandReplySession?.()
  if (session.choiceKind === 'source') return executeSearch(ctx, { sourceId:String(entry.id || ''), query:String(session.query || '') })
  if (session.choiceKind === 'title') return loadChapters(ctx, { sourceId:String(session.sourceId || ''), manga:entry.item || entry })
  if (session.choiceKind === 'selection-download') return deliverChapterSelection(ctx, { sourceId:String(session.sourceId || ''), manga:session.item, selection:session.selected || [], spec:String(session.selectionSpec || ''), quality:String(entry.quality || 'source'), delivery:String(entry.delivery || 'document') })
  if (session.choiceKind === 'chapter-download') return deliver(ctx, { sourceId:String(session.sourceId || ''), manga:session.manga, chapter:session.chapter, quality:String(entry.quality || 'source'), delivery:String(entry.delivery || 'document') })
  if (session.choiceKind === 'range-download') return deliver(ctx, { sourceId:String(session.sourceId || ''), manga:session.manga, range:session.range, quality:String(entry.quality || 'source'), delivery:String(entry.delivery || 'document') })
  return ctx.reply('That selection is no longer available.')
}

export async function runChapterShortcut(ctx, {
  query = '',
  chapterNumber = '',
} = {}) {
  const term = String(query || '').trim()
  const wanted = String(chapterNumber || '').trim()
  if (!term || !/^\d+(?:\.\d+)?$/.test(wanted)) {
    return ctx.reply('Use .chapter <manga title> <chapter number>.')
  }

  try {
    const sources = ctx.listSources('manga')
    if (!sources.length) return outcomeError(ctx, { status:'no-sources' })

    const mode = typeof ctx.sourceMode === 'function' ? ctx.sourceMode('manga') : ''
    const explicitSource = mode === 'user-choice'
      ? (ctx.getSourceDefault?.('manga') || sources[0]?.id || '')
      : ''

    let outcome = await ctx.executeSource({
      capability:'manga',
      explicitSource,
      payload:{ action:'search', query:term },
    })
    if (outcome.status !== 'ok') return outcomeError(ctx, outcome)

    outcome = await retryMangaAliases(ctx, {
      query:term,
      sourceId:explicitSource,
      firstOutcome:outcome,
    })

    const result = outcome.result || {}
    let manga = null
    let chapters = []

    if (Array.isArray(result.chapters)) {
      manga = normalizeManga(result)
      chapters = result.chapters.map(normalizeChapter)
    } else {
      const items = Array.isArray(result.items)
        ? result.items.map(normalizeManga)
        : result.item
          ? [normalizeManga(result.item)]
          : []
      manga = items[0] || null
      if (!manga) return ctx.reply('No manga result found for “' + term + '”.')

      const chapterOutcome = await ctx.executeSource({
        capability:'manga',
        explicitSource:outcome.source.id,
        payload:{ action:'chapters', itemId:manga.id, item:manga },
      })
      if (chapterOutcome.status !== 'ok') return outcomeError(ctx, chapterOutcome)
      chapters = (chapterOutcome.result?.chapters || chapterOutcome.result?.items || []).map(normalizeChapter)
      manga = {
        ...manga,
        title:String(chapterOutcome.result?.title || manga.title),
      }
    }

    const chapter = chapters.find(item =>
      String(item.number).trim() === wanted ||
      (Number.isFinite(Number(item.number)) && Number(item.number) === Number(wanted))
    )
    if (!chapter) {
      return ctx.reply('*' + manga.title + '* does not have Chapter ' + wanted + ' in the selected source.')
    }

    const saved = ctx.getDeliveryDefault?.('manga')
    return deliver(ctx, {
      sourceId:outcome.source.id,
      manga,
      chapter,
      quality:saved?.quality || 'source',
      delivery:saved?.delivery || 'document',
    })
  } catch (error) {
    return ctx.reply(error?.message || 'I could not download that chapter.')
  }
}

export async function runMangaCommand(ctx, { args = [] } = {}) {
  const first = String(args[0] || '')

  try {
    if (first === '~choice') return handleChoiceSelection(ctx)

    if (first === '~library-add') {
      const result = await addCanonicalLibraryItem(ctx, 'manga', args[1])
      if (!result.ok) return ctx.reply('I could not add that manga to Library. Run the manga search again.')
      return ctx.reply(result.added
        ? `Added *${result.item.title}* to Library.`
        : `*${result.item.title}* is already in Library.`)
    }

    if (first === '~numbers') {
      return handleNumberSelection(ctx)
    }

    if (first === '~selection-download') {
      const session = ctx.getCommandReplySession?.()
      if (
        !session ||
        session.kind !== 'media-download-options' ||
        session.command !== 'manga' ||
        !Array.isArray(session.selected)
      ) {
        return ctx.reply('That download selection expired. Run the manga command again. ✦')
      }

      ctx.clearCommandReplySession?.()
      return deliverChapterSelection(ctx, {
        sourceId:session.sourceId,
        manga:session.item,
        selection:session.selected,
        spec:session.selectionSpec || '',
        quality:String(args[1] || 'source'),
        delivery:String(args[2] || 'document'),
      })
    }

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
