export const MUSIC_UA = 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140 Mobile Safari/537.36'

export function clean(value, max = 240) {
  return String(value ?? '').replace(/\s+/g, ' ').trim().slice(0, max)
}

export function durationText(value) {
  const total = Math.max(0, Math.round(Number(value) || 0))
  if (!total) return ''
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  return hours
    ? [hours, String(minutes).padStart(2, '0'), String(seconds).padStart(2, '0')].join(':')
    : [minutes, String(seconds).padStart(2, '0')].join(':')
}

export function mimeForUrl(url, fallback = 'audio/mpeg') {
  let path = ''
  try { path = new URL(String(url || '')).pathname.toLowerCase() } catch {}
  if (path.endsWith('.flac')) return 'audio/flac'
  if (path.endsWith('.ogg') || path.endsWith('.oga')) return 'audio/ogg'
  if (path.endsWith('.opus')) return 'audio/ogg; codecs=opus'
  if (path.endsWith('.m4a') || path.endsWith('.mp4')) return 'audio/mp4'
  if (path.endsWith('.wav')) return 'audio/wav'
  if (path.endsWith('.webm')) return 'audio/webm'
  return fallback
}

export function extensionForMime(mime = '') {
  const value = String(mime).toLowerCase()
  if (value.includes('flac')) return 'flac'
  if (value.includes('ogg') || value.includes('opus')) return 'ogg'
  if (value.includes('mp4') || value.includes('m4a')) return 'm4a'
  if (value.includes('wav')) return 'wav'
  if (value.includes('webm')) return 'webm'
  return 'mp3'
}

export function safeFileName(title, artist = '', extension = 'mp3') {
  const base = [clean(artist, 80), clean(title, 120)].filter(Boolean).join(' - ') || 'track'
  const safe = base
    .replace(/[\\/:*?"<>|\x00-\x1f]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 180)
  return `${safe || 'track'}.${String(extension || 'mp3').replace(/[^a-z0-9]/gi, '') || 'mp3'}`
}

export async function fetchText(url, {
  headers = {},
  method = 'GET',
  body,
  timeoutMs = 15000,
} = {}) {
  const response = await fetch(url, {
    method,
    headers:{
      'user-agent':MUSIC_UA,
      'accept':'*/*',
      ...headers,
    },
    body,
    redirect:'follow',
    signal:AbortSignal.timeout(timeoutMs),
  })
  const text = await response.text()
  if (!response.ok) {
    const error = new Error(`HTTP ${response.status} for ${new URL(url).hostname}`)
    error.status = response.status
    error.bodyPreview = text.slice(0, 300)
    throw error
  }
  return { text, response }
}

export async function fetchJson(url, {
  headers = {},
  method = 'GET',
  body,
  timeoutMs = 15000,
} = {}) {
  const payload = body === undefined
    ? undefined
    : (typeof body === 'string' || body instanceof Uint8Array ? body : JSON.stringify(body))
  const extra = payload === undefined ? {} : { 'content-type':'application/json' }
  const { text, response } = await fetchText(url, {
    headers:{ ...extra, ...headers },
    method,
    body:payload,
    timeoutMs,
  })
  try {
    return { data:JSON.parse(text), response }
  } catch {
    const error = new Error(`Invalid JSON from ${new URL(url).hostname}`)
    error.bodyPreview = text.slice(0, 300)
    throw error
  }
}

export async function probeMedia(url, {
  headers = {},
  timeoutMs = 12000,
} = {}) {
  const response = await fetch(url, {
    headers:{
      'user-agent':MUSIC_UA,
      'accept':'*/*',
      'range':'bytes=0-1023',
      ...headers,
    },
    redirect:'follow',
    signal:AbortSignal.timeout(timeoutMs),
  })
  try {
    if (!response.ok && response.status !== 206) {
      throw new Error(`Media HTTP ${response.status}`)
    }
    const type = response.headers.get('content-type') || ''
    if (/text\/html|application\/json/i.test(type)) {
      throw new Error('Media endpoint returned a non-audio response')
    }
    return {
      url:response.url || url,
      mimetype:type.split(';')[0] || mimeForUrl(response.url || url),
      contentLength:Number(response.headers.get('content-length') || 0) || 0,
      contentRange:response.headers.get('content-range') || '',
    }
  } finally {
    await response.body?.cancel?.().catch?.(() => {})
  }
}

export async function sendAudio(context, {
  url,
  title = '',
  artist = '',
  mimetype = '',
  fileName = '',
} = {}) {
  if (!url) throw new Error('No media URL was resolved.')
  if (typeof context?.send !== 'function') throw new Error('Music delivery context is unavailable.')
  const mime = mimetype || mimeForUrl(url)
  const name = fileName || safeFileName(title, artist, extensionForMime(mime))
  await context.send({
    audio:{ url },
    mimetype:mime,
    fileName:name,
    ptt:false,
  })
  return { delivered:true }
}

export function textFromRuns(value) {
  if (!value) return ''
  if (typeof value === 'string') return clean(value)
  if (typeof value.simpleText === 'string') return clean(value.simpleText)
  if (Array.isArray(value.runs)) return clean(value.runs.map(run => run?.text || '').join(''))
  return ''
}

export function walkObjects(value, visitor, depth = 0) {
  if (depth > 14 || value == null) return
  if (Array.isArray(value)) {
    for (const item of value) walkObjects(item, visitor, depth + 1)
    return
  }
  if (typeof value !== 'object') return
  visitor(value)
  for (const child of Object.values(value)) {
    if (child && typeof child === 'object') walkObjects(child, visitor, depth + 1)
  }
}

export function firstHttpUrl(value, predicate = () => true) {
  let found = ''
  walkObjects(value, object => {
    if (found) return
    for (const candidate of [object.url, object.link, object.audio, object.stream, object.streamUrl]) {
      if (typeof candidate === 'string' && /^https?:\/\//i.test(candidate) && predicate(candidate, object)) {
        found = candidate
        return
      }
    }
  })
  return found
}

export function extractBalancedJson(text, marker) {
  const source = String(text || '')
  let start = source.indexOf(marker)
  if (start < 0) return null
  start = source.indexOf('{', start + marker.length)
  if (start < 0) return null
  let depth = 0
  let quote = ''
  let escaped = false
  for (let i = start; i < source.length; i++) {
    const ch = source[i]
    if (quote) {
      if (escaped) escaped = false
      else if (ch === '\\') escaped = true
      else if (ch === quote) quote = ''
      continue
    }
    if (ch === '"' || ch === "'") {
      quote = ch
      continue
    }
    if (ch === '{') depth++
    else if (ch === '}') {
      depth--
      if (depth === 0) {
        try { return JSON.parse(source.slice(start, i + 1)) } catch { return null }
      }
    }
  }
  return null
}
