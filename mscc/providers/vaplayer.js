import { inspectCandidates, materializeCandidates, qualitiesFor } from './stream-copy.js'

const API = 'https://streamdata.vaplayer.ru/api.php'
const EMBED_BASE = 'https://nextgencloudfabric.com'
const UA = 'Mozilla/5.0 (Linux; Android 16; SM-A165F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36'
const HTTP_TIMEOUT_MS = Math.max(5_000, Number(process.env.MSCC_VAPLAYER_HTTP_TIMEOUT_MS || 30_000))
const CACHE_TTL_MS = Math.max(10_000, Number(process.env.MSCC_VAPLAYER_CACHE_TTL_MS || 2 * 60_000))
const cache = new Map()

function normalizeImdb(value) {
  const imdb = String(value || '').trim()
  return /^tt\d{5,12}$/i.test(imdb) ? imdb.toLowerCase() : ''
}

function keyOf(type, imdbId, season, episode) {
  return [type, imdbId, season || '', episode || ''].join('|')
}

function cached(key) {
  const row = cache.get(key)
  return row && row.expiresAt > Date.now() ? row.value : null
}

function store(key, value) {
  cache.set(key, { value, expiresAt:Date.now() + CACHE_TTL_MS })
  return value
}

async function get(url, headers = {}) {
  const response = await fetch(url, {
    headers:{
      'user-agent':UA,
      'accept-language':'en-US,en;q=0.9',
      ...headers,
    },
    redirect:'follow',
    signal:AbortSignal.timeout(HTTP_TIMEOUT_MS),
  })
  if (!response.ok) throw new Error('VaPlayer HTTP ' + response.status)
  return response
}

export async function resolveVaPlayer({ type = 'movie', imdbId, season = 0, episode = 0 } = {}) {
  const imdb = normalizeImdb(imdbId)
  if (!imdb) throw new Error('VaPlayer requires a valid IMDb ID.')
  const kind = String(type || '').toLowerCase() === 'tv' ? 'tv' : 'movie'
  const seasonNumber = Number(season) || 0
  const episodeNumber = Number(episode) || 0
  if (kind === 'tv' && (!(seasonNumber > 0) || !(episodeNumber > 0))) {
    throw new Error('VaPlayer TV resolution requires season and episode numbers.')
  }

  const key = keyOf(kind, imdb, seasonNumber, episodeNumber)
  const saved = cached(key)
  if (saved) return saved

  const params = new URLSearchParams({ imdb, type:kind })
  let referer
  if (kind === 'tv') {
    params.set('season', String(seasonNumber))
    params.set('episode', String(episodeNumber))
    referer = EMBED_BASE + '/embed/tv/' + imdb + '/' + seasonNumber + '/' + episodeNumber
  } else {
    referer = EMBED_BASE + '/embed/movie/' + imdb
  }

  const response = await get(API + '?' + params, {
    Referer:referer,
    Origin:EMBED_BASE,
    Accept:'application/json,*/*',
  })
  let data
  try { data = await response.json() } catch { data = {} }
  const urls = Array.isArray(data?.data?.stream_urls)
    ? data.data.stream_urls.filter(value => /^https?:\/\//i.test(String(value || '')))
    : []
  if (!urls.length) throw new Error('VaPlayer returned no playable streams for this title.')

  const candidates = urls.slice(0, 8).map(url => ({
    url:String(url),
    headers:{
      Referer:EMBED_BASE + '/',
      Origin:EMBED_BASE,
      'User-Agent':UA,
    },
  }))
  return store(key, {
    type:kind,
    imdbId:imdb,
    season:seasonNumber,
    episode:episodeNumber,
    apiStatusCode:String(data?.status_code || ''),
    candidates,
  })
}

export async function inspectVaPlayer(input) {
  const resolved = await resolveVaPlayer(input)
  const inspected = await inspectCandidates(resolved.candidates)
  if (!inspected.length) throw new Error('VaPlayer streams were returned but none were playable.')
  return { ...resolved, inspected, qualities:qualitiesFor(inspected) }
}

export async function materializeVaPlayer(input, quality = 'source', delivery = 'document', options = {}) {
  const resolved = await inspectVaPlayer(input)
  return materializeCandidates(resolved.candidates, quality, delivery, {
    ...options,
    inspected:resolved.inspected,
  })
}

export const _test = { normalizeImdb, keyOf }
