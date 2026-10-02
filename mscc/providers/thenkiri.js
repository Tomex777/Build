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

export async function searchTheNkiri(query, type) {
  const rows = await posts(query, 20)
  return rows.filter(row => !type || row.type === type)
}

export async function browseTheNkiri(type) {
  const rows = await posts('', 20)
  return rows.filter(row => !type || row.type === type)
}

export async function inspectTheNkiriAvailability({ title, type = '', season = 0 } = {}) {
  const parts = [String(title || '').trim()]
  if (type === 'tv' && Number(season) > 0) parts.push('S' + String(Number(season)).padStart(2, '0'))
  const rows = (await posts(parts.filter(Boolean).join(' '), 12))
    .filter(row => !type || row.type === type)

  const matches = []
  for (const row of rows.slice(0, 6)) {
    const full = await postById(row.sourcePostId)
    if (!full.available) continue
    matches.push({
      id:full.id,
      title:full.title,
      year:full.year,
      type:full.type,
      sourcePostId:full.sourcePostId,
    })
  }

  return { found:matches.length > 0, matches }
}

export const _test = { stripHtml, extractYear, inferType, cleanTitle, normalizePost }
