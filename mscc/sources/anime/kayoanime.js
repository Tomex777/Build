import { createWriteStream } from 'node:fs'
import { rm } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { Readable } from 'node:stream'
import { pipeline } from 'node:stream/promises'

const BASE_URL = 'https://kayoanime.com'
const UA = 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36'
const PLAYABLE = new Set(['mkv','mp4','webm','m4v'])
const MAX_FOLDER_DEPTH = 4
const FLARE_URL = String(
  process.env.MSCC_FLARESOLVERR_URL ||
  ('http://127.0.0.1:' + (process.env.MSCC_FLARE_PORT || '8191'))
).replace(/\\\/$/, '')
const FLARE_TIMEOUT = Number(process.env.MSCC_KAYO_FLARE_TIMEOUT_MS || 60000)

function absolute(href, base = BASE_URL + '/') {
  try { return new URL(String(href || ''), base).href } catch { return '' }
}

function decodeHtml(value = '') {
  return String(value)
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n) || 32))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCodePoint(Number.parseInt(n, 16) || 32))
    .replace(/\s+/g, ' ')
    .trim()
}

function attr(tag, name) {
  const re = new RegExp('\\b' + name + '\\s*=\\s*["\\\']([^"\\\']*)["\\\']', 'i')
  return re.exec(String(tag || ''))?.[1] || ''
}

function anchorPairs(html) {
  const out = []
  const re = /<a\b([^>]*)>([\s\S]*?)<\/a>/gi
  for (const match of String(html || '').matchAll(re)) {
    const href = attr(match[1], 'href')
    if (!href) continue
    out.push({ href, text:decodeHtml(match[2]) })
  }
  return out
}

function headingAnchors(html) {
  const out = []
  const re = /<h[1-3]\b[^>]*>([\s\S]*?)<\/h[1-3]>/gi
  for (const heading of String(html || '').matchAll(re)) {
    const anchors = anchorPairs(heading[1])
    if (anchors[0]) out.push(anchors[0])
  }
  return out
}

async function flareHtml(url) {
  const response = await fetch(FLARE_URL + '/v1', {
    method:'POST',
    headers:{ 'content-type':'application/json' },
    body:JSON.stringify({ cmd:'request.get', url, maxTimeout:FLARE_TIMEOUT }),
    signal:AbortSignal.timeout(FLARE_TIMEOUT + 10000),
  })
  const text = await response.text()
  if (!response.ok) throw new Error('FlareSolverr HTTP ' + response.status)
  let data
  try { data = JSON.parse(text) } catch { throw new Error('Invalid FlareSolverr response') }
  const solution = data?.solution || {}
  if (data?.status !== 'ok' || Number(solution.status || 0) >= 400) {
    const error = new Error('KayoAnime browser verification failed.')
    error.code = 'verification-required'
    throw error
  }
  return {
    html:String(solution.response || ''),
    finalUrl:String(solution.url || url),
  }
}

async function loadHtml(url, { referer = BASE_URL + '/', userAgent = UA } = {}) {
  try {
    const response = await fetch(url, {
      headers:{
        'user-agent':userAgent,
        'referer':referer,
        'accept':'text/html,application/xhtml+xml',
      },
      redirect:'follow',
      signal:AbortSignal.timeout(12000),
    })
    const html = await response.text()
    const challenge = /captcha|verify|challenge|cloudflare|just a moment/i.test(html)
    if (response.ok && !challenge) return { html, finalUrl:response.url || url }
    if (!challenge && response.status !== 403 && response.status !== 429) {
      const error = new Error('KayoAnime HTTP ' + response.status + '.')
      error.code = 'source-http-error'
      throw error
    }
  } catch (error) {
    if (error?.code === 'source-http-error') throw error
  }

  return flareHtml(url)
}

function isKayoUrl(value) {
  try { return new URL(value).hostname === new URL(BASE_URL).hostname } catch { return false }
}

function parseListing(html, query = '') {
  const candidates = headingAnchors(html)
  const fallback = candidates.length ? candidates : anchorPairs(html)
  const tokens = String(query || '').toLowerCase().split(/\s+/).filter(token => token.length >= 3)
  const seen = new Set()
  const items = []
  for (const link of fallback) {
    const href = absolute(link.href)
    const title = link.text
    if (!href || !isKayoUrl(href) || title.length < 3) continue
    if (/\/(category|tag|author)\//i.test(href) || href === BASE_URL + '/') continue
    if (tokens.length && !tokens.some(token => title.toLowerCase().includes(token)) && !candidates.length) continue
    if (seen.has(href)) continue
    seen.add(href)
    items.push({ id:href, title })
  }
  return items
}

function extractDriveFolderId(url) {
  return /\/drive\/(?:u\/\d+\/)?folders\/([A-Za-z0-9_-]{10,})/.exec(String(url || ''))?.[1] || ''
}

function extractDriveFileId(url) {
  return /\/file\/d\/([A-Za-z0-9_-]{10,})/.exec(String(url || ''))?.[1] || ''
}

function extensionOf(name) {
  return String(name || '').split('.').pop()?.toLowerCase() || ''
}

function episodeNumber(name) {
  const explicit = /(?:episode|ep)[ ._-]*(\d{1,3}(?:\.\d+)?)/i.exec(String(name || ''))
  if (explicit) return Number(explicit[1])
  const values = [...String(name || '').matchAll(/(?<!\d)(\d{1,3})(?!\d)/g)]
    .map(match => Number(match[1]))
    .filter(Number.isFinite)
  return values.at(-1) ?? null
}

function encodeEpisode(file) {
  return 'gdrive:' + file.id + ':' + encodeURIComponent(file.name)
}

function decodeEpisode(value) {
  const raw = String(value || '')
  if (!raw.startsWith('gdrive:')) return null
  const payload = raw.slice('gdrive:'.length)
  const cut = payload.indexOf(':')
  if (cut < 1) return null
  const id = payload.slice(0, cut)
  const name = decodeURIComponent(payload.slice(cut + 1))
  if (!id || !name) return null
  return { id, name, extension:extensionOf(name) }
}

async function listDriveFolder(folderId, { prefix = '', seen = new Set(), depth = 0 } = {}) {
  if (!folderId || depth > MAX_FOLDER_DEPTH || seen.has(folderId)) return []
  seen.add(folderId)
  const url = 'https://drive.google.com/embeddedfolderview?id=' + encodeURIComponent(folderId)
  const loaded = await loadHtml(url, { referer:'https://drive.google.com/', userAgent:UA })
  const out = []
  for (const link of anchorPairs(loaded.html)) {
    const href = absolute(link.href, loaded.finalUrl)
    const label = link.text
    const fileId = extractDriveFileId(href)
    if (fileId) {
      const name = [prefix, label].filter(Boolean).join(' • ') || fileId
      out.push({ id:fileId, name, extension:extensionOf(name) })
      continue
    }
    const child = extractDriveFolderId(href)
    if (!child) continue
    const childPrefix = [prefix, label].filter(Boolean).join(' • ')
    out.push(...await listDriveFolder(child, { prefix:childPrefix, seen, depth:depth + 1 }))
  }
  return out
}

async function listEpisodes(item) {
  const url = String(item?.id || item?.url || '')
  if (!isKayoUrl(url)) throw new Error('Invalid KayoAnime item URL.')
  const loaded = await loadHtml(url, { referer:BASE_URL + '/' })
  const h1 = /<h1\b[^>]*>([\s\S]*?)<\/h1>/i.exec(loaded.html)?.[1]
  const titleTag = /<title\b[^>]*>([\s\S]*?)<\/title>/i.exec(loaded.html)?.[1]
  const pageTitle = decodeHtml(h1 || titleTag || item?.title || 'KayoAnime')
    .replace(/\s*-\s*Kayoanime.*$/i, '')
    .trim()

  const discovered = []
  const seenFolders = new Set()
  for (const link of anchorPairs(loaded.html)) {
    const href = absolute(link.href, loaded.finalUrl)
    if (!/drive\.google\.com/i.test(href)) continue
    const fileId = extractDriveFileId(href)
    if (fileId) {
      const name = link.text || 'KayoAnime file'
      discovered.push({ id:fileId, name, extension:extensionOf(name) })
      continue
    }
    const folderId = extractDriveFolderId(href)
    if (folderId) {
      discovered.push(...await listDriveFolder(folderId, {
        prefix:link.text || '',
        seen:seenFolders,
        depth:0,
      }))
    }
  }

  const unique = new Map()
  for (const file of discovered) {
    if (!PLAYABLE.has(file.extension) || unique.has(file.id)) continue
    unique.set(file.id, file)
  }
  const files = [...unique.values()].sort((a, b) => {
    const an = episodeNumber(a.name)
    const bn = episodeNumber(b.name)
    if (an === null && bn === null) return a.name.localeCompare(b.name)
    if (an === null) return 1
    if (bn === null) return -1
    return an - bn || a.name.localeCompare(b.name)
  })

  return {
    title:pageTitle,
    episodes:files.map((file, index) => ({
      id:encodeEpisode(file),
      number:String(episodeNumber(file.name) ?? index + 1),
      title:file.name,
    })),
  }
}

function mimeFor(file) {
  if (file.extension === 'mkv') return 'video/x-matroska'
  if (file.extension === 'webm') return 'video/webm'
  if (file.extension === 'm4v' || file.extension === 'mp4') return 'video/mp4'
  return 'application/octet-stream'
}

function mediaDescriptor(value) {
  const file = typeof value === 'string' ? decodeEpisode(value) : value
  if (!file) return null
  return {
    url:'https://drive.google.com/uc?export=download&id=' + encodeURIComponent(file.id),
    fileName:file.name,
    extension:file.extension,
    mimetype:mimeFor(file),
    headers:{ 'User-Agent':UA },
  }
}

function cookieHeader(response) {
  const values = typeof response?.headers?.getSetCookie === 'function'
    ? response.headers.getSetCookie()
    : []
  return values.map(value => String(value).split(';')[0]).filter(Boolean).join('; ')
}

function parseDriveConfirmation(html, baseUrl) {
  const source = String(html || '')
  const form = new RegExp('<form([^>]*)>(.*?)</form>', 'is').exec(source)
  if (form) {
    const action = attr(form[1], 'action')
    if (action) {
      const url = new URL(action, baseUrl)
      for (const input of form[2].matchAll(new RegExp('<input([^>]*)>', 'gi'))) {
        const name = attr(input[1], 'name')
        const value = attr(input[1], 'value')
        if (name) url.searchParams.set(name, value)
      }
      return url.href
    }
  }

  const link = /href=["']([^"']*(?:confirm=|download)[^"']*)["']/i.exec(source)?.[1]
  return link ? absolute(decodeHtml(link), baseUrl) : ''
}

async function resolveDriveDownload(file) {
  const initial = 'https://drive.google.com/uc?export=download&id=' + encodeURIComponent(file.id)
  const response = await fetch(initial, {
    headers:{ 'user-agent':UA, referer:'https://drive.google.com/' },
    redirect:'follow',
    signal:AbortSignal.timeout(25000),
  })
  const contentType = String(response.headers.get('content-type') || '').toLowerCase()
  const cookies = cookieHeader(response)

  if (response.ok && !contentType.includes('text/html')) {
    await response.body?.cancel().catch(() => {})
    return {
      url:response.url || initial,
      headers:{
        'User-Agent':UA,
        Referer:'https://drive.google.com/',
        ...(cookies ? { Cookie:cookies } : {}),
      },
    }
  }

  const html = await response.text()
  const confirmed = parseDriveConfirmation(html, response.url || initial)
  if (!confirmed) {
    const error = new Error('Google Drive did not expose a downloadable file handoff.')
    error.code = 'drive-confirmation-failed'
    throw error
  }

  return {
    url:confirmed,
    headers:{
      'User-Agent':UA,
      Referer:'https://drive.google.com/',
      ...(cookies ? { Cookie:cookies } : {}),
    },
  }
}

async function downloadDriveFile(file) {
  const resolved = await resolveDriveDownload(file)
  const timeoutMs = Number(process.env.MSCC_KAYO_DOWNLOAD_TIMEOUT_MS || 30 * 60_000)
  const response = await fetch(resolved.url, {
    headers:resolved.headers,
    redirect:'follow',
    signal:AbortSignal.timeout(timeoutMs),
  })
  const contentType = String(response.headers.get('content-type') || '').toLowerCase()
  if (!response.ok || contentType.includes('text/html') || !response.body) {
    throw new Error('Google Drive did not return KayoAnime media bytes.')
  }

  const extension = PLAYABLE.has(file.extension) ? file.extension : 'bin'
  const path = join(tmpdir(), 'mscc-kayo-' + randomUUID() + '.' + extension)
  try {
    await pipeline(Readable.fromWeb(response.body), createWriteStream(path))
    return { path, resolved }
  } catch (error) {
    await rm(path, { force:true }).catch(() => {})
    throw error
  }
}

async function sendFile(context, file, delivery) {
  const media = mediaDescriptor(file)
  const downloaded = await downloadDriveFile(file)
  const mimetype = media.mimetype
  const inline = delivery === 'video' && ['mp4','m4v','webm'].includes(file.extension)
  try {
    if (inline) {
      await context.send({ video:{ url:downloaded.path }, mimetype, caption:file.name })
    } else {
      await context.send({ document:{ url:downloaded.path }, mimetype, fileName:file.name })
    }
  } finally {
    await rm(downloaded.path, { force:true }).catch(() => {})
  }
}

export default {
  id:'kayoanime',
  name:'KayoAnime',
  description:'KayoAnime pages with Google Drive-hosted episode files.',
  fallbackOrder:10,
  brandAliases:['Kayo Anime'],

  async run({ action, query, item, episode, episodeId, range, delivery = 'document', context }) {
    if (action === 'search' || action === 'browse') {
      const target = action === 'search' && query
        ? BASE_URL + '/?s=' + encodeURIComponent(String(query).trim())
        : BASE_URL + '/'
      const loaded = await loadHtml(target, { referer:BASE_URL + '/' })
      return { items:parseListing(loaded.html, action === 'search' ? query : '') }
    }
    if (action === 'episodes') return listEpisodes(item)
    if (action === 'options') return { qualities:['source'], deliveries:['document','video'] }

    if (action === 'download') {
      const file = decodeEpisode(episodeId || episode?.id)
      if (!file) throw new Error('KayoAnime episode reference is invalid.')
      await sendFile(context, file, delivery)
      return { delivered:true }
    }

    if (action === 'downloadRange') {
      const listing = await listEpisodes(item)
      const start = Number(range?.start?.number)
      const end = Number(range?.end?.number)
      if (!Number.isFinite(start) || !Number.isFinite(end)) throw new Error('Invalid KayoAnime range.')
      const low = Math.min(start, end)
      const high = Math.max(start, end)
      const selected = listing.episodes.filter(entry => {
        const n = Number(entry.number)
        return Number.isFinite(n) && n >= low && n <= high
      })
      if (!selected.length) throw new Error('No KayoAnime episodes matched that range.')
      for (const entry of selected) {
        const file = decodeEpisode(entry.id)
        if (file) await sendFile(context, file, delivery)
      }
      return { delivered:true, count:selected.length }
    }

    throw new Error('Unsupported KayoAnime action: ' + action)
  },

  _test:{ parseListing, extractDriveFolderId, extractDriveFileId, episodeNumber, encodeEpisode, decodeEpisode, parseDriveConfirmation },
  _probe:{ mediaDescriptor, resolveDriveDownload, downloadDriveFile },
}
