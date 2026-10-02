const SEARCH_URL = 'https://www.wikidata.org/w/api.php'
const SPARQL_URL = 'https://query.wikidata.org/sparql'
const DEFAULT_TTL_MS = 12 * 3600000
const MAX_CACHE = 256

function clean(value, max = 320) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, max)
}

function sparqlString(value) {
  return String(value || '').replace(/\\/g, '\\\\').replace(/"/g, '\\"')
}

function tmdbProperty(type) {
  return String(type || '').toLowerCase() === 'tv' ? 'P4983' : 'P4947'
}

function oppositeTmdbProperty(type) {
  return String(type || '').toLowerCase() === 'tv' ? 'P4947' : 'P4983'
}

function oppositeScreenType(type) {
  return String(type || '').toLowerCase() === 'tv' ? 'movie' : 'tv'
}

export function createAdaptationResolver({
  fetchImpl = globalThis.fetch,
  searchUrl = SEARCH_URL,
  sparqlUrl = SPARQL_URL,
  ttlMs = DEFAULT_TTL_MS,
  now = () => Date.now(),
} = {}) {
  const cache = new Map()
  let cooldownUntil = 0

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

  async function json(url, headers = {}) {
    if (typeof fetchImpl !== 'function' || now() < cooldownUntil) return null
    try {
      const response = await fetchImpl(url, {
        headers:{
          accept:'application/json',
          'user-agent':'MSCC/2.3 adaptation resolver',
          ...headers,
        },
        signal:AbortSignal.timeout(6000),
      })
      if (!response.ok) {
        if (response.status === 429) cooldownUntil = now() + 60000
        return null
      }
      return response.json()
    } catch {
      return null
    }
  }

  async function searchBookEntity(title, author = '') {
    const q = [clean(title), clean(author)].filter(Boolean).join(' ')
    if (!q) return null

    const key = 'book-search|' + q.toLocaleLowerCase()
    const cached = cacheGet(key)
    if (cached !== null) return cached

    const url = new URL(searchUrl)
    url.searchParams.set('action', 'wbsearchentities')
    url.searchParams.set('search', q)
    url.searchParams.set('language', 'en')
    url.searchParams.set('format', 'json')
    url.searchParams.set('limit', '8')
    url.searchParams.set('origin', '*')
    const payload = await json(url)
    const candidates = payload?.search || []
    const preferred = candidates.find(item =>
      /novel|book|literary|fiction|memoir|biograph/i.test(String(item?.description || ''))
    ) || candidates[0]
    const id = String(preferred?.id || '')
    cacheSet(key, id || null)
    return id || null
  }

  async function querySparql(query) {
    const url = new URL(sparqlUrl)
    url.searchParams.set('format', 'json')
    url.searchParams.set('query', query)
    return json(url)
  }

  async function bookToScreen({ title, author = '', wikidataId = '' } = {}) {
    const qid = String(wikidataId || '').trim() || await searchBookEntity(title, author)
    if (!/^Q\d+$/.test(qid)) return []

    const key = 'book-screen|' + qid
    const cached = cacheGet(key)
    if (cached) return cached

    const query = `
SELECT ?work ?workLabel ?movieId ?tvId WHERE {
  ?work wdt:P144 wd:${qid}.
  OPTIONAL { ?work wdt:P4947 ?movieId. }
  OPTIONAL { ?work wdt:P4983 ?tvId. }
  FILTER(BOUND(?movieId) || BOUND(?tvId))
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
}
LIMIT 12
`
    const payload = await querySparql(query)
    const rows = (payload?.results?.bindings || []).map(row => ({
      wikidataId:String(row?.work?.value || '').split('/').pop() || '',
      title:clean(row?.workLabel?.value),
      tmdbMovieId:Number(row?.movieId?.value || 0) || 0,
      tmdbTvId:Number(row?.tvId?.value || 0) || 0,
    })).filter(row => row.title && (row.tmdbMovieId || row.tmdbTvId))

    return cacheSet(key, rows)
  }

  async function screenToBooks({ tmdbId, type = 'movie' } = {}) {
    const id = Number(tmdbId)
    if (!Number.isInteger(id) || id <= 0) return []

    const property = tmdbProperty(type)
    const key = `screen-book|${property}|${id}`
    const cached = cacheGet(key)
    if (cached) return cached

    const query = `
SELECT ?source ?sourceLabel WHERE {
  ?screen wdt:${property} "${sparqlString(id)}".
  ?screen wdt:P144 ?source.
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
}
LIMIT 8
`
    const payload = await querySparql(query)
    const rows = (payload?.results?.bindings || []).map(row => ({
      wikidataId:String(row?.source?.value || '').split('/').pop() || '',
      title:clean(row?.sourceLabel?.value),
    })).filter(row => /^Q\d+$/.test(row.wikidataId) && row.title)

    return cacheSet(key, rows)
  }


  async function screenCounterparts({ tmdbId, type = 'movie' } = {}) {
    const id = Number(tmdbId)
    if (!Number.isInteger(id) || id <= 0) return []

    const sourceProperty = tmdbProperty(type)
    const targetProperty = oppositeTmdbProperty(type)
    const targetType = oppositeScreenType(type)
    const key = `screen-counterpart|${sourceProperty}|${targetProperty}|${id}`
    const cached = cacheGet(key)
    if (cached) return cached

    const query = `
SELECT DISTINCT ?other ?otherLabel ?otherId WHERE {
  ?screen wdt:${sourceProperty} "${sparqlString(id)}".
  {
    ?screen wdt:P144 ?anchor.
    ?other wdt:P144 ?anchor.
  } UNION {
    ?screen wdt:P179 ?anchor.
    ?other wdt:P179 ?anchor.
  } UNION {
    ?screen wdt:P155 ?other.
  } UNION {
    ?screen wdt:P156 ?other.
  } UNION {
    ?other wdt:P155 ?screen.
  } UNION {
    ?other wdt:P156 ?screen.
  }
  FILTER(?other != ?screen)
  ?other wdt:${targetProperty} ?otherId.
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
}
LIMIT 8
`
    const payload = await querySparql(query)
    const rows = (payload?.results?.bindings || []).map(row => ({
      wikidataId:String(row?.other?.value || '').split('/').pop() || '',
      title:clean(row?.otherLabel?.value),
      tmdbId:Number(row?.otherId?.value || 0) || 0,
      type:targetType,
    })).filter(row => /^Q\d+$/.test(row.wikidataId) && row.title && row.tmdbId)

    return cacheSet(key, rows)
  }

  return {
    searchBookEntity,
    bookToScreen,
    screenToBooks,
    screenCounterparts,
    health() {
      return { coolingDown:now() < cooldownUntil, cacheSize:cache.size }
    },
  }
}
