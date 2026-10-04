import { resolveLyrics, splitLyricsText } from './utils/lyrics.js'

function clip(value, max) {
  return String(value ?? '').trim().slice(0, max)
}

export function normalizeLyricsTrack(item = {}) {
  return {
    title:clip(item?.title || item?.name, 80),
    artist:clip(item?.artist || item?.author || item?.uploader, 80),
    album:clip(item?.album, 80),
    duration:item?.durationSeconds || item?.duration || 0,
    durationSeconds:item?.durationSeconds || 0,
  }
}

export function encodeLyricsTrack(item = {}) {
  const track = normalizeLyricsTrack(item)
  const payload = {
    t:track.title,
    a:track.artist,
    l:track.album,
    d:track.durationSeconds || track.duration || 0,
  }
  return Buffer.from(JSON.stringify(payload), 'utf8').toString('base64url')
}

export function decodeLyricsTrack(value) {
  try {
    const parsed = JSON.parse(Buffer.from(String(value || ''), 'base64url').toString('utf8'))
    const track = normalizeLyricsTrack({
      title:parsed?.t,
      artist:parsed?.a,
      album:parsed?.l,
      duration:parsed?.d,
      durationSeconds:parsed?.d,
    })
    return track.title ? track : null
  } catch {
    return null
  }
}

export function lyricsInstantRows(tracks = [], {
  prefix = '.',
  max = 25,
} = {}) {
  return tracks
    .slice(0, Math.max(1, Number(max) || 25))
    .map(item => {
      const track = normalizeLyricsTrack(item)
      if (!track.title) return null
      const extra = [track.artist, track.duration].filter(Boolean).join(' • ')
      return {
        title:`🎤 ${track.title}`,
        description:extra || 'Open lyrics',
        id:`${prefix}lyrics ~track ${encodeLyricsTrack(track)}`,
      }
    })
    .filter(Boolean)
}

async function identifyTrack(ctx, query) {
  if (typeof ctx.executeSource !== 'function') return null

  try {
    const outcome = await ctx.executeSource({
      capability:'music',
      payload:{ action:'search', query },
    })
    if (outcome?.status !== 'ok') return null

    const result = outcome.result || {}
    const item = Array.isArray(result.items)
      ? result.items[0]
      : result.item || null
    if (!item) return null

    const track = normalizeLyricsTrack(item)
    return track.title ? track : null
  } catch (error) {
    console.warn('MSCC lyrics music identity lookup failed:', error?.message || error)
    return null
  }
}

async function deliverLyricsResult(ctx, { query, track, lyrics }) {
  if (!lyrics) return ctx.reply(`I could not find lyrics for “${query || track?.title || 'that song'}”.`)

  const title = lyrics.title || track?.title || query
  const artist = lyrics.artist || track?.artist || ''
  const heading = artist
    ? `*Lyrics*\n${title} — ${artist}`
    : `*Lyrics*\n${title}`

  if (lyrics.instrumental) return ctx.reply(`${heading}\n\nInstrumental — no lyrics.`)

  const chunks = splitLyricsText(lyrics.plain)
  if (!chunks.length) return ctx.reply(`I could not find readable lyrics for “${query || title}”.`)

  await ctx.reply(`${heading}\n\n${chunks[0]}`)
  for (const chunk of chunks.slice(1)) await ctx.reply(chunk)
  return true
}

async function deliverLyrics(ctx, { query, track }) {
  let lyrics
  try {
    lyrics = await resolveLyrics({ query, track })
  } catch (error) {
    console.error('MSCC lyrics lookup failed:', error)
    return ctx.reply('I could not reach the lyrics source right now.')
  }
  return deliverLyricsResult(ctx, { query, track, lyrics })
}

export async function runLyricsCommand(ctx, { args = [] } = {}) {
  const prefix = String(ctx.publicPrefix || '.')
  const first = String(args[0] || '')

  if (first === '~track') {
    const track = decodeLyricsTrack(args[1])
    if (!track) {
      return ctx.reply('That lyrics selection expired. Search for the song again.')
    }
    const query = [track.title, track.artist].filter(Boolean).join(' ')
    return deliverLyrics(ctx, { query, track })
  }

  const query = args.join(' ').trim()
  if (!query) {
    return ctx.reply(`Use ${prefix}lyrics <song name>.`)
  }

  // Identify the song first so LRCLIB can perform an exact lookup.
  const track = await identifyTrack(ctx, query)

  if (track) {
    return deliverLyrics(ctx, { query, track })
  }

  // Fall back to LRCLIB search when the music source cannot identify the track.
  try {
    const direct = await resolveLyrics({ query, track:null })
    if (direct) return deliverLyricsResult(ctx, { query, track:null, lyrics:direct })
  } catch (error) {
    console.warn('MSCC direct lyrics search failed:', error?.message || error)
  }

  return deliverLyrics(ctx, { query, track:null })
}
