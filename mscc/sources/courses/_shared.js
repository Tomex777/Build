export const COURSE_UA = 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140 Mobile Safari/537.36 MSCC/2.3'

export function clean(value, max = 400) {
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
  return decodeHtml(
    String(value)
      .replace(/<script\b[\s\S]*?<\/script>/gi, ' ')
      .replace(/<style\b[\s\S]*?<\/style>/gi, ' ')
      .replace(/<br\s*\/?>/gi, '\n')
      .replace(/<\/(?:p|div|section|article|li|h[1-6])\s*>/gi, '\n')
      .replace(/<[^>]+>/g, ' ')
  )
    .replace(/\r/g, '')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n[ \t]+/g, '\n')
    .replace(/[ \t]{2,}/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

export function absoluteUrl(base, href = '') {
  try { return new URL(String(href || ''), String(base || '')).href } catch { return '' }
}

export function safeFileName(value, extension = '') {
  const base = clean(value, 180)
    .replace(/[\\/:*?"<>|\x00-\x1f]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim() || 'course'
  const ext = String(extension || '').replace(/^\./, '').replace(/[^a-z0-9]/gi, '')
  return ext ? `${base}.${ext}` : base
}

export async function fetchText(url, {
  headers = {},
  method = 'GET',
  body,
  timeoutMs = 25000,
} = {}) {
  const response = await fetch(url, {
    method,
    headers:{
      'user-agent':COURSE_UA,
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

export async function fetchJson(url, options = {}) {
  const { text, response } = await fetchText(url, {
    ...options,
    headers:{ accept:'application/json', ...(options.headers || {}) },
  })
  try {
    return { data:JSON.parse(text), response }
  } catch {
    const error = new Error(`Invalid JSON from ${new URL(url).hostname}`)
    error.bodyPreview = text.slice(0, 300)
    throw error
  }
}

export async function sendDocument(context, {
  url = '',
  data = null,
  fileName = 'course',
  mimetype = 'application/octet-stream',
} = {}) {
  if (typeof context?.send !== 'function') throw new Error('Course delivery context is unavailable.')
  let document
  if (data != null) document = Buffer.isBuffer(data) ? data : Buffer.from(data)
  else if (url) document = { url }
  else throw new Error('Course source returned no document.')
  await context.send({ document, fileName, mimetype })
  return { delivered:true }
}
