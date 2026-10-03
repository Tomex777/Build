import * as cheerio from 'cheerio'

const UA = 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140 Mobile Safari/537.36'
const TIMEOUT_MS = 15_000

function clean(value, max = 500) {
  return String(value ?? '').replace(/\s+/g, ' ').trim().slice(0, max)
}

async function fetchHtml(url, { method = 'GET', body = undefined, headers = {} } = {}) {
  const response = await fetch(url, {
    method,
    headers:{
      'user-agent':UA,
      'accept':'text/html,application/xhtml+xml',
      'accept-language':'en-US,en;q=0.9',
      ...headers,
    },
    body,
    redirect:'follow',
    signal:AbortSignal.timeout(TIMEOUT_MS),
  })
  const text = await response.text()
  if (!response.ok) throw new Error(`Search HTTP ${response.status}.`)
  return text
}

function unwrapDuckDuckGoUrl(value) {
  const raw = String(value || '').trim()
  if (!raw) return ''
  try {
    const url = new URL(raw, 'https://duckduckgo.com')
    if (url.hostname.endsWith('duckduckgo.com')) {
      const uddg = url.searchParams.get('uddg')
      if (uddg) return decodeURIComponent(uddg)
    }
    return url.href
  } catch {
    return ''
  }
}

export function parseDuckDuckGoHtml(html, { limit = 8 } = {}) {
  const $ = cheerio.load(String(html || ''))
  const results = []
  const seen = new Set()

  $('.result').each((_, node) => {
    if (results.length >= limit) return false
    const root = $(node)
    const link = root.find('.result__a').first()
    const title = clean(link.text(), 180)
    const url = unwrapDuckDuckGoUrl(link.attr('href'))
    const snippet = clean(root.find('.result__snippet').first().text(), 450)
    if (!title || !/^https?:\/\//i.test(url) || seen.has(url)) return
    seen.add(url)
    let domain = ''
    try { domain = new URL(url).hostname.replace(/^www\./, '') } catch {}
    results.push({ title, url, snippet, domain })
  })

  if (!results.length) {
    $('a.result-link').each((_, node) => {
      if (results.length >= limit) return false
      const link = $(node)
      const title = clean(link.text(), 180)
      const url = unwrapDuckDuckGoUrl(link.attr('href'))
      if (!title || !/^https?:\/\//i.test(url) || seen.has(url)) return
      seen.add(url)
      let domain = ''
      try { domain = new URL(url).hostname.replace(/^www\./, '') } catch {}
      results.push({ title, url, snippet:'', domain })
    })
  }

  return results
}

export async function searchWeb(query, { limit = 8 } = {}) {
  const term = clean(query, 220)
  if (!term) return []
  const safeLimit = Math.max(1, Math.min(12, Number(limit) || 8))
  const urls = [
    'https://html.duckduckgo.com/html/?q=' + encodeURIComponent(term),
    'https://lite.duckduckgo.com/lite/?q=' + encodeURIComponent(term),
  ]

  let lastError = null
  for (const url of urls) {
    try {
      const html = await fetchHtml(url)
      const results = parseDuckDuckGoHtml(html, { limit:safeLimit })
      if (results.length) return results
    } catch (error) {
      lastError = error
    }
  }

  if (lastError) throw lastError
  return []
}

function safeJson(value) {
  try { return JSON.parse(String(value || '')) } catch { return null }
}

function normalizeImageMeta(meta = {}) {
  const imageUrl = clean(meta.murl || meta.mediaUrl || meta.imgurl, 2000)
  const thumbnailUrl = clean(meta.turl || meta.thumbnailUrl, 2000)
  const pageUrl = clean(meta.purl || meta.pageUrl || meta.surl, 2000)
  const title = clean(meta.t || meta.title || '', 180)
  const source = clean(meta.desc || meta.site || '', 120)
  if (!/^https?:\/\//i.test(imageUrl)) return null
  return { imageUrl, thumbnailUrl, pageUrl, title, source }
}

export function parseBingImageHtml(html, { limit = 20 } = {}) {
  const $ = cheerio.load(String(html || ''))
  const results = []
  const seen = new Set()

  $('a.iusc').each((_, node) => {
    if (results.length >= limit) return false
    const meta = safeJson($(node).attr('m'))
    const item = normalizeImageMeta(meta)
    if (!item || seen.has(item.imageUrl)) return
    seen.add(item.imageUrl)
    results.push(item)
  })

  if (results.length) return results

  const source = String(html || '')
  const re = /"murl":"((?:\\.|[^"\\])+)"/g
  let match
  while ((match = re.exec(source)) && results.length < limit) {
    let imageUrl = ''
    try { imageUrl = JSON.parse('"' + match[1] + '"') } catch {}
    if (!/^https?:\/\//i.test(imageUrl) || seen.has(imageUrl)) continue
    seen.add(imageUrl)
    results.push({ imageUrl, thumbnailUrl:'', pageUrl:'', title:'', source:'' })
  }
  return results
}

export async function searchImages(query, { limit = 20 } = {}) {
  const term = clean(query, 220)
  if (!term) return []
  const safeLimit = Math.max(1, Math.min(30, Number(limit) || 20))
  const url = 'https://www.bing.com/images/search?q=' + encodeURIComponent(term) + '&form=HDRSC3&first=1&tsc=ImageBasicHover'
  const html = await fetchHtml(url, {
    headers:{ referer:'https://www.bing.com/' },
  })
  return parseBingImageHtml(html, { limit:safeLimit })
}
