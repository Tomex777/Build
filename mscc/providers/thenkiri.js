const BASE = 'https://thenkiri.com'
const SITE_BASES = [BASE, 'https://nkiri.ink']
const SITE_HOSTS = new Set(['thenkiri.com', 'www.thenkiri.com', 'nkiri.ink', 'www.nkiri.ink'])
const UA = 'Mozilla/5.0 (Linux; Android 16; SM-A165F) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36'
import { materializeCandidates } from './stream-copy.js'

const FILE_HOSTS = new Map([
  ['downloadwella.com', 'downloadwella'],
  ['www.downloadwella.com', 'downloadwella'],
  ['wetafiles.com', 'wetafiles'],
  ['www.wetafiles.com', 'wetafiles'],
])

function decodeEntities(value) {
  return String(value || '')
    .replace(/&amp;/g, '&')
    .replace(/&#038;/g, '&')
    .replace(/&#8211;|&ndash;/g, '–')
    .replace(/&#8212;|&mdash;/g, '—')
    .replace(/&#039;|&apos;/g, "'")
    .replace(/&quot;/g, '"')
    .replace(/&nbsp;/g, ' ')
}

function stripHtml(value) {
  return decodeEntities(String(value || '').replace(/<[^>]+>/g, ' '))
    .replace(/\s+/g, ' ')
    .trim()
}

function extractYear(value) {
  const years = String(value || '').match(/\b(?:19|20)\d{2}\b/g) || []
  return years.length ? Number(years[0]) : 0
}

function inferType(title, content = '') {
  const text = title + ' ' + stripHtml(content)
  return /\bS\d{1,2}(?:E\d{1,3})?\b|\bTV Series\b|\bComplete\s+TV\s+Series\b/i.test(text)
    ? 'tv'
    : 'movie'
}

function cleanTitle(value) {
  return stripHtml(value)
    .replace(/^Download\s+/i, '')
    .replace(/\s*\|\s*[^|]+$/i, '')
    .trim()
}

function cleanSeriesTitle(value) {
  return cleanTitle(value)
    .replace(/\s+S\d{1,2}\b.*$/i, '')
    .replace(/\s*\((?:Complete|Episode[^)]*Added|New Episode Added)\)\s*$/i, '')
    .trim()
}

function hostKind(value) {
  try {
    return FILE_HOSTS.get(new URL(decodeEntities(value)).hostname.toLowerCase()) || ''
  } catch {
    return ''
  }
}

function mediaNumbers(value) {
  const text = String(value || '')
  const pair = /\bS(\d{1,2})E(\d{1,3})\b/i.exec(text)
  if (pair) return { season:Number(pair[1]), episode:Number(pair[2]) }
  const season = /\bS(\d{1,2})\b/i.exec(text)
  const episode = /\bE(?:P)?(\d{1,3})\b/i.exec(text)
  return {
    season:season ? Number(season[1]) : 0,
    episode:episode ? Number(episode[1]) : 0,
  }
}

function fileNameFromUrl(value) {
  try {
    const path = new URL(value).pathname.split('/').filter(Boolean).at(-1) || ''
    return decodeURIComponent(path).replace(/\.html$/i, '')
  } catch {
    return ''
  }
}

function cookieValues(response) {
  if (typeof response?.headers?.getSetCookie === 'function') return response.headers.getSetCookie()
  const single = response?.headers?.get?.('set-cookie')
  return single ? [single] : []
}

function mergeCookies(jar, response) {
  for (const raw of cookieValues(response)) {
    const first = String(raw || '').split(';', 1)[0]
    const eq = first.indexOf('=')
    if (eq <= 0) continue
    jar.set(first.slice(0, eq).trim(), first.slice(eq + 1).trim())
  }
}

function cookieHeader(jar) {
  return [...jar.entries()].map(([key, value]) => key + '=' + value).join('; ')
}

async function downloadwellaRequest(url, { method = 'GET', body = null, referer = '', jar } = {}) {
  const headers = {
    'user-agent':UA,
    accept:'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'accept-language':'en-US,en;q=0.9',
  }
  if (referer) headers.referer = referer
  if (body !== null) headers['content-type'] = 'application/x-www-form-urlencoded'
  const cookies = jar ? cookieHeader(jar) : ''
  if (cookies) headers.cookie = cookies
  const response = await fetch(url, {
    method,
    headers,
    body,
    redirect:'follow',
    signal:AbortSignal.timeout(30000),
  })
  if (jar) mergeCookies(jar, response)
  if (!response.ok) throw new Error('Downloadwella HTTP ' + response.status)
  return response
}

function attr(tag, name) {
  const match = new RegExp('\\b' + String(name) + '\\s*=\\s*["\\\']([^"\\\']*)', 'i').exec(String(tag || ''))
  return match ? decodeEntities(match[1]) : ''
}

function downloadwellaForm(html, baseUrl) {
  const source = String(html || '')
  const forms = source.match(new RegExp('<form\\b[\\s\\S]*?<\\/form>', 'gi')) || []
  for (const form of forms) {
    const inputs = form.match(new RegExp('<input\\b[^>]*>', 'gi')) || []
    const fields = {}
    for (const input of inputs) {
      const name = attr(input, 'name')
      if (name) fields[name] = attr(input, 'value')
    }
    const op = String(fields.op || '').toLowerCase()
    const hasFreeFields = Boolean(fields.id && fields.rand && Object.prototype.hasOwnProperty.call(fields, 'method_free'))
    if (op !== 'download2' && !hasFreeFields) continue
    const open = new RegExp('^<form\\b[^>]*>', 'i').exec(form)?.[0] || ''
    const action = new URL(attr(open, 'action') || baseUrl, baseUrl).href
    return { action, fields }
  }
  return null
}

function startDownloadUrl(html, baseUrl) {
  const source = String(html || '')
  const pattern = new RegExp('<a\\b([^>]*)>([\\s\\S]*?)<\\/a>', 'gi')
  let match
  while ((match = pattern.exec(source))) {
    const href = attr(match[1], 'href').trim()
    if (!href || /^javascript:/i.test(href)) continue
    const absolute = new URL(href, baseUrl).href
    const text = stripHtml(match[2]).toLowerCase()
    const pathname = new URL(absolute).pathname.toLowerCase()
    const downloadButton = /<button\b[^>]*class\s*=\s*["'][^"']*\bdownloadbtn\b/i.test(match[2]) ||
      /\bdownloadbtn\b/i.test(match[1])
    const legacyStartLink = /\bstart\s+download\b/i.test(text) && pathname.includes('/d/')
    const directDownloadLink = pathname.includes('/d/') && /download/i.test(text)
    // Downloadwella's current flow returns an anchor containing a button with class downloadbtn.
    if (downloadButton || legacyStartLink || directDownloadLink) return absolute
  }
  return ''
}

async function resolveDownloadwella(link, sourceReferer = '') {
  const page = String(link?.url || link || '').trim()
  if (!page) throw new Error('Downloadwella release URL is missing.')
  const jar = new Map()
  const getResponse = await downloadwellaRequest(page, { referer:sourceReferer, jar })
  const getHtml = await getResponse.text()
  const form = downloadwellaForm(getHtml, getResponse.url)
  if (!form) throw new Error('Downloadwella free-download form was not found.')
  const postResponse = await downloadwellaRequest(form.action, {
    method:'POST',
    body:new URLSearchParams(form.fields).toString(),
    referer:page,
    jar,
  })
  const postHtml = await postResponse.text()
  const directUrl = startDownloadUrl(postHtml, postResponse.url)
  if (!directUrl) throw new Error('Downloadwella direct download link was not found.')
  return {
    url:directUrl,
    headers:{
      Referer:page,
      Cookie:cookieHeader(jar),
      'User-Agent':UA,
    },
    fileName:String(link?.fileName || fileNameFromUrl(directUrl) || 'movie.mkv').replace(/\.html$/i, ''),
  }

}

function extractReleaseLinks(content) {
  const html = String(content || '')
  const links = []
  const seen = new Set()
  const pattern = /<a\b[^>]*\bhref\s*=\s*(["'])(.*?)\1[^>]*>([\s\S]*?)<\/a>/gi
  let match
  while ((match = pattern.exec(html))) {
    const url = decodeEntities(match[2]).replace(/\\\//g, '/').trim()
    const host = hostKind(url)
    if (!host || seen.has(url)) continue
    seen.add(url)
    const label = stripHtml(match[3])
    const fileName = fileNameFromUrl(url)
    const numbers = mediaNumbers(label + ' ' + fileName)
    links.push({
      url,
      host,
      label,
      fileName,
      season:numbers.season,
      episode:numbers.episode,
    })
  }
  return links
}

function normalizePost(post) {
  const rawTitle = stripHtml(post?.title?.rendered || '')
  const content = String(post?.content?.rendered || '')
  const type = inferType(rawTitle, content)
  const year = extractYear(rawTitle)
  const numbers = mediaNumbers(rawTitle)
  const title = type === 'tv' ? cleanSeriesTitle(rawTitle) : cleanTitle(rawTitle)
  const releaseLinks = extractReleaseLinks(content)

  return {
    id:'thenkiri:' + String(post?.id || ''),
    title,
    description:[year || '', type === 'tv' && numbers.season ? 'S' + String(numbers.season).padStart(2, '0') : '', type === 'tv' ? 'TV' : 'Movie'].filter(Boolean).join(' • '),
    year,
    type,
    seasonNumber:type === 'tv' ? numbers.season : 0,
    sourcePostId:Number(post?.id || 0) || 0,
    sourceLink:String(post?.link || ''),
    available:releaseLinks.length > 0,
    releaseLinks,
  }
}

async function getJson(url, retries = 2) {
  let lastError
  for (let attempt = 1; attempt <= retries; attempt += 1) {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 12000)
    try {
      const response = await fetch(url, {
        headers:{
          'user-agent':UA,
          accept:'application/json',
          'accept-language':'en-US,en;q=0.9',
          'accept-encoding':'identity',
        },
        signal:controller.signal,
      })
      if (!response.ok) throw new Error('TheNkiri catalog HTTP ' + response.status)
      return await response.json()
    } catch (error) {
      lastError = error
      if (attempt < retries) await new Promise(resolve => setTimeout(resolve, 400 * attempt))
    } finally {
      clearTimeout(timer)
    }
  }
  throw lastError
}

function siteHtmlCandidates(value) {
  const target = new URL(value, BASE)
  if (!SITE_HOSTS.has(target.hostname.toLowerCase())) {
    throw new Error('Refusing to fetch a non-TheNkiri page.')
  }
  const path = target.pathname + target.search + target.hash
  return [...new Set([target.href, ...SITE_BASES.map(origin => new URL(path, origin).href)])]
}

async function getHtmlPage(value) {
  let lastError
  for (const url of siteHtmlCandidates(value)) {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 10000)
    try {
      const response = await fetch(url, {
        headers:{
          'user-agent':UA,
          accept:'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
          'accept-language':'en-US,en;q=0.9',
          'accept-encoding':'identity',
        },
        redirect:'follow',
        signal:controller.signal,
      })
      if (!response.ok) throw new Error('TheNkiri HTML HTTP ' + response.status)
      const html = await response.text()
      if (!html || html.length < 100) throw new Error('TheNkiri returned an empty HTML page.')
      return { html, url:response.url || url }
    } catch (error) {
      lastError = error
    } finally {
      clearTimeout(timer)
    }
  }
  throw lastError || new Error('TheNkiri HTML page could not be fetched.')
}

function contentPermalink(value, baseUrl) {
  try {
    const url = new URL(value, baseUrl)
    if (!SITE_HOSTS.has(url.hostname.toLowerCase())) return ''
    const path = url.pathname.replace(/\/+$/, '')
    if (!path) return ''
    const parts = path.split('/').filter(Boolean)
    if (parts.length !== 1) return ''
    const slug = parts[0].toLowerCase()
    const reserved = new Set([
      'home', 'movies', 'movie', 'series', 'tv-series', 'genre', 'genres',
      'category', 'tag', 'search', 'page', 'how-to-download',
      'new-server-how-to-download', 'about', 'about-us', 'contact',
      'privacy-policy', 'terms', 'feed', 'wp-json',
    ])
    if (reserved.has(slug)) return ''
    return url.href
  } catch {
    return ''
  }
}

function postIdNear(html, index) {
  const articleStart = html.lastIndexOf('<article', index)
  const articleEnd = html.indexOf('</article>', index)
  if (articleStart >= 0 && articleEnd >= index && articleEnd - articleStart < 100000) {
    const article = html.slice(articleStart, articleEnd + 10)
    const match = /\bpost-(\d+)\b/i.exec(article) ||
      /\bid=["'][^"']*(\d+)[^"']*["']/i.exec(article)
    if (match) return Number(match[1]) || 0
  }
  return 0
}

function parseHtmlListing(html, baseUrl = BASE, limit = 20) {
  const source = String(html || '')
  const rows = []
  const seen = new Set()
  const headingPattern = /<h[23]\b[^>]*>([\s\S]*?)<\/h[23]>/gi
  let heading
  while ((heading = headingPattern.exec(source)) && rows.length < limit) {
    const anchor = /<a\b([^>]*)>([\s\S]*?)<\/a>/i.exec(heading[1])
    if (!anchor) continue
    const rawUrl = attr(anchor[1], 'href').trim()
    const link = contentPermalink(rawUrl, baseUrl)
    if (!link || seen.has(link)) continue
    const rawTitle = attr(anchor[1], 'title') || attr(anchor[2], 'alt') || stripHtml(anchor[2])
    const title = cleanTitle(rawTitle)
    if (!title || title.length < 3) continue
    const slug = new URL(link).pathname.split('/').filter(Boolean).at(-1) || ''
    if (!slug) continue
    seen.add(link)
    const postId = postIdNear(source, heading.index)
    const item = normalizePost({
      id:slug,
      link,
      title:{ rendered:rawTitle },
      content:{ rendered:'' },
    })
    // Keep the slug in id because media flows retain that field but drop provider-only fields.
    item.id = 'thenkiri:' + slug
    item.sourcePostId = postId
    item.sourceLink = link
    item.type = inferType(rawTitle, '')
    item.title = item.type === 'tv' ? cleanSeriesTitle(rawTitle) : cleanTitle(rawTitle)
    rows.push(item)
  }
  return rows
}

function titleFromHtml(html) {
  const h1 = /<h1\b[^>]*>([\s\S]*?)<\/h1>/i.exec(String(html || ''))
  const title = /<title\b[^>]*>([\s\S]*?)<\/title>/i.exec(String(html || ''))
  return stripHtml(h1 ? h1[1] : title ? title[1] : '')
}

function normalizeHtmlPost(html, pageUrl) {
  const rawTitle = titleFromHtml(html)
  if (!rawTitle) throw new Error('TheNkiri post title was not found.')
  const url = new URL(pageUrl, BASE)
  if (!SITE_HOSTS.has(url.hostname.toLowerCase())) throw new Error('Refusing a non-TheNkiri post URL.')
  const slug = url.pathname.split('/').filter(Boolean).at(-1) || ''
  if (!slug) throw new Error('TheNkiri post URL did not contain a slug.')
  const articleId = postIdNear(html, Math.max(0, String(html).indexOf('<h1')))
  const item = normalizePost({
    id:slug,
    link:url.href,
    title:{ rendered:rawTitle },
    content:{ rendered:'' },
  })
  item.id = 'thenkiri:' + slug
  item.sourcePostId = articleId
  item.sourceLink = url.href
  item.releaseLinks = extractReleaseLinks(html)
  item.available = item.releaseLinks.length > 0
  item.type = inferType(rawTitle, '')
  item.title = item.type === 'tv' ? cleanSeriesTitle(rawTitle) : cleanTitle(rawTitle)
  return item
}

async function posts(query = '', limit = 20, { content = false } = {}) {
  const requestedLimit = Math.min(Math.max(Number(limit) || 20, 1), 20)
  const url = new URL('/wp-json/wp/v2/posts', BASE)
  url.searchParams.set('per_page', String(requestedLimit))
  url.searchParams.set('_fields', content ? 'id,date,slug,link,title,content' : 'id,date,slug,link,title')
  if (String(query || '').trim()) url.searchParams.set('search', String(query).trim())

  let apiError = null
  try {
    const rows = await getJson(url, 1)
    const normalized = Array.isArray(rows)
      ? rows.map(normalizePost).filter(row => row.sourcePostId && row.title)
      : []
    if (normalized.length) return normalized.slice(0, requestedLimit)
  } catch (error) {
    apiError = error
  }

  let htmlLoaded = false
  let htmlError = null
  for (const origin of SITE_BASES) {
    const listingUrl = new URL('/', origin)
    listingUrl.searchParams.set('post_type', 'post')
    if (String(query || '').trim()) listingUrl.searchParams.set('s', String(query).trim())
    try {
      const page = await getHtmlPage(listingUrl.href)
      htmlLoaded = true
      const fallback = parseHtmlListing(page.html, page.url, requestedLimit)
      if (fallback.length) return fallback
    } catch (error) {
      htmlError = error
    }
  }

  if (apiError && !htmlLoaded) {
    throw new Error('TheNkiri catalog API and HTML fallback failed: ' +
      apiError.message + (htmlError ? '; ' + htmlError.message : ''))
  }
  return []
}

async function postById(id) {
  const url = new URL('/wp-json/wp/v2/posts/' + encodeURIComponent(String(id)), BASE)
  url.searchParams.set('_fields', 'id,date,slug,link,title,content')
  return normalizePost(await getJson(url, 1))
}

async function postByUrl(value) {
  const page = await getHtmlPage(value)
  return normalizeHtmlPost(page.html, page.url)
}

function sourceSlug(item) {
  const raw = String(item?.id || '').replace(/^thenkiri:/i, '').trim()
  return raw && !/^\d+$/.test(raw) && /^[a-z0-9_-]+$/i.test(raw) ? raw : ''
}

async function postForItem(item) {
  const slug = sourceSlug(item)
  const link = String(item?.sourceLink || '').trim()

  // HTML-derived results carry a slug through the movie/TV selection token.
  // Prefer that route so a missing REST API does not block detail/episode lookup.
  if (slug) {
    if (link) {
      try {
        return await postByUrl(link)
      } catch {}
    }
    try {
      return await postByUrl(new URL('/' + slug + '/', BASE).href)
    } catch {}
  }

  const id = sourcePostId(item)
  if (id) {
    try {
      return await postById(id)
    } catch {}
  }

  if (link) {
    try {
      return await postByUrl(link)
    } catch {}
  }
  return null
}

function matchingLinks(links, { type = '', season = 0, episode = 0 } = {}) {
  const rows = Array.isArray(links) ? links : []
  if (String(type || '').toLowerCase() !== 'tv') return rows
  const s = Number(season) || 0
  const e = Number(episode) || 0
  if (e > 0) {
    return rows.filter(link => link.episode === e && (!s || !link.season || link.season === s))
  }
  if (s > 0) return rows.filter(link => !link.season || link.season === s)
  return rows
}

export async function searchTheNkiri(query, type) {
  const rows = await posts(query, 20)
  return rows.filter(row => !type || row.type === type)
}

export async function browseTheNkiri(type) {
  const rows = await posts('', 20)
  return rows.filter(row => !type || row.type === type)
}

function sourcePostId(item) {
  return Number(item?.sourcePostId || String(item?.id || '').replace(/^thenkiri:/i, '')) || 0
}

function seasonsFromRelease(item) {
  const seasons = new Map()
  const fallbackSeason = Number(item?.seasonNumber || 0)
  for (const link of Array.isArray(item?.releaseLinks) ? item.releaseLinks : []) {
    const season = Number(link?.season || fallbackSeason)
    if (!Number.isInteger(season) || season <= 0) continue
    if (!seasons.has(season)) seasons.set(season, new Set())
    const episode = Number(link?.episode || 0)
    if (Number.isInteger(episode) && episode > 0) seasons.get(season).add(episode)
  }
  if (!seasons.size && fallbackSeason > 0) seasons.set(fallbackSeason, new Set())
  return [...seasons.entries()]
    .sort(([a],[b]) => a - b)
    .map(([number, episodes]) => ({
      id:String(number),
      number,
      title:'Season ' + number,
      episodeCount:episodes.size,
    }))
}

function episodesFromRelease(item, seasonNumber) {
  const season = Number(seasonNumber)
  if (!Number.isInteger(season) || season <= 0) return []
  const fallbackSeason = Number(item?.seasonNumber || 0)
  const numbers = new Set()
  for (const link of Array.isArray(item?.releaseLinks) ? item.releaseLinks : []) {
    const linkSeason = Number(link?.season || fallbackSeason)
    // Never assign an episode with unknown season identity to every season.
    if (linkSeason !== season) continue
    const episode = Number(link?.episode || 0)
    if (Number.isInteger(episode) && episode > 0) numbers.add(episode)
  }
  return [...numbers].sort((a,b) => a - b).map(number => ({
    id:String(number),
    number:String(number),
    seasonNumber:season,
    title:'Episode ' + number,
  }))
}

async function fullTvPost(item) {
  const post = await postForItem(item)
  return post?.type === 'tv' ? post : null
}

export async function listTheNkiriSeasons({ item } = {}) {
  const post = await fullTvPost(item)
  return post ? seasonsFromRelease(post) : []
}

export async function listTheNkiriEpisodes({ item, seasonNumber } = {}) {
  const post = await fullTvPost(item)
  return post ? episodesFromRelease(post, seasonNumber) : []
}

export async function resolveTheNkiriRelease({ item, title, type = '', season = 0, episode = 0 } = {}) {
  const expectedType = String(type || item?.type || '').toLowerCase()
  const candidates = []
  const direct = await postForItem(item)
  if (direct) candidates.push(direct)

  // HTML search fallback is also needed when REST API results stop being available.
  if (!direct || !direct.releaseLinks.length) {
    const parts = [String(title || item?.title || '').trim()]
    if (expectedType === 'tv' && Number(season) > 0) {
      parts.push('S' + String(Number(season)).padStart(2, '0'))
    }
    const rows = (await posts(parts.filter(Boolean).join(' '), 12))
      .filter(row => !expectedType || row.type === expectedType)
    for (const row of rows.slice(0, 6)) {
      const full = await postForItem(row)
      if (full && !candidates.some(existing => existing.sourceLink === full.sourceLink)) {
        candidates.push(full)
      }
    }
  }

  for (const full of candidates) {
    if (expectedType && full.type !== expectedType) continue
    const links = matchingLinks(full.releaseLinks, { type:expectedType, season, episode })
    if (!links.length) continue
    return {
      found:true,
      item:full,
      links,
      link:links[0],
    }
  }
  return { found:false, item:candidates[0] || null, links:[], link:null }
}

export async function resolveTheNkiriDownload({ item, title, type = '', season = 0, episode = 0 } = {}) {
  const release = await resolveTheNkiriRelease({ item, title, type, season, episode })
  if (!release.found) throw new Error('TheNkiri has no matching release right now.')
  const links = release.links.filter(link => link.host === 'downloadwella')
  if (!links.length) throw new Error('TheNkiri release is available, but no proven Downloadwella file is present.')
  let lastError
  for (const link of links) {
    try {
      const candidate = await resolveDownloadwella(link, release.item?.sourceLink || '')
      return { release, link, candidate }
    } catch (error) {
      lastError = error
    }
  }
  throw lastError || new Error('TheNkiri Downloadwella release could not be resolved.')
}

export async function materializeTheNkiriRelease(input = {}) {
  const resolved = await resolveTheNkiriDownload(input)
  const media = await materializeCandidates([resolved.candidate], 'source', 'document')
  return {
    ...media,
    fileName:resolved.candidate.fileName || 'movie.mkv',
    release:resolved.release,
    link:resolved.link,
  }
}

export async function inspectTheNkiriAvailability({ item, title, type = '', season = 0, episode = 0 } = {}) {
  const release = await resolveTheNkiriRelease({ item, title, type, season, episode })
  if (!release.found) return { found:false, matches:[] }
  return {
    found:true,
    matches:[{
      id:release.item.id,
      title:release.item.title,
      year:release.item.year,
      type:release.item.type,
      sourcePostId:release.item.sourcePostId,
      sourceLink:release.item.sourceLink,
      links:release.links,
    }],
  }
}

export const _test = {
  stripHtml,
  extractYear,
  parseHtmlListing,
  titleFromHtml,
  normalizeHtmlPost,
  inferType,
  cleanTitle,
  cleanSeriesTitle,
  normalizePost,
  hostKind,
  mediaNumbers,
  extractReleaseLinks,
  matchingLinks,
  seasonsFromRelease,
  episodesFromRelease,
  sourcePostId,
  downloadwellaForm,
  startDownloadUrl,
  resolveDownloadwella,
}
