function tmdbIdOf(item) {
  const direct = Number(item?.tmdbId || 0)
  if (Number.isInteger(direct) && direct > 0) return direct
  const match = /^tmdb:(\d+)$/.exec(String(item?.id || ''))
  return match ? Number(match[1]) : 0
}

function itemFrom(match) {
  const id = Number(match?.id || 0)
  if (!Number.isInteger(id) || id <= 0) return null
  const year = Number(match?.year || 0) || 0
  return {
    id:'tmdb:' + id,
    title:String(match?.title || match?.originalTitle || '').trim(),
    description:year ? String(year) : '',
    tmdbId:id,
  }
}

export async function searchMetadata(context, query, type) {
  if (typeof context?.resolveTmdbTitles !== 'function') return []
  const result = await context.resolveTmdbTitles(String(query || '').trim(), type)
  return (result?.matches || []).map(itemFrom).filter(item => item?.title)
}

export async function browseMetadata(context, type) {
  if (typeof context?.browseTmdbMedia !== 'function') return []
  const result = await context.browseTmdbMedia(type)
  return (result?.matches || []).map(itemFrom).filter(item => item?.title)
}

export async function mediaDetails(context, item, type) {
  const tmdbId = tmdbIdOf(item)
  if (!tmdbId || typeof context?.resolveTmdbMedia !== 'function') return null
  return context.resolveTmdbMedia(tmdbId, type)
}

export async function seasonRows(context, item) {
  const details = await mediaDetails(context, item, 'tv')
  return (details?.seasons || [])
    .filter(row => Number(row?.number) > 0)
    .map(row => ({
      id:String(row?.id || row?.number),
      number:Number(row?.number),
      title:String(row?.name || 'Season ' + row?.number),
      episodeCount:Number(row?.episodeCount || 0) || 0,
    }))
}

export async function episodeRows(context, item, seasonNumber) {
  const tmdbId = tmdbIdOf(item)
  const season = Number(seasonNumber)
  if (!tmdbId || !Number.isInteger(season) || season <= 0 || typeof context?.resolveTmdbSeason !== 'function') return []
  const details = await context.resolveTmdbSeason(tmdbId, season)
  return (details?.episodes || []).map(row => ({
    id:String(row?.id || row?.number),
    number:String(row?.number || ''),
    seasonNumber:season,
    title:String(row?.title || 'Episode ' + row?.number),
  })).filter(row => row.number)
}

export { tmdbIdOf }

export const _test = { itemFrom, tmdbIdOf }
