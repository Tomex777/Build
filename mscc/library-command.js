import { normalizeLibraryType, libraryTypeLabel } from './media-library.js'
import { runSeriesCommand } from './series-flow.js'
import { runMangaCommand } from './manga-flow.js'
import { runMovieCommand } from './movie-flow.js'
import { runTvCommand } from './tv-flow.js'

function slotOf(value) {
  const slot = Number(value)
  return Number.isInteger(slot) && slot > 0 ? slot : 0
}

function itemDescription(item) {
  return item.watchReleases ? '🔔 Watching releases' : 'Saved'
}

async function openItem(ctx, item) {
  const id = String(item.externalId || '')
  if (item.mediaType === 'anime') {
    return runSeriesCommand(ctx, { capability:'anime', commandName:'anime', args:['~anilist', id] })
  }
  if (item.mediaType === 'manga') {
    return runMangaCommand(ctx, { args:['~anilist', id] })
  }
  if (item.mediaType === 'movie') {
    return runMovieCommand(ctx, { args:['~tmdb', id] })
  }
  if (item.mediaType === 'tv') {
    return runTvCommand(ctx, { args:['~tmdb', id] })
  }
  return ctx.reply('That Library item is no longer supported.')
}

export async function runLibraryCommand(ctx, { args = [] } = {}) {
  const first = String(args[0] || '').trim().toLowerCase()
  const prefix = ctx.publicPrefix || '.'

  if (first === 'remove') {
    const slot = slotOf(args[1])
    if (!slot) return ctx.reply('Use: ' + prefix + 'library remove <number>')
    const item = ctx.libraryBySlot?.(slot)
    if (!item) return ctx.reply('That Library number does not exist.')
    ctx.libraryRemove?.(item.itemKey)
    return ctx.reply('Removed *' + item.title + '* from Library.')
  }

  if (first === 'watch' || first === 'unwatch') {
    const slot = slotOf(args[1])
    if (!slot) return ctx.reply('Use: ' + prefix + 'library ' + first + ' <number>')
    const item = ctx.libraryBySlot?.(slot)
    if (!item) return ctx.reply('That Library number does not exist.')
    const enabled = first === 'watch'
    const updated = ctx.librarySetWatch?.(item.itemKey, enabled) || item
    if (enabled) await ctx.libraryPrimeWatch?.(updated)
    return ctx.reply(enabled
      ? 'Watching releases for *' + item.title + '*. New release alerts will be sent to your DM.'
      : 'Stopped watching releases for *' + item.title + '*.')
  }

  const directSlot = slotOf(first)
  if (directSlot) {
    const item = ctx.libraryBySlot?.(directSlot)
    if (!item) return ctx.reply('That Library number does not exist.')
    return openItem(ctx, item)
  }

  const filter = first ? normalizeLibraryType(first) : ''
  if (first && !filter) {
    return ctx.reply(
      'Use ' + prefix + 'library, ' +
      prefix + 'library anime, ' +
      prefix + 'library manga, ' +
      prefix + 'library movie, or ' +
      prefix + 'library tv.'
    )
  }

  const items = ctx.libraryList?.(filter) || []
  if (!items.length) {
    return ctx.reply(filter
      ? 'Your ' + libraryTypeLabel(filter) + ' Library is empty.'
      : 'Your Library is empty.')
  }

  const grouped = filter
    ? [[filter, items]]
    : ['anime','manga','movie','tv']
        .map(type => [type, items.filter(item => item.mediaType === type)])
        .filter(([, rows]) => rows.length)

  return ctx.replyList({
    title:filter ? libraryTypeLabel(filter) + ' Library' : 'Library',
    text:filter
      ? String(items.length) + ' saved ' + libraryTypeLabel(filter).toLowerCase() + ' item' + (items.length === 1 ? '' : 's') + '.'
      : String(items.length) + ' saved item' + (items.length === 1 ? '' : 's') + '.',
    buttonText:'Open Library',
    footer:'Open: ' + prefix + 'library <number> · Remove: ' + prefix + 'library remove <number>',
    sections:grouped.map(([type, rows]) => ({
      title:libraryTypeLabel(type),
      rows:rows.map(item => ({
        title:String(item.slot) + '. ' + item.title,
        description:itemDescription(item),
        id:prefix + 'library ' + item.slot,
      })),
    })),
  })
}

export async function runLibraryWatchCommand(ctx, { args = [], enabled = true } = {}) {
  const slot = slotOf(args[0])
  const prefix = ctx.publicPrefix || '.'
  if (!slot) {
    return ctx.reply('Use: ' + prefix + (enabled ? 'watch' : 'unwatch') + ' <library number>')
  }
  const item = ctx.libraryBySlot?.(slot)
  if (!item) return ctx.reply('That Library number does not exist.')
  const updated = ctx.librarySetWatch?.(item.itemKey, enabled) || item
  if (enabled) await ctx.libraryPrimeWatch?.(updated)
  return ctx.reply(enabled
    ? 'Watching releases for *' + item.title + '*. New release alerts will be sent to your DM.'
    : 'Stopped watching releases for *' + item.title + '*.')
}
