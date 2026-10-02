import { parseNumberSelection } from './number-selection.js'
const token = value => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
const untoken = value => JSON.parse(Buffer.from(String(value || ''), 'base64url').toString('utf8'))

const titleOf = item => String(item?.title || item?.name || 'Untitled')
const idOf = item => String(item?.id ?? item?.workId ?? item?.url ?? titleOf(item))

function normalizeBook(item, index = 0) {
  return {
    id:idOf(item),
    number:String(index + 1),
    title:titleOf(item),
    author:String(item?.author || item?.authorName || '').trim(),
    year:String(item?.year || item?.published || '').trim(),
    cover:String(item?.cover || item?.image || item?.thumbnail || '').trim(),
    wikidataId:String(item?.wikidataId || '').trim(),
    raw:item,
  }
}

function normalizeEdition(item, index = 0) {
  return {
    id:String(item?.id ?? item?.editionId ?? item?.isbn ?? item?.url ?? index + 1),
    number:String(index + 1),
    title:String(item?.title || item?.format || item?.name || `Edition ${index + 1}`),
    format:String(item?.format || item?.type || '').trim(),
    language:String(item?.language || '').trim(),
    size:String(item?.size || '').trim(),
    raw:item,
  }
}

function normalizeChapter(item, index = 0) {
  return {
    id:String(item?.id ?? item?.chapterId ?? item?.url ?? index + 1),
    number:String(index + 1),
    title:String(item?.name || item?.title || `Chapter ${index + 1}`).trim(),
    raw:item,
  }
}

function parsePick(value, entries = []) {
  const n = Number(String(value || '').trim())
  if (!Number.isInteger(n) || n < 1 || n > entries.length) return null
  return entries[n - 1]
}

function normalizeFormatKey(value) {
  return String(value || '').trim().toLowerCase().replace(/[^a-z0-9]+/g, '')
}

function editionFormatKey(edition) {
  return normalizeFormatKey(edition?.format || edition?.title || '')
}

function savedBookFormat(ctx) {
  const saved = ctx.getDeliveryDefault?.('books')
  return normalizeFormatKey(saved?.quality || '')
}

function outcomeError(ctx, outcome) {
  if (outcome?.status === 'no-sources') return ctx.reply('No book sources are installed yet.')
  if (outcome?.status === 'source-error') return ctx.reply(`${outcome.source?.name || 'The book source'} could not complete that request.`)
  if (outcome?.status === 'all-failed') return ctx.reply('All configured book sources failed for that request.')
  return ctx.reply('Could not complete that book request.')
}

async function chooseSource(ctx, query) {
  const sources = ctx.listSources('books')
  const prefix = ctx.publicPrefix || '.'
  if (!sources.length) return outcomeError(ctx, { status:'no-sources' })
  return ctx.replyList({
    title:'Choose book source',
    text:`Choose a source for “${query}”.`,
    buttonText:'Choose source',
    footer:`Save a default: ${prefix}source books <source>`,
    rows:sources.map(source => ({
      title:source.name,
      description:source.description || 'Use for this search',
      id:`${prefix}book ~source ${source.id} ${token(query)}`,
    })),
  })
}

async function search(ctx, { query, sourceId = '' }) {
  if (!query) return ctx.reply('Usage: .book <title or author>')

  const outcome = await ctx.executeSource({
    capability:'books',
    explicitSource:sourceId,
    payload:{ action:'search', query },
  })
  if (outcome.status === 'choice-required') return chooseSource(ctx, query)
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)

  const result = outcome.result || {}
  const books = (Array.isArray(result.items) ? result.items : result.item ? [result.item] : [])
    .map(normalizeBook)

  if (!books.length) return ctx.reply(`No books found for “${query}”.`)

  ctx.setCommandReplySession?.({
    kind:'number-selection',
    command:'book',
    stage:'book',
    sourceId:outcome.source.id,
    entries:books,
    previewedCover:String(books[0]?.cover || ''),
    expiresAt:Date.now() + 30 * 60000,
  })

  await previewBook(ctx, books[0])

  return ctx.reply([
    `*Books for “${query}”*`,
    '',
    ...books.slice(0, 25).map(book => {
      const extra = [book.author, book.year].filter(Boolean).join(' • ')
      return `${book.number}. ${book.title}${extra ? ` — ${extra}` : ''}`
    }),
    '',
    'Reply with the book number.',
  ].join('\n'))
}

async function previewBook(ctx, book) {
  const cover = String(book?.cover || book?.raw?.cover || book?.raw?.image || book?.raw?.thumbnail || '').trim()
  if (!cover || typeof ctx.sendImageUrl !== 'function') return false
  const lines = [
    `*${book.title}*`,
    book.author ? `Author: ${book.author}` : '',
    book.year ? `Published: ${book.year}` : '',
  ].filter(Boolean)
  try {
    await ctx.sendImageUrl(cover, lines.join('\n'))
    return true
  } catch {
    return false
  }
}

async function adaptationRows(ctx, book) {
  if (typeof ctx.resolveBookScreens !== 'function') return []
  const relations = await ctx.resolveBookScreens({
    title:book.title,
    author:book.author,
    wikidataId:book.wikidataId,
  })
  const prefix = ctx.publicPrefix || '.'
  return relations.slice(0, 5).flatMap(item => {
    if (item.tmdbMovieId) {
      return [{
        title:`🎬 Movie: ${item.title}`,
        description:'Open the movie command flow',
        id:`${prefix}movie ~tmdb ${item.tmdbMovieId}`,
      }]
    }
    if (item.tmdbTvId) {
      return [{
        title:`📺 Series: ${item.title}`,
        description:'Open the TV command flow',
        id:`${prefix}tv ~tmdb ${item.tmdbTvId}`,
      }]
    }
    return []
  })
}

function chapterPrompt(book, chapters = []) {
  const total = chapters.length
  const preview = total <= 14
    ? chapters
    : [...chapters.slice(0, 10), null, ...chapters.slice(-3)]
  const lines = [
    `*${book.title}*`,
    book.author ? `Author: ${book.author}` : '',
    `${total} chapter${total === 1 ? '' : 's'}`,
    '',
    ...preview.map(chapter => chapter
      ? `${chapter.number}. ${chapter.title}`
      : '…'),
    '',
    '*Reply with the chapter number(s) you want.*',
    'Examples: `1` • `1,3,4,7` • `1-10` • `1-5,9,12-15`',
  ]
  return lines.filter(Boolean).join('\n')
}

async function chooseChapters(ctx, { sourceId, book, chapters }) {
  const normalized = chapters.map(normalizeChapter)
  if (!normalized.length) return ctx.reply('No chapters were found for that novel.')

  ctx.setCommandReplySession?.({
    kind:'number-selection',
    command:'book',
    stage:'chapters',
    sourceId,
    book,
    entries:normalized,
    unit:'chapter',
    expiresAt:Date.now() + 30 * 60000,
  })

  return ctx.reply(chapterPrompt(book, normalized))
}

async function downloadChapters(ctx, { sourceId, book, chapters }) {
  ctx.clearCommandReplySession?.()
  const outcome = await ctx.executeSource({
    capability:'books',
    explicitSource:sourceId,
    payload:{
      action:'download-chapters',
      itemId:book.id,
      item:book.raw || book,
      chapters:chapters.map(chapter => chapter.raw || chapter),
      delivery:'document',
    },
  })
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)
  if (outcome.result?.delivered === true) return true
  if (typeof outcome.result === 'string') return ctx.reply(outcome.result)
  if (outcome.result?.text) return ctx.reply(String(outcome.result.text))
  return ctx.reply(`Novel chapters prepared: ${chapters.length}.`)
}

async function loadEditions(ctx, { sourceId, book }) {
  const outcome = await ctx.executeSource({
    capability:'books',
    explicitSource:sourceId,
    payload:{ action:'editions', itemId:book.id, item:book.raw || book },
  })
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)

  const result = outcome.result || {}
  if (Array.isArray(result.chapters) && result.chapters.length) {
    const enrichedBook = {
      ...book,
      author:String(result?.novel?.author || book.author || '').trim(),
      cover:String(result?.novel?.cover || book.cover || '').trim(),
    }
    return chooseChapters(ctx, {
      sourceId,
      book:enrichedBook,
      chapters:result.chapters,
    })
  }

  let editions = (result.editions || result.formats || result.items || []).map(normalizeEdition)
  const related = await adaptationRows(ctx, book)

  if (!editions.length) return download(ctx, { sourceId, book, edition:null })

  const preferredFormat = savedBookFormat(ctx)
  if (preferredFormat) {
    const preferred = editions.filter(edition => editionFormatKey(edition) === preferredFormat)
    if (preferred.length === 1) {
      const result = await download(ctx, { sourceId, book, edition:preferred[0] })
      if (related.length) {
        await ctx.replyList({
          title:book.title,
          text:`Using saved ${preferred[0].format || preferred[0].title} format.`,
          buttonText:'Adaptations',
          rows:related,
        })
      }
      return result
    }
    if (preferred.length > 1) {
      const preferredIds = new Set(preferred.map(edition => edition.id))
      editions = [...preferred, ...editions.filter(edition => !preferredIds.has(edition.id))]
    }
  }

  ctx.setCommandReplySession?.({
    kind:'number-selection',
    command:'book',
    stage:'edition',
    sourceId,
    book,
    entries:editions,
    expiresAt:Date.now() + 30 * 60000,
  })

  const text = [
    `*${book.title}*`,
    preferredFormat ? `Saved format: ${preferredFormat.toUpperCase()}` : '',
    '',
    ...editions.slice(0, 25).map(edition => {
      const extra = [edition.format, edition.language, edition.size].filter(Boolean).join(' • ')
      return `${edition.number}. ${edition.title}${extra ? ` — ${extra}` : ''}`
    }),
    '',
    'Reply with the edition/format number.',
    `Save a default: ${ctx.publicPrefix || '.'}delivery books epub document`,
  ].filter(Boolean).join('\n')

  if (related.length) {
    return ctx.replyList({
      title:book.title,
      text,
      buttonText:'Adaptations',
      footer:`Type the edition number to download. Save format: ${ctx.publicPrefix || '.'}delivery books epub document`,
      rows:related,
    })
  }
  return ctx.reply(text)
}

async function download(ctx, { sourceId, book, edition }) {
  ctx.clearCommandReplySession?.()
  const outcome = await ctx.executeSource({
    capability:'books',
    explicitSource:sourceId,
    payload:{
      action:'download',
      itemId:book.id,
      item:book.raw || book,
      edition:edition?.raw || edition || null,
      editionId:edition?.id || '',
      delivery:'document',
    },
  })
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)
  if (outcome.result?.delivered === true) return true
  if (typeof outcome.result === 'string') return ctx.reply(outcome.result)
  if (outcome.result?.text) return ctx.reply(String(outcome.result.text))
  return ctx.reply(`Book download started: ${book.title}.`)
}

async function handleNumbers(ctx) {
  const session = ctx.getCommandReplySession?.()
  if (!session || session.command !== 'book' || session.kind !== 'number-selection') {
    return ctx.reply('That book selection expired. Run .book again.')
  }
  const selected = parsePick(ctx.commandReplyInput, session.entries || [])
  if (!selected) return ctx.reply('Reply with one number from the book list.')

  if (session.stage === 'book') {
    if (String(selected.cover || '') && String(selected.cover || '') !== String(session.previewedCover || '')) {
      await previewBook(ctx, selected)
    }
    return loadEditions(ctx, { sourceId:session.sourceId, book:selected })
  }
  if (session.stage === 'edition') {
    return download(ctx, { sourceId:session.sourceId, book:session.book, edition:selected })
  }
  if (session.stage === 'chapters') {
    const parsed = parseNumberSelection(ctx.commandReplyInput, session.entries || [], {
      numberOf:chapter => chapter?.number,
      maxSelected:250,
    })
    if (!parsed.ok) {
      if (parsed.error === 'too-many') {
        return ctx.reply('Choose up to 250 chapters in one download.')
      }
      return ctx.reply('I could not match those chapter numbers. Try: 1 or 1,3,4,7 or 1-10')
    }
    return downloadChapters(ctx, {
      sourceId:session.sourceId,
      book:session.book,
      chapters:parsed.selected,
    })
  }
  return ctx.reply('That book selection expired. Run .book again.')
}

export async function runBookCommand(ctx, { args = [] } = {}) {
  const first = String(args[0] || '')
  if (first === '~numbers') return handleNumbers(ctx)
  if (first === '~relation') {
    try {
      return search(ctx, { query:untoken(args[1] || '') })
    } catch {
      return ctx.reply('That book relation expired. Search the book again.')
    }
  }
  if (first === '~source') {
    try {
      return search(ctx, {
        sourceId:String(args[1] || ''),
        query:untoken(args[2] || ''),
      })
    } catch {
      return ctx.reply('That book source selection expired. Run .book again.')
    }
  }
  return search(ctx, { query:args.join(' ').trim() })
}
