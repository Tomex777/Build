import { inspectVixSrc, materializeVixSrc } from '../../providers/vixsrc.js'
import { browseMetadata, episodeRows, searchMetadata, seasonRows, tmdbIdOf } from '../../providers/screen-source-metadata.js'

function cleanName(value) {
  return String(value || 'episode').replace(/[\\/:*?"<>|]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0,120) || 'episode'
}

async function deliverEpisode(item, seasonNumber, episode, quality, delivery, context) {
  const tmdbId = tmdbIdOf(item)
  const season = Number(seasonNumber)
  const number = Number(episode?.number)
  if (!tmdbId || !Number.isInteger(season) || season <= 0 || !Number.isInteger(number) || number <= 0) {
    throw new Error('VixSrc TV identity is incomplete.')
  }
  const media = await materializeVixSrc({ type:'tv', tmdbId, season, episode:number }, quality, delivery)
  try {
    const s = String(season).padStart(2,'0')
    const e = String(number).padStart(2,'0')
    const caption = (item?.title || 'TV') + ' — S' + s + 'E' + e
    if (delivery === 'video') {
      await context.send({ video:{ url:media.path }, mimetype:'video/mp4', caption })
    } else {
      await context.send({
        document:{ url:media.path },
        mimetype:'video/x-matroska',
        fileName:cleanName(item?.title) + '.S' + s + 'E' + e + '.mkv',
        caption,
      })
    }
    return { delivered:true }
  } finally {
    await media.cleanup()
  }
}

export default {
  id:'vixsrc',
  name:'VixSrc',
  description:'TV episodes resolved by TMDB identity and delivered copy-only from VixSrc.',
  fallbackOrder:20,

  async run({
    action, query, item, season, seasonNumber, episode, episodeId, selection, range,
    quality = 'source', delivery = 'document', context,
  }) {
    if (action === 'search') return { items:await searchMetadata(context, query, 'tv') }
    if (action === 'browse') return { items:await browseMetadata(context, 'tv') }
    if (action === 'seasons') return { seasons:await seasonRows(context, item) }
    if (action === 'episodes') {
      const number = Number(seasonNumber || season?.number || 1)
      return { title:item?.title, seasonNumber:number, episodes:await episodeRows(context, item, number) }
    }
    if (action === 'options') {
      const picked = episode || selection?.[0] || range?.start
      const ep = Number(picked?.number || episodeId || 0)
      const s = Number(seasonNumber || season?.number || picked?.seasonNumber || 1)
      const tmdbId = tmdbIdOf(item)
      if (!tmdbId || !ep) return { qualities:['source','1080','720','480','360'], deliveries:['document','video'] }
      const resolved = await inspectVixSrc({ type:'tv', tmdbId, season:s, episode:ep })
      return { qualities:resolved.qualities, deliveries:['document','video'] }
    }
    if (action === 'download') {
      const s = Number(seasonNumber || season?.number || episode?.seasonNumber || 1)
      const picked = episode || { id:episodeId, number:episodeId }
      return deliverEpisode(item, s, picked, quality, delivery, context)
    }
    if (action === 'downloadRange') {
      const s = Number(seasonNumber || season?.number || 1)
      const rows = await episodeRows(context, item, s)
      const low = Math.min(Number(range?.start?.number), Number(range?.end?.number))
      const high = Math.max(Number(range?.start?.number), Number(range?.end?.number))
      if (!Number.isFinite(low) || !Number.isFinite(high)) throw new Error('VixSrc TV range is invalid.')
      const chosen = rows.filter(row => Number(row.number) >= low && Number(row.number) <= high)
      if (!chosen.length) throw new Error('VixSrc found no episodes in that range.')
      for (const row of chosen) await deliverEpisode(item, s, row, quality, delivery, context)
      return { delivered:true, count:chosen.length }
    }
    throw new Error('Unsupported VixSrc TV action: ' + action)
  },
}
