const BASES = String(process.env.MSCC_TFPDL_BASES || 'https://tfpdl.com,https://tfp.re')
  .split(',')
  .map(value => value.trim().replace(/\/$/, ''))
  .filter(Boolean)
const UA = 'Mozilla/5.0 (Linux; Android 16; SM-A165F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36'
const HTTP_TIMEOUT_MS = Math.max(5_000, Number(process.env.MSCC_TFPDL_HTTP_TIMEOUT_MS || 25_000))
const CACHE_TTL_MS = Math.max(15_000, Number(process.env.MSCC_TFPDL_CACHE_TTL_MS || 5 * 60_000))
const cache = new Map()

function decodeEntities(value) {
  return String(value || '')
    .replace(/&amp;/gi, '&')
    .replace(/&quot;/gi, '"')
    .replace(/&#0*39;|&apos;/gi, "'")
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
}

function stripHtml(value) {
  return decodeEntities(String(value || '').replace(/<[^>]*>/g, ' '))
    .replace(/\s+/g, ' ')
    .trim()
}

function normalizeWords(value) {
  return stripHtml(value)
    .toLowerCase()
    .replace(/\b(?:tfpdl|web[- .]?dl|webrip|bluray|brrip|hdrip|dvdrip|hdtv|x26[45]|h26[45]|hevc|av1|ddp?\d(?:\.\d)?|aac|dual|dubbed|extended|unrated|remux)\b/gi, ' ')
    .replace(/\b(?:2160p|1080p|720p|576p|480p|360p)\b/gi, ' ')
    .replace(/[^a-z0-9]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function yearOf(value) {
  const matches = String(value || '').match(/\b(?:19|20)\d{2}\b/g) || []
  return matches.length ? Number(matches.at(-1)) : 0
}

function qualityOf(value) {
  const match = String(value || '').match(/\b(2160|1080|720|576|480|360)p\b/i)
  return match ? match[1] : ''
}

function episodeOf(value) {
  const text = String(value || '')
  const se = /\bS(\d{1,2})E(\d{1,3})\b/i.exec(text)
  if (se) return { season:Number(se[1]), episode:Number(se[2]) }
  const e = /\bE(?:P(?:ISODE)?)?[ ._-]?(\d{1,3})\b/i.exec(text)
  return e ? { season:0, episode:Number(e[1]) } : null
}

function seasonOf(value) {
  const match = /\bS(?:EASON)?[ ._-]?(\d{1,2})\b/i.exec(String(value || ''))
  return match ? Number(match[1]) : 0
}

function titleScore(postTitle, query, expectedYear = 0) {
  const post = normalizeWords(postTitle)
  const wanted = normalizeWords(query)
  if (!post || !wanted) return 0
  const p = post.split(' ').filter(Boolean)
  const q = wanted.split(' ').filter(Boolean)
  if (!q.length) return 0
  const set = new Set(p)
  const matched = q.filter(word => set.has(word)).length
  const coverage = matched / q.length
  let score = coverage
  if (post === wanted) score += 1
  else if (post.startsWith(wanted + ' ') || post.includes(' ' + wanted + ' ')) score += 0.35
  const year = yearOf(postTitle)
  if (expectedYear > 0) score += year === Number(expectedYear) ? 0.5 : -0.4
  return score
}

function wrappersFromHtml(html) {
  const source = decodeEntities(html)
  const rows = []
  const re = /<a\b[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi
  for (const match of source.matchAll(re)) {
    const href = match[1]
    if (!/\/tfpdl\?/i.test(href)) continue
    rows.push({
      text:stripHtml(match[2]),
      host:(() => { try { return new URL(href, 'https://tfpdl.com').hostname } catch { return '' } })(),
    })
  }
  return rows
}

function normalizePost(post, query = '', expectedYear = 0) {
  const title = stripHtml(post?.title?.rendered || post?.title || '')
  const content = String(post?.content?.rendered || '')
  const episode = episodeOf(title)
  const season = seasonOf(title)
  return {
    id:String(post?.id || ''),
    title,
    slug:String(post?.slug || ''),
    date:String(post?.date || ''),
    year:yearOf(title),
    quality:qualityOf(title),
    type:episode || season ? 'tv' : 'movie',
    season:episode?.season || season || 0,
    episode:episode?.episode || 0,
    wrapperCount:wrappersFromHtml(content).length,
    titleScore:query ? titleScore(title, query, expectedYear) : 0,
  }
}

async function fetchJson(url) {
  const response = await fetch(url, {
    headers:{ 'user-agent':UA, accept:'application/json,*/*' },
    redirect:'follow',
    signal:AbortSignal.timeout(HTTP_TIMEOUT_MS),
  })
  if (!response.ok) throw new Error('TFPDL HTTP ' + response.status)
  const body = await response.text()
  let json
  try { json = JSON.parse(body) } catch { throw new Error('TFPDL did not return JSON.') }
  return { response, json }
}

export async function searchTfpdlCatalog(query, { year = 0, limit = 20 } = {}) {
  const clean = String(query || '').trim()
  if (!clean) return []
  const key = 'search|' + clean.toLowerCase() + '|' + Number(year || 0)
  const saved = cache.get(key)
  if (saved && saved.expiresAt > Date.now()) return saved.value

  let lastError
  for (const base of BASES) {
    try {
      const url = new URL(base + '/wp-json/wp/v2/posts')
      url.searchParams.set('search', clean + (year ? ' ' + year : ''))
      url.searchParams.set('per_page', String(Math.max(1, Math.min(100, Number(limit) || 20))))
      url.searchParams.set('_fields', 'id,title,content,date,slug')
      const { json } = await fetchJson(url.href)
      const rows = (Array.isArray(json) ? json : [])
        .map(post => normalizePost(post, clean, year))
        .filter(row => row.id && row.title)
        .sort((a,b) => b.titleScore - a.titleScore || b.year - a.year)
      cache.set(key, { value:rows, expiresAt:Date.now() + CACHE_TTL_MS })
      return rows
    } catch (error) {
      lastError = error
    }
  }
  throw lastError || new Error('TFPDL catalog search failed.')
}

export async function tfpdlHealth() {
  let lastError
  for (const base of BASES) {
    try {
      const url = new URL(base + '/wp-json/wp/v2/posts')
      url.searchParams.set('per_page', '10')
      url.searchParams.set('orderby', 'date')
      url.searchParams.set('order', 'desc')
      url.searchParams.set('_fields', 'id,title,content,date,slug')
      const { response, json } = await fetchJson(url.href)
      const rows = Array.isArray(json) ? json.map(post => normalizePost(post)) : []
      return {
        healthy:response.ok && rows.length > 0,
        base:new URL(response.url).origin,
        postCount:rows.length,
        postsWithWrappers:rows.filter(row => row.wrapperCount > 0).length,
        newestDate:rows[0]?.date || '',
      }
    } catch (error) {
      lastError = error
    }
  }
  return { healthy:false, error:String(lastError?.message || lastError || 'unknown') }
}

export const _test = {
  decodeEntities,
  stripHtml,
  normalizeWords,
  yearOf,
  qualityOf,
  episodeOf,
  seasonOf,
  titleScore,
  wrappersFromHtml,
  normalizePost,
}
