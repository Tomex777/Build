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
    .replace(/\s*\|\s*Download\b.*$/i, '')
    .replace(/^Download\s+/i, '')
    .replace(/\s*\((?:19|20)\d{2}\)\s*$/, match => match)
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
  const title = cleanTitle(rawTitle)
  const releaseLinks = extractReleaseLinks(content)

  return {
    id:'thenkiri:' + String(post?.id || ''),
    title,
    description:[year || '', type === 'tv' ? 'TV' : 'Movie'].filter(Boolean).join(' • '),
    year,
    type,
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


function tagAttr(tag, name) {
  const pattern = new RegExp('\\b' + name + '\\s*=\\s*(["\\'])(.*?)\\1', 'i')
  const match = pattern.exec(String(tag || ''))
  return match ? decodeEntities(match[2]).trim() : ''
}

function formFields(formHtml) {
  const fields = new Map()
  const tags = String(formHtml || '').match(/<input\b[^>]*>/gi) || []
  for (const tag of tags) {
    if (/\bdisabled(?:\s|=|>|$)/i.test(tag)) continue
    const name = tagAttr(tag, 'name')
    if (!name) continue
    const type = tagAttr(tag, 'type').toLowerCase()
    const value = tagAttr(tag, 'value')
    if ((type === 'checkbox' || type === 'radio') && !/\bchecked(?:\s|=|>|$)/i.test(tag)) continue
    if (type === 'submit' || type === 'button') {
      if (value && /download|create|free/i.test(value)) fields.set(name, value)
      continue
    }
    fields.set(name, value)
  }
  return fields
}

function downloadForm(html) {
  const forms = String(html || '').match(/<form\b[\s\S]*?<\/form>/gi) || []
  const scored = forms.map(form => {
    const fields = formFields(form)
    let score = 0
    if (String(fields.get('op') || '').toLowerCase() === 'download2') score += 10
    if (fields.has('method_free')) score += 4
    if (/create\s+download\s+link|free\s+download/i.test(form)) score += 3
    if (/\bname=["']F1["']/i.test(form)) score += 1
    return { form, fields, score }
  }).sort((a,b) => b.score - a.score)
  const best = scored[0]
  if (!best || best.score <= 0) return null
  const openTag = best.form.match(/<form\b[^>]*>/i)?.[0] || ''
  return {
    action:tagAttr(openTag, 'action'),
    method:(tagAttr(openTag, 'method') || 'post').toLowerCase(),
    fields:best.fields,
  }
}

function directLinkFromHtml(html, baseUrl) {
  const text = String(html || '')
  const anchors = text.match(/<a\b[^>]*>/gi) || []
  const scored = []
  for (const tag of anchors) {
    const href = tagAttr(tag, 'href')
    if (!href) continue
    let url
    try { url = new URL(href, baseUrl).href } catch { continue }
    const id = tagAttr(tag, 'id').toLowerCase()
    const cls = tagAttr(tag, 'class').toLowerCase()
    const path = (() => { try { return new URL(url).pathname.toLowerCase() } catch { return '' } })()
    let score = 0
    if (id === 'uniqueexpirylink' || id === 'd_l' || id === 'dlink') score += 20
    if (/btn[-_ ]?(?:dow|download)|download-btn/.test(cls)) score += 12
    if (/\.(?:mkv|mp4|avi|mov|webm|m4v)(?:$|[?#])/i.test(url)) score += 10
    if (/\/(?:download|dl|file)\//i.test(path)) score += 3
    if (/\.html(?:$|[?#])/i.test(url)) score -= 10
    if (score > 0) scored.push({ url, score })
  }

  const scriptPatterns = [
    /(?:download_url|downloadUrl|file|src)\s*[:=]\s*["'](https?:\/\/[^"']+)["']/ig,
    /(?:window\.)?location(?:\.href)?\s*=\s*["'](https?:\/\/[^"']+)["']/ig,
  ]
  for (const pattern of scriptPatterns) {
    let match
    while ((match = pattern.exec(text))) {
      const url = decodeEntities(match[1]).replace(/\\\//g, '/')
      if (/\.(?:mkv|mp4|avi|mov|webm|m4v)(?:$|[?#])/i.test(url)) scored.push({ url, score:15 })
    }
  }

  scored.sort((a,b) => b.score - a.score)
  return scored[0]?.url || ''
}

function mergeCookies(current, response) {
  const jar = new Map()
  for (const part of String(current || '').split(/;\s*/).filter(Boolean)) {
    const eq = part.indexOf('=')
    if (eq > 0) jar.set(part.slice(0,eq), part.slice(eq + 1))
  }
  const setCookies = typeof response?.headers?.getSetCookie === 'function'
    ? response.headers.getSetCookie()
    : [response?.headers?.get?.('set-cookie')].filter(Boolean)
  for (const row of setCookies) {
    const pair = String(row || '').split(';', 1)[0]
    const eq = pair.indexOf('=')
    if (eq > 0) jar.set(pair.slice(0,eq), pair.slice(eq + 1))
  }
  return [...jar].map(([key,value]) => key + '=' + value).join('; ')
}

async function hostFetch(url, { method = 'GET', body = null, cookie = '', referer = '' } = {}) {
  const headers = {
    'user-agent':UA,
    accept:'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'accept-language':'en-US,en;q=0.9',
    'accept-encoding':'identity',
  }
  if (cookie) headers.cookie = cookie
  if (referer) headers.referer = referer
  if (method === 'POST') headers['content-type'] = 'application/x-www-form-urlencoded'
  return fetch(url, {
    method,
    headers,
    body:method === 'POST' ? body : undefined,
    redirect:'manual',
    signal:AbortSignal.timeout(30_000),
  })
}

function absoluteLocation(response, baseUrl) {
  const location = response.headers.get('location')
  if (!location) return ''
  try { return new URL(location, baseUrl).href } catch { return '' }
}

export async function resolveTheNkiriFile(link) {
  const pageUrl = String(link?.url || link || '').trim()
  const host = hostKind(pageUrl)
  if (!host) throw new Error('Unsupported TheNkiri file host.')

  let currentUrl = pageUrl
  let referer = pageUrl
  let cookie = ''
  let response = await hostFetch(currentUrl, { referer:'https://thenkiri.com/' })

  for (let step = 0; step < 4; step += 1) {
    cookie = mergeCookies(cookie, response)
    const location = absoluteLocation(response, currentUrl)
    if (location) {
      if (!hostKind(location) || /\.(?:mkv|mp4|avi|mov|webm|m4v)(?:$|[?#])/i.test(location)) {
        return { pageUrl, host, url:location, headers:{ Referer:referer, Cookie:cookie } }
      }
      referer = currentUrl
      currentUrl = location
      response = await hostFetch(currentUrl, { cookie, referer })
      continue
    }

    const contentType = String(response.headers.get('content-type') || '').toLowerCase()
    if (!response.ok) throw new Error('TheNkiri file host HTTP ' + response.status)
    if (contentType && !contentType.includes('text/html') && !contentType.includes('application/xhtml')) {
      await response.body?.cancel().catch(() => {})
      return { pageUrl, host, url:currentUrl, headers:{ Referer:referer, Cookie:cookie } }
    }

    const html = await response.text()
    const direct = directLinkFromHtml(html, currentUrl)
    if (direct && direct !== currentUrl) {
      return { pageUrl, host, url:direct, headers:{ Referer:currentUrl, Cookie:cookie } }
    }

    const form = downloadForm(html)
    if (!form) {
      const captcha = /captcha|turnstile|recaptcha|hcaptcha/i.test(html)
      throw new Error('TheNkiri file host did not expose a download form' + (captcha ? ' (challenge present)' : '') + '.')
    }
    const params = new URLSearchParams()
    for (const [name, value] of form.fields) params.set(name, value)
    if (!params.has('referer')) params.set('referer', '')
    if (!params.has('method_free') && /download/i.test(html)) params.set('method_free', 'Free Download')
    const action = new URL(form.action || currentUrl, currentUrl).href
    referer = currentUrl
    currentUrl = action
    response = await hostFetch(action, {
      method:form.method === 'get' ? 'GET' : 'POST',
      body:params.toString(),
      cookie,
      referer,
    })
  }

  throw new Error('TheNkiri file host exceeded the supported download steps.')
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
  normalizePost,
  hostKind,
  mediaNumbers,
  extractReleaseLinks,
  matchingLinks,
  tagAttr,
  formFields,
  downloadForm,
  directLinkFromHtml,
}
