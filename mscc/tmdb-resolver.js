const BASE_URL = 'https://api.themoviedb.org/3'
const DEFAULT_TTL_MS = 6 * 3600000
const MAX_CACHE = 256

function clean(value, max = 280) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, max)
}

function kindOf(value) {
  return String(value || '').trim().toLowerCase() === 'tv' ? 'tv' : 'movie'
}

function dedupe(values = []) {
  const seen = new Set()
  const out = []
  for (const value of values) {
    const item = clean(value)
    if (!item) continue
    const key = item.toLocaleLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    out.push(item)
  }
  return out
}

function yearOf(value) {
  const match = String(value || '').match(/^(\d{4})/)
  return match ? Number(match[1]) : 0
}

function compactMovie(item) {
  return {
    id:Number(item?.id || 0) || 0,
    type:'movie',
    title:clean(item?.title || item?.original_title),
    originalTitle:clean(item?.original_title),
    aliases:dedupe([item?.title, item?.original_title]),
    year:yearOf(item?.release_date),
    overview:clean(item?.overview, 800),
    posterPath:String(item?.poster_path || ''),
    releaseDate:String(item?.release_date || ''),
    imdbId:clean(item?.imdb_id || item?.external_ids?.imdb_id, 32),
  }
}

function compactTv(item) {
  return {
    id:Number(item?.id || 0) || 0,
    type:'tv',
    title:clean(item?.name || item?.original_name),
    originalTitle:clean(item?.original_name),
    aliases:dedupe([item?.name, item?.original_name]),
    year:yearOf(item?.first_air_date),
    overview:clean(item?.overview, 800),
    posterPath:String(item?.poster_path || ''),
    firstAirDate:String(item?.first_air_date || ''),
    numberOfSeasons:Number(item?.number_of_seasons || 0) || 0,
    numberOfEpisodes:Number(item?.number_of_episodes || 0) || 0,
    imdbId:clean(item?.imdb_id || item?.external_ids?.imdb_id, 32),
    seasons:(item?.seasons || []).map(season => ({
      id:Number(season?.id || 0) || 0,
      number:Number(season?.season_number),
      name:clean(season?.name || `Season ${season?.season_number}`),
      episodeCount:Number(season?.episode_count || 0) || 0,
      airDate:String(season?.air_date || ''),
    })).filter(season => Number.isFinite(season.number)),
  }
}

function retryAfterMs(response) {
  const raw = response?.headers?.get?.('retry-after')
  if (!raw) return 0
  const seconds = Number(raw)
  if (Number.isFinite(seconds)) return Math.max(0, seconds * 1000)
  const at = Date.parse(raw)
  return Number.isFinite(at) ? Math.max(0, at - Date.now()) : 0
}

export function createTmdbResolver({
  fetchImpl = globalThis.fetch,
  baseUrl = BASE_URL,
  readAccessToken = process.env.TMDB_READ_ACCESS_TOKEN || '',
  apiKey = process.env.TMDB_API_KEY || '',
  ttlMs = DEFAULT_TTL_MS,
  now = () => Date.now(),
} = {}) {
  const cache = new Map()
  let cooldownUntil = 0

  function enabled() {
    return Boolean(String(readAccessToken || '').trim() || String(apiKey || '').trim())
  }

  function cacheGet(key) {
    const found = cache.get(key)
    if (!found || now() - found.at >= ttlMs) return null
    return found.value
  }

  function cacheSet(key, value) {
    cache.set(key, { at:now(), value })
    while (cache.size > MAX_CACHE) cache.delete(cache.keys().next().value)
    return value
  }

  async function request(path, params = {}) {
    if (!enabled() || typeof fetchImpl !== 'function' || now() < cooldownUntil) return null

    const url = new URL(baseUrl + path)
    for (const [key, value] of Object.entries(params)) {
      if (value === '' || value === null || value === undefined) continue
      url.searchParams.set(key, String(value))
    }

    const headers = { accept:'application/json' }
    if (String(readAccessToken || '').trim()) {
      headers.authorization = 'Bearer ' + String(readAccessToken).trim()
    } else {
      url.searchParams.set('api_key', String(apiKey).trim())
    }

    try {
      const response = await fetchImpl(url, {
        method:'GET',
        headers,
        signal:AbortSignal.timeout(5000),
      })
      if (!response.ok) {
        if (response.status === 429) {
          cooldownUntil = now() + Math.max(retryAfterMs(response), 60000)
        } else if (response.status === 401 || response.status === 403) {
          cooldownUntil = now() + 5 * 60000
        }
        return null
      }
      return await response.json()
    } catch {
      return null
    }
  }

  async function search(query, type = 'movie') {
    const term = clean(query)
    const kind = kindOf(type)
    if (!term) return { query:'', aliases:[], matches:[], source:'none' }

    const key = `search|${kind}|${term.toLocaleLowerCase()}`
    const cached = cacheGet(key)
    if (cached) return { ...cached, source:'cache' }

    const fallback = { query:term, aliases:[term], matches:[], source:'fallback' }
    const payload = await request(`/search/${kind}`, {
      query:term,
      include_adult:false,
      language:'en-US',
      page:1,
    })
    if (!payload) return fallback

    const compact = kind === 'tv' ? compactTv : compactMovie
    const matches = (payload?.results || []).slice(0, 8).map(compact).filter(item => item.id && item.title)
    const aliases = dedupe([term, ...matches.flatMap(item => item.aliases)])

    return cacheSet(key, {
      query:term,
      aliases,
      matches,
      source:'tmdb',
    })
  }

  async function browse(type = 'movie') {
    const kind = kindOf(type)
    const key = `browse|${kind}`
    const cached = cacheGet(key)
    if (cached) return { ...cached, source:'cache' }

    const payload = await request(`/trending/${kind}/day`, {
      language:'en-US',
      page:1,
    })
    if (!payload) return { matches:[], source:'fallback' }

    const compact = kind === 'tv' ? compactTv : compactMovie
    const matches = (payload?.results || []).slice(0, 20).map(compact).filter(item => item.id && item.title)
    return cacheSet(key, { matches, source:'tmdb' })
  }

  async function details(id, type = 'movie') {
    const numericId = Number(id)
    const kind = kindOf(type)
    if (!Number.isInteger(numericId) || numericId <= 0) return null

    const key = `details|${kind}|${numericId}`
    const cached = cacheGet(key)
    if (cached) return { ...cached, source:'cache' }

    const payload = await request(`/${kind}/${numericId}`, {
      language:'en-US',
      append_to_response:'alternative_titles,external_ids',
    })
    if (!payload?.id) return null

    const item = kind === 'tv' ? compactTv(payload) : compactMovie(payload)
    const alt = kind === 'tv'
      ? (payload?.alternative_titles?.results || [])
      : (payload?.alternative_titles?.titles || [])
    item.aliases = dedupe([
      ...item.aliases,
      ...alt.map(value => value?.title),
    ])

    return cacheSet(key, { ...item, source:'tmdb' })
  }

  async function releaseState(id, type = 'tv') {
    const numericId = Number(id)
    const kind = kindOf(type)
    if (!Number.isInteger(numericId) || numericId <= 0) return null

    const key = `release|${kind}|${numericId}`
    const cached = cache.get(key)
    if (cached && now() - cached.at < 5 * 60000) return cached.value

    const payload = await request(`/${kind}/${numericId}`, { language:'en-US' })
    if (!payload?.id) return null

    let value
    if (kind === 'tv') {
      const latest = payload?.last_episode_to_air
      value = {
        kind:'episode',
        number:Number(latest?.episode_number || 0) || 0,
        season:Number(latest?.season_number || 0) || 0,
        releasedAtMs:Date.parse(String(latest?.air_date || '')) || 0,
        source:'tmdb',
      }
    } else {
      const releaseDate = String(payload?.release_date || '').trim()
      const releaseAtMs = Date.parse(releaseDate ? releaseDate + 'T00:00:00Z' : '') || 0
      value = {
        kind:'movie',
        number:releaseAtMs > 0 && releaseAtMs <= now() ? 1 : 0,
        season:0,
        releasedAtMs:releaseAtMs,
        source:'tmdb',
      }
    }

    cache.set(key, { at:now(), value })
    return value
  }

  async function seasonDetails(seriesId, seasonNumber) {
    const id = Number(seriesId)
    const season = Number(seasonNumber)
    if (!Number.isInteger(id) || id <= 0 || !Number.isInteger(season) || season < 0) return null

    const key = `season|${id}|${season}`
    const cached = cacheGet(key)
    if (cached) return { ...cached, source:'cache' }

    const payload = await request(`/tv/${id}/season/${season}`, { language:'en-US' })
    if (!payload) return null

    const value = {
      id:Number(payload?.id || 0) || 0,
      seriesId:id,
      seasonNumber:season,
      title:clean(payload?.name || `Season ${season}`),
      episodes:(payload?.episodes || []).map(episode => ({
        id:Number(episode?.id || 0) || 0,
        number:String(episode?.episode_number ?? ''),
        seasonNumber:Number(episode?.season_number ?? season),
        title:clean(episode?.name || `Episode ${episode?.episode_number}`),
        airDate:String(episode?.air_date || ''),
        runtime:Number(episode?.runtime || 0) || 0,
      })).filter(episode => episode.id && episode.number),
      source:'tmdb',
    }
    return cacheSet(key, value)
  }

  return {
    enabled,
    search,
    browse,
    details,
    releaseState,
    seasonDetails,
    health() {
      return {
        enabled:enabled(),
        coolingDown:now() < cooldownUntil,
        cacheSize:cache.size,
      }
    },
  }
}
