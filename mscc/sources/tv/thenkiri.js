import { browseTheNkiri, materializeTheNkiriRelease, searchTheNkiri } from '../../providers/thenkiri.js'
import { episodeRows, seasonRows } from '../../providers/screen-source-metadata.js'

function cleanName(value) {
  return String(value || 'episode')
    .replace(/[\\/:*?"<>|]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0,120) || 'episode'
}

async function deliverEpisode({
  context, item, seasonNumber, episodeNumber, materialize = materializeTheNkiriRelease,
}) {
  if (typeof context?.send !== 'function') {
    throw new Error('TheNkiri TV delivery requires a message context.')
  }
  const season = Number(seasonNumber)
  const episode = Number(episodeNumber)
  if (!Number.isInteger(season) || season <= 0 || !Number.isInteger(episode) || episode <= 0) {
    throw new Error('TheNkiri TV episode identity is incomplete.')
  }

  const media = await materialize({
    item,
    title:item?.title,
    type:'tv',
    season,
    episode,
  })
  try {
    const s = String(season).padStart(2, '0')
    const e = String(episode).padStart(2, '0')
    const fileName = cleanName(item?.title) + '.S' + s + 'E' + e + '.mkv'
    await context.send({
      document:{ url:media.path },
      mimetype:'video/x-matroska',
      fileName,
      caption:(item?.title || 'TV') + ' — S' + s + 'E' + e,
    })
    return { delivered:true, fileName }
  } finally {
    await media.cleanup()
  }
}

export default {
  id:'thenkiri',
  name:'TheNkiri',
  description:'TheNkiri TV catalog with verified Downloadwella episode delivery where supported.',
  fallbackOrder:30,

  async run({
    action, query, item, season, seasonNumber, episode, episodeId, range, context,
  }) {
    if (action === 'search') return { items:await searchTheNkiri(query, 'tv') }
    if (action === 'browse') return { items:await browseTheNkiri('tv') }
    if (action === 'seasons') return { seasons:await seasonRows(context, item) }
    if (action === 'episodes') {
      const number = Number(seasonNumber || season?.number || 1)
      return { title:item?.title, seasonNumber:number, episodes:await episodeRows(context, item, number) }
    }
    if (action === 'options') {
      return { qualities:['source'], deliveries:['document'] }
    }
    if (action === 'download') {
      const s = Number(seasonNumber || season?.number || episode?.seasonNumber || 1)
      const e = Number(episode?.number || episodeId || 0)
      return deliverEpisode({ context, item, seasonNumber:s, episodeNumber:e })
    }
    if (action === 'downloadRange') {
      const s = Number(seasonNumber || season?.number || 1)
      const start = Number(range?.start?.number)
      const end = Number(range?.end?.number)
      if (!Number.isInteger(s) || s <= 0 || !Number.isInteger(start) || start <= 0 ||
          !Number.isInteger(end) || end <= 0) {
        throw new Error('TheNkiri TV range is invalid.')
      }
      const low = Math.min(start, end)
      const high = Math.max(start, end)
      const rows = await episodeRows(context, item, s)
      const chosen = rows.filter(row => {
        const number = Number(row?.number)
        return Number.isInteger(number) && number >= low && number <= high
      })
      if (!chosen.length) throw new Error('TheNkiri found no episodes in that range.')
      for (const row of chosen) {
        await deliverEpisode({
          context,
          item,
          seasonNumber:s,
          episodeNumber:Number(row.number),
        })
      }
      return { delivered:true, count:chosen.length }
    }
    throw new Error('Unsupported TheNkiri TV action: ' + action)
  },
}

export const _test = { cleanName, deliverEpisode }
