import { spawn } from 'node:child_process'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { downloadHlsSegments, remuxHlsSegments, resolveM3u8 } from '../../utils/media/hls.js'

export const ANIME_UA = 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36'

const FLARE_URL = String(
  process.env.MSCC_FLARESOLVERR_URL ||
  ('http://127.0.0.1:' + (process.env.MSCC_FLARE_PORT || '8191'))
).replace(/\/$/, '')
const FLARE_TIMEOUT = Number(process.env.MSCC_ANIME_FLARE_TIMEOUT_MS || 60000)

function runAnimeFfmpeg(args, { timeoutMs = 120000 } = {}) {
  const binary = String(process.env.FFMPEG_PATH || 'ffmpeg').trim() || 'ffmpeg'
  return new Promise((resolve, reject) => {
    const child = spawn(binary, ['-hide_banner','-loglevel','error','-y', ...args], {
      stdio:['ignore','ignore','pipe'],
    })
    let stderr = ''
    let settled = false
    let timedOut = false
    const timer = setTimeout(() => {
      timedOut = true
      child.kill('SIGKILL')
    }, timeoutMs)
    timer.unref?.()

    const finish = error => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      if (error) reject(error)
      else resolve()
    }
    child.stderr?.on('data', chunk => {
      stderr += chunk.toString()
      if (stderr.length > 32000) stderr = stderr.slice(-32000)
    })
    child.once('error', error => {
      finish(error?.code === 'ENOENT' ? new Error('ffmpeg is not installed on this MSCC host.') : error)
    })
    child.once('close', code => {
      if (timedOut) return finish(new Error('Anime media conversion timed out.'))
      if (code === 0) return finish()
      const detail = stderr.trim().split(/\r?\n/).slice(-4).join(' | ')
      return finish(new Error(detail ? 'ffmpeg failed (' + code + '): ' + detail : 'ffmpeg failed with code ' + code))
    })
  })
}

function challengeLike(status, text) {
  if (![403, 429, 503].includes(Number(status))) return false
  return /just a moment|cloudflare|cf-chl-|challenge-platform|captcha|attention required|verify you are human/i.test(String(text || ''))
}

async function flareText(url) {
  const response = await fetch(FLARE_URL + '/v1', {
    method:'POST',
    headers:{ 'content-type':'application/json' },
    body:JSON.stringify({ cmd:'request.get', url:String(url), maxTimeout:FLARE_TIMEOUT }),
    signal:AbortSignal.timeout(FLARE_TIMEOUT + 10000),
  })
  if (!response.ok) throw new Error('FlareSolverr HTTP ' + response.status)
  const data = await response.json()
  const solution = data?.solution || {}
  if (data?.status !== 'ok' || Number(solution.status || 0) >= 400) {
    throw new Error('Browser verification failed for ' + new URL(String(url)).hostname)
  }
  return {
    text:String(solution.response || ''),
    response:{
      ok:true,
      status:Number(solution.status || 200),
      url:String(solution.url || url),
      headers:new Headers(),
    },
  }
}

export function safeName(value, fallback = 'anime') {
  const clean = String(value || fallback)
    .replace(/[\\/:*?"<>|\u0000-\u001f]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 180)
  return clean || fallback
}

export async function fetchText(url, headers = {}, timeoutMs = 25000) {
  try {
    const response = await fetch(url, {
      headers:{ 'user-agent':ANIME_UA, ...headers },
      redirect:'follow',
      signal:AbortSignal.timeout(timeoutMs),
    })
    const text = await response.text()
    if (response.ok && !challengeLike(response.status, text)) return { text, response }
    if (!challengeLike(response.status, text)) {
      const error = new Error('HTTP ' + response.status + ' for ' + new URL(url).hostname)
      error.status = response.status
      error.body = text.slice(0, 800)
      throw error
    }
  } catch (error) {
    if (error?.status && ![403,429,503].includes(Number(error.status))) throw error
  }
  return flareText(url)
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
  concurrency = 8,
  fetchTextFn,
  fetchBytesFn,
}) {
  const directory = await mkdtemp(join(tmpdir(), 'mscc-anime-hls-'))
  const output = join(directory, safeName(title) + '.mp4')
  const textLoader = typeof fetchTextFn === 'function'
    ? fetchTextFn
    : async (target, requestHeaders) => (await fetchText(target, requestHeaders, 45000)).text
  const byteLoader = typeof fetchBytesFn === 'function'
    ? fetchBytesFn
    : (target, requestHeaders) => fetchBytes(target, requestHeaders, 60000)

  try {
    const resolved = await resolveM3u8({
      url,
      headers,
      fetchText:textLoader,
      chooseVariant:variants => chooseVariant(variants, quality),
    })

    const downloaded = await downloadHlsSegments({
      segments:resolved.segments,
      directory,
      headers,
      concurrency,
      fetchBytes:byteLoader,
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


export async function deliverStreamWithFfmpeg(context, {
  url,
  headers = {},
  title = 'anime',
  delivery = 'document',
}) {
  const directory = await mkdtemp(join(tmpdir(), 'mscc-anime-stream-'))
  const output = join(directory, safeName(title) + '.mp4')
  const headerBlob = Object.entries(headers || {})
    .map(([key, value]) => key + ': ' + String(value))
    .join('\\r\\n')

  try {
    const args = []
    if (headers?.['user-agent'] || headers?.['User-Agent']) {
      args.push('-user_agent', String(headers['user-agent'] || headers['User-Agent']))
    }
    if (headerBlob) args.push('-headers', headerBlob + '\\r\\n')
    args.push('-i', url, '-map', '0:v:0?', '-map', '0:a:0?', '-c', 'copy', '-movflags', '+faststart', output)
    await runAnimeFfmpeg(args, { timeoutMs:15 * 60 * 1000 })

    if (delivery === 'video') {
      await context.send({ video:{ url:output }, mimetype:'video/mp4', caption:safeName(title) })
    } else {
      await context.send({ document:{ url:output }, mimetype:'video/mp4', fileName:safeName(title) + '.mp4' })
    }
    return { delivered:true }
  } finally {
    await rm(directory, { recursive:true, force:true }).catch(() => {})
  }
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
