import { parseNumberSelection } from './number-selection.js'

function normalizeTrack(item, index) {
  return {
    id:String(item?.id ?? item?.url ?? item?.slug ?? index + 1),
    number:String(index + 1),
    title:String(item?.title || item?.name || `Result ${index + 1}`),
    artist:String(item?.artist || item?.author || item?.uploader || '').trim(),
    duration:String(item?.duration || '').trim(),
    description:String(item?.description || '').trim(),
    raw:item,
  }
}

function outcomeError(ctx, outcome) {
  if (outcome?.status === 'no-sources') return ctx.reply('No music sources are installed yet.')
  if (outcome?.status === 'source-error') return ctx.reply(`${outcome.source?.name || 'The music source'} could not complete that request.`)
  if (outcome?.status === 'all-failed') return ctx.reply('All configured music sources failed for that request.')
  return ctx.reply('Could not complete that music request.')
}

async function search(ctx, query) {
  if (!query) return ctx.reply('Usage: .song <song name>')

  const outcome = await ctx.executeSource({
    capability:'music',
    payload:{ action:'search', query },
  })
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)

  const result = outcome.result || {}
  const tracks = Array.isArray(result.items)
    ? result.items.map(normalizeTrack)
    : result.item
      ? [normalizeTrack(result.item, 0)]
      : []

  if (!tracks.length) return ctx.reply(`No music results found for “${query}”.`)

  ctx.setCommandReplySession?.({
    kind:'number-selection',
    command:'song',
    capability:'music',
    sourceId:outcome.source.id,
    entries:tracks,
    unit:'song',
    query,
    expiresAt:Date.now() + 30 * 60000,
  })

  const lines = [
    `*Results for “${query}”*`,
    '',
    ...tracks.slice(0, 25).map(track => {
      const extra = [track.artist, track.duration].filter(Boolean).join(' • ')
      return `${track.number}. ${track.title}${extra ? ` — ${extra}` : ''}`
    }),
    '',
    'Reply with the number(s) you want.',
    'Examples: 1   •   1,3,5   •   1-4',
  ]
  return ctx.reply(lines.join('\n'))
}

async function downloadTrack(ctx, { sourceId, track }) {
  const outcome = await ctx.executeSource({
    capability:'music',
    pinnedSource:sourceId,
    payload:{
      action:'download',
      itemId:track.id,
      item:track.raw || track,
      track:track.raw || track,
      quality:'source',
      delivery:'audio',
    },
  })
  if (outcome.status !== 'ok') return { ok:false, outcome }

  const result = outcome.result
  if (result?.delivered === true) return { ok:true, silent:true }
  if (typeof result === 'string') return { ok:true, text:result }
  if (result?.text) return { ok:true, text:String(result.text) }
  return { ok:true, text:`Download started: ${track.title}.` }
}

async function handleNumbers(ctx) {
  const session = ctx.getCommandReplySession?.()
  if (
    !session ||
    session.kind !== 'number-selection' ||
    session.command !== 'song' ||
    !Array.isArray(session.entries)
  ) {
    return ctx.reply('That song selection expired. Run .song again.')
  }

  const spec = String(ctx.commandReplyInput || '').trim()
  const parsed = parseNumberSelection(spec, session.entries, {
    numberOf:track => track?.number,
    maxSelected:25,
  })
  if (!parsed.ok) {
    return ctx.reply('I could not match those song numbers. Try: 1 or 1,3,5 or 1-4')
  }

  ctx.clearCommandReplySession?.()

  const messages = []
  for (const track of parsed.selected) {
    const result = await downloadTrack(ctx, {
      sourceId:session.sourceId,
      track,
    })
    if (!result.ok) return outcomeError(ctx, result.outcome)
    if (result.text) messages.push(result.text)
  }

  if (messages.length === 1) return ctx.reply(messages[0])
  if (messages.length > 1) return ctx.reply(messages.join('\n'))
  if (parsed.selected.length > 1) {
    return ctx.reply(`Started ${parsed.selected.length} songs. ✦`)
  }
  return true
}

export async function runSongCommand(ctx, { args = [] } = {}) {
  const first = String(args[0] || '')
  if (first === '~numbers') return handleNumbers(ctx)
  return search(ctx, args.join(' ').trim())
}
