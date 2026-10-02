import { inspectVaPlayer, materializeVaPlayer } from '../../providers/vaplayer.js'
import { browseMetadata, episodeRows, mediaDetails, searchMetadata, seasonRows } from '../../providers/screen-source-metadata.js'

function cleanName(value) {
  return String(value || 'episode').replace(/[\\/:*?"<>|]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0,120) || 'episode'
}

async function imdbIdentity(context, item) {
  const details = await mediaDetails(context, item, 'tv')
  const imdbId = String(details?.imdbId || '').trim()
  if (!imdbId) throw new Error('VaPlayer could not resolve an IMDb ID for this TV series.')
  return imdbId
}

async function deliverEpisode(item, seasonNumber, episode, quality, delivery, context) {
  const imdbId = await imdbIdentity(context, item)
  const season = Number(seasonNumber)
  const number = Number(episode?.number)
  if (!Number.isInteger(season) || season <= 0 || !Number.isInteger(number) || number <= 0) {
    throw new Error('VaPlayer TV episode identity is incomplete.')
  }
  const media = await materializeVaPlayer({ type:'tv', imdbId, season, episode:number }, quality, delivery)
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
  id:'vaplayer',
  name:'VaPlayer',
  description:'TV episodes resolved by IMDb identity and delivered copy-only from VaPlayer.',
  fallbackOrder:30,
  brandAliases:['VA Player'],

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
      if (!ep) return { qualities:['source','1080','720','480','360'], deliveries:['document','video'] }
      const imdbId = await imdbIdentity(context, item)
      const resolved = await inspectVaPlayer({ type:'tv', imdbId, season:s, episode:ep })
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
      if (!Number.isFinite(low) || !Number.isFinite(high)) throw new Error('VaPlayer TV range is invalid.')
      const chosen = rows.filter(row => Number(row.number) >= low && Number(row.number) <= high)
      if (!chosen.length) throw new Error('VaPlayer found no episodes in that range.')
      for (const row of chosen) await deliverEpisode(item, s, row, quality, delivery, context)
      return { delivered:true, count:chosen.length }
    }
    throw new Error('Unsupported VaPlayer TV action: ' + action)
  },
}
