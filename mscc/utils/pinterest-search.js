const PINTEREST_ORIGIN = 'https://www.pinterest.com'
const SEARCH_RESOURCE = `${PINTEREST_ORIGIN}/resource/BaseSearchResource/get/`
const USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
  '(KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36'

export const PINTEREST_SEARCH_MAX = 150

function cleanQuery(value) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, 240)
}

function cleanUrl(value) {
  const raw = String(value || '').trim()
  if (!raw.toLowerCase().startsWith('https://')) return ''
  try {
    const url = new URL(raw)
    if (!/(^|\.)pinimg\.com$/i.test(url.hostname)) return ''
    return url.href
  } catch {
    return ''
  }
}

function mainImage(result) {
  const images = result?.images || {}
  const variants = Object.values(images)
    .map(value => ({
      url:cleanUrl(value?.url),
      area:Number(value?.width || 0) * Number(value?.height || 0),
    }))
    .filter(value => value.url)
    .sort((a, b) => b.area - a.area)

  return cleanUrl(images?.orig?.url) || variants[0]?.url || ''
}

function resultTitle(result) {
  const direct = String(result?.title || result?.grid_title || '').trim()
  if (direct.length >= 2) return direct
  const annotation = result?.pin_join?.visual_annotation
  if (Array.isArray(annotation) && annotation[0]) return String(annotation[0]).trim()
  return String(result?.name || result?.auto_alt_text || '').trim()
}

function responseBookmarks(payload) {
  const direct = payload?.resource_response?.bookmarks
  if (Array.isArray(direct)) return direct.filter(value => value && value !== '-end-').map(String)
  const singular = payload?.resource_response?.bookmark
  if (singular && singular !== '-end-') return [String(singular)]
  const nested = payload?.resource?.options?.bookmarks
  if (Array.isArray(nested)) return nested.filter(value => value && value !== '-end-').map(String)
  return []
}

function cookieHeader(response) {
  const getSetCookie = response?.headers?.getSetCookie
  const values = typeof getSetCookie === 'function'
    ? getSetCookie.call(response.headers)
    : []
  return values
    .map(value => String(value).split(';')[0])
    .filter(Boolean)
    .join('; ')
}

async function warmPinterest(query) {
  const sourceUrl = `/search/pins/?q=${encodeURIComponent(query)}&rs=typed`
  try {
    const response = await fetch(`${PINTEREST_ORIGIN}${sourceUrl}`, {
      headers: {
        Accept:'text/html,application/xhtml+xml',
        'Accept-Language':'en-US,en;q=0.9',
        'User-Agent':USER_AGENT,
      },
      redirect:'follow',
      signal:AbortSignal.timeout(20_000),
    })
    return { sourceUrl, cookie:cookieHeader(response) }
  } catch {
    return { sourceUrl, cookie:'' }
  }
}

async function fetchPinterestPage(query, bookmarks = [], session = null, pageSize = 50) {
  const warmed = session || await warmPinterest(query)
  const options = {
    query,
    scope:'pins',
    bookmarks:Array.isArray(bookmarks) ? bookmarks : [],
    page_size:Math.max(1, Math.min(50, Number(pageSize) || 50)),
    redux_normalize_feed:true,
    rs:'typed',
  }

  const url = new URL(SEARCH_RESOURCE)
  url.searchParams.set('source_url', warmed.sourceUrl || '/search/pins/')
  url.searchParams.set('data', JSON.stringify({ options, context:{} }))
  url.searchParams.set('_', String(Date.now()))

  const headers = {
    Accept:'application/json, text/javascript, */*; q=0.01',
    'Accept-Language':'en-US,en;q=0.9',
    'User-Agent':USER_AGENT,
    'X-Requested-With':'XMLHttpRequest',
    'X-Pinterest-AppState':'active',
    'X-Pinterest-Source-Url':warmed.sourceUrl || '/ideas/',
    'X-Pinterest-PWS-Handler':'www/search/[scope].js',
    Referer:`${PINTEREST_ORIGIN}/`,
  }
  if (warmed.cookie) headers.Cookie = warmed.cookie

  const response = await fetch(url, {
    headers,
    redirect:'follow',
    signal:AbortSignal.timeout(25_000),
  })

  if (!response.ok) {
    throw new Error(`Pinterest search returned HTTP ${response.status}.`)
  }

  const payload = await response.json()
  const results = payload?.resource_response?.data?.results
  if (!Array.isArray(results)) {
    throw new Error('Pinterest returned an unexpected search response.')
  }

  return {
    results,
    bookmarks:responseBookmarks(payload),
    session:warmed,
  }
}

export function pinterestPackName(query) {
  const first = cleanQuery(query).split(/\s+/).find(Boolean)
  return (first || 'Pinterest').slice(0, 80)
}

export function parsePinterestSearchArgs(args = [], {
  defaultCount = 30,
  minCount = 3,
  maxCount = PINTEREST_SEARCH_MAX,
} = {}) {
  const parts = Array.isArray(args)
    ? args.map(value => String(value || '').trim()).filter(Boolean)
    : String(args || '').trim().split(/\s+/).filter(Boolean)

  let count = Math.max(minCount, Math.min(maxCount, Number(defaultCount) || 30))
  if (parts.length) {
    const last = parts[parts.length - 1]
    if (/^\d{1,4}$/.test(last)) {
      count = Math.max(minCount, Math.min(maxCount, Number(last) || defaultCount))
      parts.pop()
    }
  }

  return {
    query:cleanQuery(parts.join(' ')),
    count,
  }
}

export async function searchPinterestImages(query, {
  limit = 50,
  candidateMultiplier = 3,
  maxPages = 12,
} = {}) {
  const clean = cleanQuery(query)
  if (!clean) throw new Error('Pinterest search needs a query.')

  const wanted = Math.max(1, Math.min(PINTEREST_SEARCH_MAX, Number(limit) || 50))
  const candidateTarget = Math.min(450, Math.max(wanted, wanted * Math.max(1, Number(candidateMultiplier) || 1)))

  const seen = new Set()
  const items = []
  let bookmarks = []
  let session = null

  for (let page = 0; page < maxPages && items.length < candidateTarget; page += 1) {
    const result = await fetchPinterestPage(clean, bookmarks, session, Math.min(50, candidateTarget - items.length))
    session = result.session

    for (const pin of result.results) {
      if (pin?.type === 'story') continue
      const imageUrl = mainImage(pin)
      if (!imageUrl || seen.has(imageUrl)) continue
      seen.add(imageUrl)
      items.push({
        id:String(pin?.id || '').trim(),
        title:resultTitle(pin),
        imageUrl,
        pinUrl:pin?.id ? `${PINTEREST_ORIGIN}/pin/${pin.id}/` : '',
      })
      if (items.length >= candidateTarget) break
    }

    const next = result.bookmarks
    if (!next.length || JSON.stringify(next) === JSON.stringify(bookmarks)) break
    bookmarks = next
  }

  return items
}
