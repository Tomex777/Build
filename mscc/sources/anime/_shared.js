import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { runFfmpeg } from '../../utils/media-conversion.js'
import { resolveM3u8 } from '../../utils/media/hls.js'

export const ANIME_UA = 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36'

const FLARE_URL = String(
  process.env.MSCC_FLARESOLVERR_URL ||
  ('http://127.0.0.1:' + (process.env.MSCC_FLARE_PORT || '8191'))
).replace(/\/$/, '')

export function clean(value, max = 500) {
  return String(value ?? '').replace(/\s+/g, ' ').trim().slice(0, max)
}

export function decodeHtml(value = '') {
  return String(value)
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n) || 32))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCodePoint(Number.parseInt(n, 16) || 32))
}

export function textFromHtml(value = '') {
  return clean(
    decodeHtml(
      String(value)
        .replace(/<script\b[\s\S]*?<\/script>/gi, ' ')
        .replace(/<style\b[\s\S]*?<\/style>/gi, ' ')
        .replace(/<br\s*\/?>/gi, '\n')
        .replace(/<\/p\s*>/gi, '\n')
        .replace(/<\/div\s*>/gi, '\n')
        .replace(/<[^>]+>/g, ' ')
    ),
    50_000,
  )
}

export function attr(tag, name) {
  const rx = new RegExp('\\b' + String(name) + '\\s*=\\s*["\\\']([^"\\\']*)["\\\']', 'i')
  return decodeHtml(rx.exec(String(tag || ''))?.[1] || '')
}

export function absolute(value, base) {
  try { return new URL(String(value || ''), base).href } catch { return '' }
}

export function anchorPairs(html, base = '') {
  const rows = []
  const rx = /<a\b([^>]*)>([\s\S]*?)<\/a>/gi
  for (const match of String(html || '').matchAll(rx)) {
    const tag = match[1] || ''
    const hrefRaw = attr(tag, 'href')
    if (!hrefRaw) continue
    rows.push({
      href:base ? absolute(hrefRaw, base) : hrefRaw,
      rawHref:hrefRaw,
      text:textFromHtml(match[2]),
      tag,
      className:attr(tag, 'class'),
    })
  }
  return rows
}

function challengeLike(status, text) {
  if (![403, 429, 503].includes(Number(status))) return false
  return /just a moment|cloudflare|cf-chl-|challenge-platform|captcha|attention required|verify you are human/i.test(String(text || ''))
}

async function directFetchText(url, {
  headers = {},
  method = 'GET',
  body,
  timeoutMs = 20000,
  redirect = 'follow',
} = {}) {
  const response = await fetch(url, {
    method,
    headers:{
      'user-agent':ANIME_UA,
      accept:'*/*',
      ...headers,
    },
    body,
    redirect,
    signal:AbortSignal.timeout(timeoutMs),
  })
  const text = await response.text()
  return {
    text,
    status:response.status,
    finalUrl:response.url || String(url),
    headers:Object.fromEntries(response.headers.entries()),
    ok:response.ok,
  }
}

async function flareGet(url, { timeoutMs = 60000 } = {}) {
  const response = await fetch(FLARE_URL + '/v1', {
    method:'POST',
    headers:{ 'content-type':'application/json' },
    body:JSON.stringify({
      cmd:'request.get',
      url:String(url),
      maxTimeout:timeoutMs,
    }),
    signal:AbortSignal.timeout(timeoutMs + 10000),
  })
  if (!response.ok) throw new Error('FlareSolverr HTTP ' + response.status)
  const payload = await response.json()
  if (payload?.status !== 'ok') throw new Error('FlareSolverr: ' + clean(payload?.message || 'request failed', 220))
  const solution = payload?.solution || {}
  const cookies = Array.isArray(solution.cookies)
    ? solution.cookies.map(cookie => `${cookie.name}=${cookie.value}`).join('; ')
    : ''
  return {
    text:String(solution.response || ''),
    status:Number(solution.status || 0) || 200,
    finalUrl:String(solution.url || url),
    headers:{
      ...(cookies ? { cookie:cookies } : {}),
      ...(solution.userAgent ? { 'user-agent':String(solution.userAgent) } : {}),
    },
    ok:Number(solution.status || 200) < 400,
  }
}

export async function fetchText(url, options = {}) {
  let direct
  try {
    direct = await directFetchText(url, options)
  } catch (error) {
    if (options.allowFlare === false || String(options.method || 'GET').toUpperCase() !== 'GET') throw error
    return flareGet(url, { timeoutMs:Math.max(30000, Number(options.timeoutMs || 20000)) })
  }

  if (direct.ok) return direct
  if (
    options.allowFlare !== false &&
    String(options.method || 'GET').toUpperCase() === 'GET' &&
    challengeLike(direct.status, direct.text)
  ) {
    try {
      return await flareGet(url, { timeoutMs:Math.max(30000, Number(options.timeoutMs || 20000)) })
    } catch {}
  }

  const error = new Error(`HTTP ${direct.status} for ${new URL(String(url)).hostname}`)
  error.status = direct.status
  error.bodyPreview = direct.text.slice(0, 300)
  throw error
}

export async function fetchJson(url, options = {}) {
  const result = await fetchText(url, {
    ...options,
    headers:{ accept:'application/json', ...(options.headers || {}) },
  })
  try {
    return { ...result, data:JSON.parse(result.text) }
  } catch {
    const error = new Error('Invalid JSON from ' + new URL(String(url)).hostname)
    error.bodyPreview = result.text.slice(0, 300)
    throw error
  }
}

export function safeFileName(value, extension = '') {
  const base = clean(value, 180)
    .replace(/[\\/:*?"<>|\x00-\x1f]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim() || 'anime'
  const ext = String(extension || '').replace(/^\./, '').replace(/[^a-z0-9.]/gi, '')
  return ext ? `${base}.${ext}` : base
}

function ffmpegHeaderString(headers = {}) {
  return Object.entries(headers)
    .filter(([, value]) => value != null && String(value).trim())
    .map(([key, value]) => `${key}: ${String(value).trim()}`)
    .join('\r\n') + (Object.keys(headers).length ? '\r\n' : '')
}

function selectVariant(variants, quality) {
  if (!Array.isArray(variants) || !variants.length) return null
  const rows = [...variants].sort((a,b) => Number(b.height || 0) - Number(a.height || 0))
  const wanted = Number(String(quality || '').replace(/[^0-9]/g, ''))
  if (!wanted) return rows[0]
  return rows.find(row => Number(row.height || 0) <= wanted) || rows.at(-1)
}

export async function sendHls({
  context,
  url,
  title,
  quality = 'source',
  delivery = 'document',
  headers = {},
  timeoutMs = 30 * 60_000,
}) {
  if (typeof context?.send !== 'function') throw new Error('Anime delivery context is unavailable.')

  const resolved = await resolveM3u8({
    url,
    headers,
    fetchText:async (target, inheritedHeaders) => (await fetchText(target, {
      headers:inheritedHeaders,
      timeoutMs:30000,
      allowFlare:false,
    })).text,
    chooseVariant:variants => selectVariant(variants, quality),
  })

  const directory = await mkdtemp(join(tmpdir(), 'mscc-anime-hls-'))
  const output = join(directory, 'episode.mp4')
  const headerString = ffmpegHeaderString(headers)
  const args = []
  if (headers['user-agent'] || headers['User-Agent']) {
    args.push('-user_agent', String(headers['user-agent'] || headers['User-Agent']))
  }
  if (headerString.trim()) args.push('-headers', headerString)
  args.push(
    '-i', resolved.playlistUrl,
    '-map', '0:v:0?',
    '-map', '0:a:0?',
    '-c', 'copy',
    '-movflags', '+faststart',
    output,
  )

  try {
    await runFfmpeg(args, { timeoutMs })
    const fileName = safeFileName(title || 'episode', 'mp4')
    if (delivery === 'video') {
      await context.send({ video:{ url:output }, mimetype:'video/mp4', caption:title || fileName })
    } else {
      await context.send({ document:{ url:output }, mimetype:'video/mp4', fileName })
    }
    return { delivered:true, fileName, playlistUrl:resolved.playlistUrl }
  } finally {
    await rm(directory, { recursive:true, force:true }).catch(() => {})
  }
}

export async function sendRemoteMedia({
  context,
  url,
  title,
  delivery = 'document',
  mimetype = 'video/mp4',
  extension = 'mp4',
  headers = {},
}) {
  if (typeof context?.send !== 'function') throw new Error('Anime delivery context is unavailable.')
  const fileName = safeFileName(title || 'episode', extension)
  const media = { url, ...(Object.keys(headers).length ? { headers } : {}) }
  if (delivery === 'video' && /^video\//i.test(mimetype)) {
    await context.send({ video:media, mimetype, caption:title || fileName })
  } else {
    await context.send({ document:media, mimetype, fileName })
  }
  return { delivered:true, fileName }
}

export async function sendStream({
  context,
  url,
  title,
  quality = 'source',
  delivery = 'document',
  headers = {},
  timeoutMs = 30 * 60_000,
}) {
  if (/\.m3u8(?:$|[?#])/i.test(String(url || ''))) {
    return sendHls({ context, url, title, quality, delivery, headers, timeoutMs })
  }

  if (typeof context?.send !== 'function') throw new Error('Anime delivery context is unavailable.')
  const directory = await mkdtemp(join(tmpdir(), 'mscc-anime-stream-'))
  const output = join(directory, 'episode.mp4')
  const headerString = ffmpegHeaderString(headers)
  const args = []
  if (headers['user-agent'] || headers['User-Agent']) {
    args.push('-user_agent', String(headers['user-agent'] || headers['User-Agent']))
  }
  if (headerString.trim()) args.push('-headers', headerString)
  args.push(
    '-i', String(url),
    '-map', '0:v:0?',
    '-map', '0:a:0?',
    '-c', 'copy',
    '-movflags', '+faststart',
    output,
  )

  try {
    await runFfmpeg(args, { timeoutMs })
    const fileName = safeFileName(title || 'episode', 'mp4')
    if (delivery === 'video') {
      await context.send({ video:{ url:output }, mimetype:'video/mp4', caption:title || fileName })
    } else {
      await context.send({ document:{ url:output }, mimetype:'video/mp4', fileName })
    }
    return { delivered:true, fileName }
  } finally {
    await rm(directory, { recursive:true, force:true }).catch(() => {})
  }
}
