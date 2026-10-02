const BASE = 'https://thenkiri.com'
const UA = 'Mozilla/5.0 (Linux; Android 16; SM-A165F) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36'

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
    .replace(/\s*\|\s*Download\b.*$/i, '')
    .replace(/^Download\s+/i, '')
    .replace(/\s*\((?:19|20)\d{2}\)\s*$/, match => match)
    .trim()
}

function normalizePost(post) {
  const rawTitle = stripHtml(post?.title?.rendered || '')
  const content = String(post?.content?.rendered || '')
  const type = inferType(rawTitle, content)
  const year = extractYear(rawTitle)
  const title = cleanTitle(rawTitle)
  const hasDownloadHost = /https?:\/\/(?:www\.)?downloadwella\.com\//i.test(content)

  return {
    id:'thenkiri:' + String(post?.id || ''),
    title,
    description:[year || '', type === 'tv' ? 'TV' : 'Movie'].filter(Boolean).join(' • '),
    year,
    type,
    sourcePostId:Number(post?.id || 0) || 0,
    sourceLink:String(post?.link || ''),
    available:hasDownloadHost,
  }
}

async function getJson(url) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 25000)
  try {
    const response = await fetch(url, {
      headers:{
        'user-agent':UA,
        accept:'application/json',
        'accept-language':'en-US,en;q=0.9',
      },
      signal:controller.signal,
    })
    if (!response.ok) throw new Error('TheNkiri catalog HTTP ' + response.status)
    return response.json()
  } finally {
    clearTimeout(timer)
  }
}

async function posts(query = '', limit = 30) {
  const url = new URL('/wp-json/wp/v2/posts', BASE)
  url.searchParams.set('per_page', String(Math.min(Math.max(Number(limit) || 30, 1), 100)))
  url.searchParams.set('_fields', 'id,date,slug,link,title,content')
  if (String(query || '').trim()) url.searchParams.set('search', String(query).trim())

  const rows = await getJson(url)
  return Array.isArray(rows) ? rows.map(normalizePost).filter(row => row.sourcePostId && row.title) : []
}

export async function searchTheNkiri(query, type) {
  const rows = await posts(query, 50)
  return rows.filter(row => !type || row.type === type)
}

export async function browseTheNkiri(type) {
  const rows = await posts('', 50)
  return rows.filter(row => !type || row.type === type)
}

export async function inspectTheNkiriAvailability({ title, type = '', season = 0, episode = 0 } = {}) {
  const parts = [String(title || '').trim()]
  if (type === 'tv' && Number(season) > 0) parts.push('S' + String(Number(season)).padStart(2, '0'))
  if (type === 'tv' && Number(episode) > 0) parts.push('E' + String(Number(episode)).padStart(2, '0'))
  const rows = await posts(parts.filter(Boolean).join(' '), 50)
  const candidates = rows.filter(row => (!type || row.type === type) && row.available)
  return {
    found:candidates.length > 0,
    matches:candidates.map(row => ({
      id:row.id,
      title:row.title,
      year:row.year,
      type:row.type,
      sourcePostId:row.sourcePostId,
    })),
  }
}

export const _test = { stripHtml, extractYear, inferType, cleanTitle, normalizePost }
