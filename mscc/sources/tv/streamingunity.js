import {
  browseTitles,
  listEpisodes,
  listSeasons,
  materializeStream,
  qualityList,
  resolveStream,
  searchTitles,
} from '../../providers/streamingunity.js'

function cleanName(value) {
  return String(value || 'episode')
    .replace(/[\\/:*?"<>|]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0,120) || 'episode'
}

async function deliverEpisode(item, seasonNumber, episode, quality, delivery, context) {
  if (!episode?.id) throw new Error('StreamingUnity episode reference is missing.')
  const stream = await resolveStream(item, episode.id)
  const media = await materializeStream(stream, quality, delivery)
  try {
    const season = String(Number(seasonNumber) || 1).padStart(2, '0')
    const number = String(Number(episode.number) || 0).padStart(2, '0')
    const base = cleanName(item?.title) + '.S' + season + 'E' + number
    const caption = (item?.title || 'TV') + ' — S' + season + 'E' + number

    if (delivery === 'video') {
      await context.send({
        video:{ url:media.path },
        mimetype:'video/mp4',
        caption,
      })
    } else {
      await context.send({
        document:{ url:media.path },
        mimetype:'video/x-matroska',
        fileName:base + '.mkv',
        caption,
      })
    }
    return { delivered:true }
  } finally {
    await media.cleanup()
  }
}

export default {
  id:'streamingunity',
  name:'StreamingUnity',
  description:'TV episodes resolved to live HLS and delivered without video re-encoding.',
  fallbackOrder:10,
  brandAliases:['StreamingCommunity'],

  async run({
    action,
    query,
    item,
    season,
    seasonNumber,
    episode,
    episodeId,
    selection,
    range,
    quality = 'source',
    delivery = 'document',
    context,
  }) {
    if (action === 'search') {
      return { items:await searchTitles(query, 'tv') }
    }
    if (action === 'browse') {
      return { items:await browseTitles('tv') }
    }
    if (action === 'seasons') {
      return { seasons:await listSeasons(item) }
    }
    if (action === 'episodes') {
      const number = Number(seasonNumber || season?.number || 1)
      return {
        title:item?.title,
        seasonNumber:number,
        episodes:await listEpisodes(item, number),
      }
    }
    if (action === 'options') {
      const picked = episode || selection?.[0] || range?.start
      const id = picked?.id || episodeId
      if (!id) {
        return {
          qualities:['source','1080','720','480'],
          deliveries:['document','video'],
        }
      }
      const stream = await resolveStream(item, id)
      return {
        qualities:qualityList(stream),
        deliveries:['document','video'],
      }
    }
    if (action === 'download') {
      const number = Number(seasonNumber || season?.number || episode?.seasonNumber || 1)
      const picked = episode || { id:episodeId, number:episode?.number || '' }
      return deliverEpisode(item, number, picked, quality, delivery, context)
    }
    if (action === 'downloadRange') {
      const number = Number(seasonNumber || season?.number || 1)
      const episodes = await listEpisodes(item, number)
      const low = Math.min(Number(range?.start?.number), Number(range?.end?.number))
      const high = Math.max(Number(range?.start?.number), Number(range?.end?.number))
      if (!Number.isFinite(low) || !Number.isFinite(high)) throw new Error('StreamingUnity TV range is invalid.')
      const chosen = episodes.filter(row => {
        const value = Number(row.number)
        return value >= low && value <= high
      })
      if (!chosen.length) throw new Error('StreamingUnity found no episodes in that range.')
      for (const row of chosen) {
        await deliverEpisode(item, number, row, quality, delivery, context)
      }
      return { delivered:true, count:chosen.length }
    }
    throw new Error('Unsupported StreamingUnity TV action: ' + action)
  },
}
