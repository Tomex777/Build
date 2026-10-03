import { parseNumberSelection } from './number-selection.js'
import { albumTracks, searchAlbums } from './album-service.js'
import { downloadMusicQuery } from './music-flow.js'

function durationText(seconds) {
  const total = Math.max(0, Math.round(Number(seconds) || 0))
  if (!total) return ''
  const minutes = Math.floor(total / 60)
  const secs = total % 60
  return minutes + ':' + String(secs).padStart(2,'0')
}

async function openAlbum(ctx, albumId) {
  const album = await albumTracks(albumId)
  const entries = album.tracks.slice(0, 120)

  ctx.setCommandReplySession?.({
    kind:'album-selection',
    command:'album',
    capability:'music',
    album:{
      id:album.id,
      title:album.title,
      artist:album.artist,
      artwork:album.artwork,
    },
    entries,
    expiresAt:Date.now() + 30 * 60000,
  })

  const year = album.releaseDate ? String(album.releaseDate).slice(0,4) : ''
  const lines = [
    '💿 *' + album.title + '*',
    [album.artist, year, album.genre].filter(Boolean).join(' · '),
    '',
    ...entries.map((track,index) => {
      const duration = durationText(track.durationSeconds)
      return String(index + 1) + '. ' + track.title + (duration ? ' [' + duration + ']' : '')
    }),
    '',
    '*Reply with the track number(s) you want.*',
    'Examples: 1 · 1,3,5 · 1-4',
    '*Reply all to download the whole album.*',
  ].filter(Boolean)

  if (album.artwork && typeof ctx.sendImageUrl === 'function') {
    try { return await ctx.sendImageUrl(album.artwork, lines.join('\n')) } catch {}
  }
  return ctx.reply(lines.join('\n'))
}

async function searchAlbum(ctx, query) {
  const rows = await searchAlbums(query, 10)
  if (!rows.length) return ctx.reply('No albums found for “' + query + '”.')
  if (rows.length === 1) return openAlbum(ctx, rows[0].id)

  const prefix = ctx.publicPrefix || '.'
  const text = [
    '💿 *Albums* — ' + query,
    '',
    ...rows.map((row,index) => {
      const year = row.releaseDate ? String(row.releaseDate).slice(0,4) : ''
      return String(index + 1) + '. ' + row.title + ' — ' + row.artist + (year ? ' (' + year + ')' : '')
    }),
    '',
    'Choose the album to open its full track list.',
  ].join('\n')

  if (typeof ctx.replyList === 'function') {
    return ctx.replyList({
      title:'Albums',
      text,
      buttonText:'Choose album',
      rows:rows.map(row => ({
        title:row.title,
        description:[row.artist, row.releaseDate ? String(row.releaseDate).slice(0,4) : '', row.trackCount ? row.trackCount + ' tracks' : ''].filter(Boolean).join(' · '),
        id:prefix + 'album ~open ' + row.id,
      })),
    })
  }
  return ctx.reply(text)
}

async function handleSelection(ctx) {
  const session = ctx.getCommandReplySession?.()
  if (!session || session.kind !== 'album-selection' || session.command !== 'album' || !Array.isArray(session.entries)) {
    return ctx.reply('That album selection expired. Run .album again.')
  }

  const spec = String(ctx.commandReplyInput || '').trim().toLowerCase()
  let selected = []
  if (spec === 'all') {
    selected = session.entries
  } else {
    const numbered = session.entries.map((track,index) => ({ ...track, number:String(index + 1) }))
    const parsed = parseNumberSelection(spec, numbered, {
      numberOf:track => track.number,
      maxSelected:120,
    })
    if (!parsed.ok) return ctx.reply('Reply with track numbers like 1,3,5 or 1-4 — or reply all.')
    selected = parsed.selected
  }

  ctx.clearCommandReplySession?.()
  if (!selected.length) return ctx.reply('That album selection was empty.')

  const failures = []
  let completed = 0
  for (const track of selected) {
    const query = [track.title, track.artist].filter(Boolean).join(' ')
    const result = await downloadMusicQuery(ctx, query, {
      title:track.title,
      artist:track.artist,
      album:track.album || session.album?.title || '',
    })
    if (result.ok) completed += 1
    else failures.push(track.title)
  }

  if (!failures.length) {
    return ctx.reply(
      selected.length === 1
        ? 'Started *' + selected[0].title + '*.'
        : 'Started *' + completed + '* tracks from *' + (session.album?.title || 'the album') + '*.'
    )
  }

  return ctx.reply([
    'Album download started.',
    'Completed: ' + completed + '/' + selected.length,
    failures.length ? 'Could not resolve: ' + failures.slice(0,8).join(', ') + (failures.length > 8 ? '…' : '') : '',
  ].filter(Boolean).join('\n'))
}

export async function runAlbumCommand(ctx, { args = [] } = {}) {
  const first = String(args[0] || '')
  try {
    if (first === '~open') return openAlbum(ctx, args[1])
    if (first === '~selection') return handleSelection(ctx)
    const query = args.join(' ').trim()
    if (!query) return ctx.reply('Use .album <album or artist name>.')
    return searchAlbum(ctx, query)
  } catch (error) {
    return ctx.reply(error?.message || 'Album lookup failed.')
  }
}
