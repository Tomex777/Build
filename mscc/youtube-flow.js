import { parseNumberSelection } from './number-selection.js'
import {
  prepareYouTubeVideo,
  resolveYouTubeVideo,
  searchYouTubeVideos,
  selectVideoCandidate,
  parseYouTubeVideoId,
} from './youtube-video.js'

function videoLabel(video = {}) {
  return [
    video.channel || '',
    video.duration || '',
    video.views || '',
  ].filter(Boolean).join(' • ')
}

function normalizeQuality(value) {
  const raw = String(value || '').trim().toLowerCase().replace(/p$/, '')
  return raw === '4k' ? '2160' : raw
}

function parseSearchArgs(args = []) {
  const queryParts = []
  let deliveryOverride = ''
  for (const raw of args) {
    const value = String(raw || '').trim()
    if (value === '-d' || value === '--doc' || value === '--document') {
      deliveryOverride = 'document'
      continue
    }
    if (value === '--video') {
      deliveryOverride = 'video'
      continue
    }
    if (value) queryParts.push(value)
  }
  return {
    query:queryParts.join(' ').trim(),
    deliveryOverride,
  }
}

function qualityRows(videoId, candidates, delivery, prefix) {
  return [...candidates]
    .sort((a,b) => b.height - a.height)
    .map(candidate => ({
      title:`${candidate.height}p`,
      description:candidate.kind === 'adaptive' ? 'Video + audio' : 'Progressive',
      id:`${prefix}youtube --download ${videoId} ${candidate.height} ${delivery}`,
    }))
}

async function sendSearch(ctx, query, { deliveryOverride = '' } = {}) {
  if (!query) {
    return ctx.reply(`Usage: ${ctx.publicPrefix || '.'}youtube <search>`)
  }

  let videos
  try {
    videos = await searchYouTubeVideos(query, { limit:20 })
  } catch (error) {
    console.error('MSCC YouTube search failed:', error)
    return ctx.reply(error?.message || 'YouTube search failed.')
  }

  if (!videos.length) return ctx.reply(`No YouTube videos found for “${query}”.`)

  ctx.setCommandReplySession?.({
    kind:'number-selection',
    command:'youtube',
    stage:'search',
    entries:videos.map((video, index) => ({ ...video, number:String(index + 1) })),
    query,
    deliveryOverride,
    expiresAt:Date.now() + 30 * 60000,
  })

  const entries = videos.map((video, index) => ({ ...video, number:String(index + 1) }))
  const lines = [
    '▶️ *YouTube*',
    `Search: *${query}*`,
    '',
    ...entries.map(video => {
      const extra = videoLabel(video)
      return `${video.number}. ${video.title}${extra ? ` — ${extra}` : ''}`
    }),
    '',
    '*Reply with one number to choose a video.*',
  ]
  const text = lines.join('\n')
  const prefix = ctx.publicPrefix || '.'

  if (typeof ctx.replyList === 'function') {
    return ctx.replyList({
      title:'YouTube results',
      text,
      caption:text,
      image:entries[0]?.thumbnail ? { url:entries[0].thumbnail } : null,
      buttonText:'Choose video',
      footer:'Choose one video, or reply with its number.',
      rows:entries.map(video => ({
        title:video.title.slice(0, 72),
        description:videoLabel(video).slice(0, 72),
        id:`${prefix}youtube --pick ${video.id}${deliveryOverride === 'document' ? ' --doc' : deliveryOverride === 'video' ? ' --video' : ''}`,
      })),
    })
  }

  return ctx.reply(text)
}

async function sendVideo(ctx, videoId, quality, delivery = 'video') {
  const chosenDelivery = delivery === 'document' ? 'document' : 'video'
  const progress = typeof ctx.progress === 'function'
    ? await ctx.progress(`Preparing YouTube ${quality === 'best' ? 'video' : quality + 'p'}…`)
    : null

  let media
  try {
    media = await prepareYouTubeVideo(videoId, {
      quality,
      onStage: text => progress?.update?.(text),
    })

    await progress?.update?.('Sending video…')

    const chat = ctx.message?.key?.remoteJid
    const sock = ctx.account?.sock
    if (!chat || !sock) throw new Error('WhatsApp connection is unavailable.')

    const caption = [
      `*${media.details.title}*`,
      media.details.channel ? media.details.channel : '',
      `${media.candidate.height}p`,
    ].filter(Boolean).join('\n')

    if (chosenDelivery === 'document' || media.mimetype !== 'video/mp4') {
      await sock.sendMessage(chat, {
        document:{ url:media.file },
        mimetype:media.mimetype,
        fileName:media.fileName,
        caption,
      }, { quoted:ctx.message })
    } else {
      await sock.sendMessage(chat, {
        video:{ url:media.file },
        mimetype:media.mimetype,
        fileName:media.fileName,
        caption,
      }, { quoted:ctx.message })
    }

    await progress?.done?.(`Sent *${media.details.title}* • ${media.candidate.height}p`)
    return true
  } catch (error) {
    console.error('MSCC YouTube delivery failed:', error)
    await progress?.fail?.(error?.message || 'YouTube video delivery failed.')
    if (!progress) await ctx.reply(error?.message || 'YouTube video delivery failed.')
    return false
  } finally {
    await media?.cleanup?.()
  }
}

async function chooseQuality(ctx, video, { deliveryOverride = '' } = {}) {
  const progress = typeof ctx.progress === 'function'
    ? await ctx.progress('Checking YouTube formats…')
    : null

  let resolved
  try {
    resolved = await resolveYouTubeVideo(video.id)
  } catch (error) {
    console.error('MSCC YouTube format resolution failed:', error)
    await progress?.fail?.(error?.message || 'I could not resolve that YouTube video.')
    if (!progress) await ctx.reply(error?.message || 'I could not resolve that YouTube video.')
    return
  }

  const saved = ctx.getDeliveryDefault?.('youtube') || null
  const savedQuality = normalizeQuality(saved?.quality || '')
  const delivery = deliveryOverride || (saved?.delivery === 'document' ? 'document' : saved?.delivery === 'video' ? 'video' : 'video')

  if (savedQuality) {
    const candidate = selectVideoCandidate(resolved.candidates, savedQuality)
    if (!candidate) {
      await progress?.fail?.('Your saved YouTube quality is unavailable for this video.')
      return
    }
    await progress?.done?.(`Using your YouTube default: ${candidate.height}p • ${delivery}.`)
    return sendVideo(ctx, video.id, candidate.quality, delivery)
  }

  const choices = [...resolved.candidates]
    .sort((a,b) => b.height - a.height)
    .map((candidate, index) => ({
      number:String(index + 1),
      quality:candidate.quality,
      height:candidate.height,
      kind:candidate.kind,
    }))

  ctx.setCommandReplySession?.({
    kind:'number-selection',
    command:'youtube',
    stage:'quality',
    video,
    delivery,
    entries:choices,
    expiresAt:Date.now() + 30 * 60000,
  })

  await progress?.done?.(`Choose a quality for *${resolved.details.title}*.`)

  const prefix = ctx.publicPrefix || '.'
  const text = [
    `▶️ *${resolved.details.title}*`,
    resolved.details.channel ? `Channel: ${resolved.details.channel}` : '',
    '',
    ...choices.map(item => `${item.number}. ${item.height}p${item.kind === 'adaptive' ? ' • video + audio' : ''}`),
    '',
    '*Reply with one quality number.*',
    `Delivery: ${delivery}`,
  ].filter(Boolean).join('\n')

  if (typeof ctx.replyList === 'function') {
    return ctx.replyList({
      title:'YouTube quality',
      text,
      caption:text,
      image:resolved.details.thumbnail ? { url:resolved.details.thumbnail } : null,
      buttonText:'Choose quality',
      footer:'Save a default later with .delivery youtube <quality> <video|document>.',
      rows:qualityRows(video.id, resolved.candidates, delivery, prefix),
    })
  }

  return ctx.reply(text)
}

async function handleNumbers(ctx) {
  const session = ctx.getCommandReplySession?.()
  if (!session || session.kind !== 'number-selection' || session.command !== 'youtube' || !Array.isArray(session.entries)) {
    return ctx.reply('That YouTube selection expired. Run .youtube again.')
  }

  const spec = String(ctx.commandReplyInput || '').trim()
  const parsed = parseNumberSelection(spec, session.entries, {
    numberOf:item => item?.number,
    maxSelected:1,
  })
  if (!parsed.ok || parsed.selected.length !== 1) {
    return ctx.reply('Choose one YouTube result at a time.')
  }

  const chosen = parsed.selected[0]
  ctx.clearCommandReplySession?.()

  if (session.stage === 'quality') {
    return sendVideo(ctx, session.video?.id, chosen.quality, session.delivery || 'video')
  }

  return chooseQuality(ctx, chosen, {
    deliveryOverride:session.deliveryOverride || '',
  })
}

async function handlePick(ctx, args) {
  const id = String(args[1] || '').trim()
  if (!/^[A-Za-z0-9_-]{11}$/.test(id)) return ctx.reply('That YouTube result is invalid.')

  const deliveryOverride = args.includes('--doc') || args.includes('--document')
    ? 'document'
    : args.includes('--video')
      ? 'video'
      : ''

  const session = ctx.getCommandReplySession?.()
  const video = session?.stage === 'search' && Array.isArray(session.entries)
    ? session.entries.find(item => item?.id === id)
    : null

  ctx.clearCommandReplySession?.()
  return chooseQuality(ctx, video || {
    id,
    title:'YouTube video',
    channel:'',
    thumbnail:`https://i.ytimg.com/vi/${id}/hqdefault.jpg`,
  }, { deliveryOverride })
}

async function handleDirectDownload(ctx, args) {
  const id = String(args[1] || '').trim()
  const quality = normalizeQuality(args[2] || 'best')
  const delivery = String(args[3] || 'video').trim().toLowerCase() === 'document' ? 'document' : 'video'
  if (!/^[A-Za-z0-9_-]{11}$/.test(id)) return ctx.reply('That YouTube result is invalid.')
  ctx.clearCommandReplySession?.()
  return sendVideo(ctx, id, quality || 'best', delivery)
}

export async function runYouTubeCommand(ctx, { args = [] } = {}) {
  const first = String(args[0] || '').trim().toLowerCase()
  if (first === '~numbers') return handleNumbers(ctx)
  if (first === '--pick') return handlePick(ctx, args)
  if (first === '--download') return handleDirectDownload(ctx, args)

  const parsed = parseSearchArgs(args)
  const directId = parseYouTubeVideoId(parsed.query)
  if (directId) {
    return chooseQuality(ctx, {
      id:directId,
      title:'YouTube video',
      channel:'',
      thumbnail:`https://i.ytimg.com/vi/${directId}/hqdefault.jpg`,
    }, { deliveryOverride:parsed.deliveryOverride })
  }
  return sendSearch(ctx, parsed.query, parsed)
}
