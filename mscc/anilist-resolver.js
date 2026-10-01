const ENDPOINT = 'https://graphql.anilist.co'
const DEFAULT_TTL_MS = 6 * 3600000
const MAX_CACHE = 256

const SEARCH_QUERY = `
query ($search: String!, $type: MediaType!) {
  Page(page: 1, perPage: 5) {
    media(search: $search, type: $type) {
      id
      idMal
      type
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
      chapters
      volumes
      siteUrl
    }
  }
}
`

const MEDIA_QUERY = `
query ($id: Int!, $type: MediaType!) {
  Media(id: $id, type: $type) {
    id
    idMal
    type
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
    chapters
    volumes
    siteUrl
    relations {
      edges {
        relationType(version: 2)
        node {
          id
          idMal
          type
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
          chapters
          volumes
          siteUrl
        }
      }
    }
  }
}
`

function clean(value, max = 240) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, max)
}

function mediaTypeOf(value) {
  return String(value || 'ANIME').trim().toUpperCase() === 'MANGA' ? 'MANGA' : 'ANIME'
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

function compactMedia(media, { includeRelations = true } = {}) {
  if (!media) return null
  const aliases = mediaAliases(media)
  const item = {
    id:Number(media?.id || 0) || 0,
    idMal:Number(media?.idMal || 0) || 0,
    type:mediaTypeOf(media?.type),
    aliases,
    title:aliases[0] || '',
    format:String(media?.format || ''),
    status:String(media?.status || ''),
    seasonYear:Number(media?.seasonYear || 0) || 0,
    episodes:Number(media?.episodes || 0) || 0,
    chapters:Number(media?.chapters || 0) || 0,
    volumes:Number(media?.volumes || 0) || 0,
    siteUrl:String(media?.siteUrl || ''),
  }

  if (includeRelations) {
    item.relations = (media?.relations?.edges || [])
      .map(edge => {
        const node = compactMedia(edge?.node, { includeRelations:false })
        if (!node?.id) return null
        return {
          relationType:String(edge?.relationType || ''),
          node,
        }
      })
      .filter(Boolean)
  }

  return item
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

  function cacheGet(key) {
    const cached = cache.get(key)
    if (!cached || now() - cached.at >= ttlMs) return null
    return cached.value
  }

  function fallbackFor(query) {
    return {
      query:clean(query),
      aliases:clean(query) ? [clean(query)] : [],
      matches:[],
      source:'fallback',
    }
  }

  async function request(query, variables) {
    if (typeof fetchImpl !== 'function' || now() < cooldownUntil) return null
    try {
      const response = await fetchImpl(endpoint, {
        method:'POST',
        headers:{
          accept:'application/json',
          'content-type':'application/json',
          'user-agent':'MSCC/2.3 AniList media resolver',
        },
        body:JSON.stringify({ query, variables }),
        signal:AbortSignal.timeout(5000),
      })

      if (!response.ok) {
        if (response.status === 429) {
          cooldownUntil = now() + Math.max(retryAfterMs(response), 60000)
        } else if (response.status === 403) {
          cooldownUntil = now() + 5 * 60000
        }
        return null
      }

      const payload = await response.json()
      if (Array.isArray(payload?.errors) && payload.errors.length) return null
      return payload?.data || null
    } catch {
      return null
    }
  }

  async function resolve(query, type = 'ANIME') {
    const search = clean(query)
    const mediaType = mediaTypeOf(type)
    if (!search) return { query:'', aliases:[], matches:[], source:'none' }

    const key = 'search|' + mediaType + '|' + search.toLocaleLowerCase()
    const cached = cacheGet(key)
    if (cached) return { ...cached, source:'cache' }

    const fallback = fallbackFor(search)
    const data = await request(SEARCH_QUERY, { search, type:mediaType })
    if (!data) return fallback

    const matches = (data?.Page?.media || [])
      .map(media => compactMedia(media, { includeRelations:false }))
      .filter(item => item?.id && item.aliases.length)

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
  }

  async function getMedia(id, type = 'ANIME') {
    const numericId = Number(id)
    const mediaType = mediaTypeOf(type)
    if (!Number.isInteger(numericId) || numericId <= 0) return null

    const key = 'media|' + mediaType + '|' + numericId
    const cached = cacheGet(key)
    if (cached) return { ...cached, source:'cache' }

    const data = await request(MEDIA_QUERY, { id:numericId, type:mediaType })
    const media = compactMedia(data?.Media)
    if (!media?.id) return null

    return cacheSet(key, {
      ...media,
      source:'anilist',
    })
  }

  return {
    resolve,
    getMedia,
    health() {
      return {
        coolingDown:now() < cooldownUntil,
        cacheSize:cache.size,
      }
    },
  }
}
