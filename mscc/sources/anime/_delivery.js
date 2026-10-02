import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { downloadHlsSegments, remuxHlsSegments, resolveM3u8 } from '../../utils/media/hls.js'

export const ANIME_UA = 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36'

export function safeName(value, fallback = 'anime') {
  const clean = String(value || fallback)
    .replace(/[\\/:*?"<>|\u0000-\u001f]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 180)
  return clean || fallback
}

export async function fetchText(url, headers = {}, timeoutMs = 25000) {
  const response = await fetch(url, {
    headers:{ 'user-agent':ANIME_UA, ...headers },
    redirect:'follow',
    signal:AbortSignal.timeout(timeoutMs),
  })
  const text = await response.text()
  if (!response.ok) {
    const error = new Error('HTTP ' + response.status + ' for ' + new URL(url).hostname)
    error.status = response.status
    error.body = text.slice(0, 800)
    throw error
  }
  return { text, response }
}

export async function fetchJson(url, headers = {}, timeoutMs = 25000) {
  const { text, response } = await fetchText(url, {
    accept:'application/json, text/plain, */*',
    ...headers,
  }, timeoutMs)
  try {
    return { data:JSON.parse(text), response }
  } catch {
    throw new Error('Invalid JSON from ' + new URL(url).hostname)
  }
}

export async function fetchBytes(url, headers = {}, timeoutMs = 45000) {
  const response = await fetch(url, {
    headers:{ 'user-agent':ANIME_UA, ...headers },
    redirect:'follow',
    signal:AbortSignal.timeout(timeoutMs),
  })
  if (!response.ok && response.status !== 206) {
    throw new Error('Media HTTP ' + response.status + ' for ' + new URL(url).hostname)
  }
  return Buffer.from(await response.arrayBuffer())
}

function qualityNumber(value) {
  const number = Number(String(value || '').replace(/[^0-9]/g, ''))
  return Number.isFinite(number) && number > 0 ? number : 0
}

function chooseVariant(variants, quality) {
  if (!variants.length) return null
  const wanted = qualityNumber(quality)
  const ranked = [...variants].sort((a,b) => (a.height || 0) - (b.height || 0))
  if (!wanted) return ranked.at(-1)
  return ranked.filter(row => (row.height || 0) <= wanted).at(-1)
    || ranked.find(row => (row.height || 0) >= wanted)
    || ranked.at(-1)
}

export async function deliverHls(context, {
  url,
  headers = {},
  title = 'anime',
  quality = 'source',
  delivery = 'document',
}) {
  const directory = await mkdtemp(join(tmpdir(), 'mscc-anime-hls-'))
  const output = join(directory, safeName(title) + '.mp4')

  try {
    const resolved = await resolveM3u8({
      url,
      headers,
      fetchText:async (target, requestHeaders) => (await fetchText(target, requestHeaders, 45000)).text,
      chooseVariant:variants => chooseVariant(variants, quality),
    })

    const downloaded = await downloadHlsSegments({
      segments:resolved.segments,
      directory,
      headers,
      concurrency:8,
      fetchBytes:(target, requestHeaders) => fetchBytes(target, requestHeaders, 60000),
    })

    await remuxHlsSegments({
      directory,
      count:downloaded.total,
      output,
    })

    if (delivery === 'video') {
      await context.send({
        video:{ url:output },
        mimetype:'video/mp4',
        caption:safeName(title),
      })
    } else {
      await context.send({
        document:{ url:output },
        mimetype:'video/mp4',
        fileName:safeName(title) + '.mp4',
      })
    }
    return { delivered:true }
  } finally {
    await rm(directory, { recursive:true, force:true }).catch(() => {})
  }
}

export async function deliverRemote(context, {
  url,
  headers = {},
  title = 'anime',
  extension = 'mp4',
  mimetype = 'video/mp4',
  delivery = 'document',
}) {
  const media = { url, ...(headers && Object.keys(headers).length ? { headers } : {}) }
  if (delivery === 'video' && /^(mp4|m4v|webm)$/i.test(extension)) {
    await context.send({ video:media, mimetype, caption:safeName(title) })
  } else {
    await context.send({
      document:media,
      mimetype,
      fileName:safeName(title) + '.' + String(extension || 'bin').toLowerCase(),
    })
  }
  return { delivered:true }
}

export function decodeHtml(value = '') {
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
