const BASE = 'https://thenkiri.com'
const UA = 'Mozilla/5.0 (Linux; Android 16; SM-A165F) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36'
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

async function posts(query = '', limit = 20, { content = false } = {}) {
  const url = new URL('/wp-json/wp/v2/posts', BASE)
  url.searchParams.set('per_page', String(Math.min(Math.max(Number(limit) || 20, 1), 20)))
  url.searchParams.set('_fields', content ? 'id,date,slug,link,title,content' : 'id,date,slug,link,title')
  if (String(query || '').trim()) url.searchParams.set('search', String(query).trim())

  const rows = await getJson(url)
  return Array.isArray(rows) ? rows.map(normalizePost).filter(row => row.sourcePostId && row.title) : []
}

async function postById(id) {
  const url = new URL('/wp-json/wp/v2/posts/' + encodeURIComponent(String(id)), BASE)
  url.searchParams.set('_fields', 'id,date,slug,link,title,content')
  return normalizePost(await getJson(url))
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

export async function resolveTheNkiriRelease({ item, title, type = '', season = 0, episode = 0 } = {}) {
  const expectedType = String(type || item?.type || '').toLowerCase()
  const directId = Number(item?.sourcePostId || String(item?.id || '').replace(/^thenkiri:/i, '')) || 0
  const candidates = []
  if (directId) {
    candidates.push(await postById(directId))
  } else {
    const parts = [String(title || item?.title || '').trim()]
    if (expectedType === 'tv' && Number(season) > 0) parts.push('S' + String(Number(season)).padStart(2, '0'))
    const rows = (await posts(parts.filter(Boolean).join(' '), 12))
      .filter(row => !expectedType || row.type === expectedType)
    for (const row of rows.slice(0, 6)) candidates.push(await postById(row.sourcePostId))
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
  inferType,
  cleanTitle,
  cleanSeriesTitle,
  normalizePost,
  hostKind,
  mediaNumbers,
  extractReleaseLinks,
  matchingLinks,
}
