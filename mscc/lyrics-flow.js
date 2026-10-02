import { resolveLyrics, splitLyricsText } from './utils/lyrics.js'

function normalizeTrack(item = {}) {
  return {
    title:String(item?.title || item?.name || '').trim(),
    artist:String(item?.artist || item?.author || item?.uploader || '').trim(),
    album:String(item?.album || '').trim(),
    duration:item?.durationSeconds || item?.duration || 0,
    durationSeconds:item?.durationSeconds || 0,
  }
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

    const track = normalizeTrack(item)
    return track.title ? track : null
  } catch (error) {
    console.warn('MSCC lyrics music identity lookup failed:', error?.message || error)
    return null
  }
}

export async function runLyricsCommand(ctx, { args = [] } = {}) {
  const query = args.join(' ').trim()
  const prefix = String(ctx.publicPrefix || '.')

  if (!query) {
    return ctx.reply(`Use ${prefix}lyrics <song name>.`)
  }

  const track = await identifyTrack(ctx, query)

  let lyrics
  try {
    lyrics = await resolveLyrics({ query, track })
  } catch (error) {
    console.error('MSCC lyrics lookup failed:', error)
    return ctx.reply('I could not reach the lyrics source right now.')
  }

  if (!lyrics) {
    return ctx.reply(`I could not find lyrics for “${query}”.`)
  }

  const title = lyrics.title || track?.title || query
  const artist = lyrics.artist || track?.artist || ''
  const heading = artist
    ? `*Lyrics*\n${title} — ${artist}`
    : `*Lyrics*\n${title}`

  if (lyrics.instrumental) {
    return ctx.reply(`${heading}\n\nInstrumental — no lyrics.`)
  }

  const chunks = splitLyricsText(lyrics.plain)
  if (!chunks.length) {
    return ctx.reply(`I could not find readable lyrics for “${query}”.`)
  }

  await ctx.reply(`${heading}\n\n${chunks[0]}`)
  for (const chunk of chunks.slice(1)) {
    await ctx.reply(chunk)
  }
  return true
}
