const ENDPOINT = 'https://graphql.anilist.co'
const DEFAULT_TTL_MS = 6 * 3600000
const MAX_CACHE = 256

const QUERY = `
query ($search: String!, $type: MediaType!) {
  Page(page: 1, perPage: 5) {
    media(search: $search, type: $type) {
      id
      idMal
      title {
        romaji
        english
        native
        userPreferred
      }
      synonyms
      format
      status
      seasonYear
      episodes
      siteUrl
    }
  }
}
`

function clean(value, max = 240) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, max)
}

function dedupeTitles(values = []) {
  const seen = new Set()
  const out = []
  for (const value of values) {
    const title = clean(value)
    if (!title) continue
    const key = title.toLocaleLowerCase()
    if (seen.has(key)) continue
    seen.add(key)
    out.push(title)
  }
  return out
}

function retryAfterMs(response) {
  const raw = response?.headers?.get?.('retry-after')
  if (!raw) return 0
  const seconds = Number(raw)
  if (Number.isFinite(seconds)) return Math.max(0, seconds * 1000)
  const at = Date.parse(raw)
  return Number.isFinite(at) ? Math.max(0, at - Date.now()) : 0
}

function mediaAliases(media) {
  return dedupeTitles([
    media?.title?.userPreferred,
    media?.title?.romaji,
    media?.title?.english,
    media?.title?.native,
    ...(Array.isArray(media?.synonyms) ? media.synonyms : []),
  ])
}

function compactMedia(media) {
  const aliases = mediaAliases(media)
  return {
    id:Number(media?.id || 0) || 0,
    idMal:Number(media?.idMal || 0) || 0,
    aliases,
    title:aliases[0] || '',
    format:String(media?.format || ''),
    status:String(media?.status || ''),
    seasonYear:Number(media?.seasonYear || 0) || 0,
    episodes:Number(media?.episodes || 0) || 0,
    siteUrl:String(media?.siteUrl || ''),
  }
}

export function createAniListResolver({
  fetchImpl = globalThis.fetch,
  endpoint = ENDPOINT,
  ttlMs = DEFAULT_TTL_MS,
  now = () => Date.now(),
} = {}) {
  const cache = new Map()
  let cooldownUntil = 0

  function cacheSet(key, value) {
    cache.set(key, { at:now(), value })
    while (cache.size > MAX_CACHE) cache.delete(cache.keys().next().value)
    return value
  }

  async function resolve(query, type = 'ANIME') {
    const search = clean(query)
    const mediaType = String(type || 'ANIME').trim().toUpperCase() === 'MANGA' ? 'MANGA' : 'ANIME'
    if (!search) return { query:'', aliases:[], matches:[], source:'none' }

    const key = mediaType + '|' + search.toLocaleLowerCase()
    const cached = cache.get(key)
    if (cached && now() - cached.at < ttlMs) return { ...cached.value, source:'cache' }

    const fallback = {
      query:search,
      aliases:[search],
      matches:[],
      source:'fallback',
    }
    if (typeof fetchImpl !== 'function' || now() < cooldownUntil) return fallback

    let response = null
    try {
      response = await fetchImpl(endpoint, {
        method:'POST',
        headers:{
          accept:'application/json',
          'content-type':'application/json',
          'user-agent':'MSCC/2.3 AniList title resolver',
        },
        body:JSON.stringify({
          query:QUERY,
          variables:{ search, type:mediaType },
        }),
        signal:AbortSignal.timeout(5000),
      })

      if (!response.ok) {
        if (response.status === 429) {
          cooldownUntil = now() + Math.max(retryAfterMs(response), 60000)
        } else if (response.status === 403) {
          cooldownUntil = now() + 5 * 60000
        }
        return fallback
      }

      const payload = await response.json()
      if (Array.isArray(payload?.errors) && payload.errors.length) return fallback

      const matches = (payload?.data?.Page?.media || [])
        .map(compactMedia)
        .filter(item => item.id && item.aliases.length)

      const aliases = dedupeTitles([
        search,
        ...matches.flatMap(item => item.aliases),
      ])

      return cacheSet(key, {
        query:search,
        aliases,
        matches,
        source:'anilist',
      })
    } catch {
      return fallback
    }
  }

  return {
    resolve,
    health() {
      return {
        coolingDown:now() < cooldownUntil,
        cacheSize:cache.size,
      }
    },
  }
}
