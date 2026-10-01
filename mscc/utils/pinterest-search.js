const PINTEREST_ORIGIN = 'https://www.pinterest.com'
const SEARCH_RESOURCE = `${PINTEREST_ORIGIN}/resource/BaseSearchResource/get/`
const USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
  '(KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36'

function cleanQuery(value) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, 240)
}

function cleanUrl(value) {
  const raw = String(value || '').trim()
  if (!/^https:\/\/i.test(raw)) return ''
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
  const preferred = [
    images?.orig?.url,
    images?.['1200x']?.url,
    images?.['736x']?.url,
    images?.['564x']?.url,
    images?.['474x']?.url,
    images?.['236x']?.url,
  ]
  for (const value of preferred) {
    const url = cleanUrl(value)
    if (url) return url
  }
  return ''
}

function resultTitle(result) {
  const direct = String(result?.title || result?.grid_title || '').trim()
  if (direct.length >= 2) return direct
  const annotation = result?.pin_join?.visual_annotation
  if (Array.isArray(annotation) && annotation[0]) return String(annotation[0]).trim()
  return String(result?.name || result?.auto_alt_text || '').trim()
}

function nextBookmark(payload) {
  const direct = payload?.resource_response?.bookmark
  if (direct && direct !== '-end-') return String(direct)
  const nested = payload?.resource?.options?.bookmarks
  if (Array.isArray(nested) && nested[0] && nested[0] !== '-end-') return String(nested[0])
  return ''
}

async function fetchPinterestPage(query, bookmark = '') {
  const data = {
    options: {
      query,
      scope: 'pins',
      page_size: 50,
      bookmarks: [bookmark || ''],
    },
    context: {},
  }

  const url = new URL(SEARCH_RESOURCE)
  url.searchParams.set('source_url', `/search/pins/?q=${encodeURIComponent(query)}`)
  url.searchParams.set('data', JSON.stringify(data))

  const response = await fetch(url, {
    headers: {
      Accept: 'application/json, text/javascript, */*; q=0.01',
      'Accept-Language': 'en-US,en;q=0.9',
      'User-Agent': USER_AGENT,
      'X-Requested-With': 'XMLHttpRequest',
      'X-Pinterest-AppState': 'active',
      'X-Pinterest-Source-Url': '/ideas/',
      'X-Pinterest-PWS-Handler': 'www/ideas.js',
      Referer: `${PINTEREST_ORIGIN}/`,
    },
    signal: AbortSignal.timeout(25_000),
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
    bookmark: nextBookmark(payload),
  }
}

export function pinterestPackName(query) {
  const first = cleanQuery(query).split(/\s+/).find(Boolean)
  return (first || 'Pinterest').slice(0, 80)
}

export async function searchPinterestImages(query, {
  limit = 50,
  candidateMultiplier = 3,
  maxPages = 12,
} = {}) {
  const clean = cleanQuery(query)
  if (!clean) throw new Error('Pinterest search needs a query.')

  const wanted = Math.max(1, Math.min(150, Number(limit) || 50))
  const candidateTarget = Math.min(450, Math.max(wanted, wanted * Math.max(1, candidateMultiplier)))

  const seen = new Set()
  const items = []
  let bookmark = ''

  for (let page = 0; page < maxPages && items.length < candidateTarget; page += 1) {
    const result = await fetchPinterestPage(clean, bookmark)
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

    if (!result.bookmark || result.bookmark === bookmark) break
    bookmark = result.bookmark
  }

  return items
}

export const PINTEREST_SEARCH_MAX = 150
