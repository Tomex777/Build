import AdmZip from 'adm-zip'
import * as cheerio from 'cheerio'
import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

export const MANGA_UA = 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36'
const NYORA_BASE = String(process.env.MSCC_NYORA_HELPER_URL || 'https://api.nyora.xyz').replace(/\/$/, '')
const FLARE_URL = String(process.env.MSCC_FLARESOLVERR_URL || ('http://127.0.0.1:' + (process.env.MSCC_FLARE_PORT || '8191'))).replace(/\/$/, '')
const FLARE_TIMEOUT = Number(process.env.MSCC_MANGA_FLARE_TIMEOUT_MS || 60000)

export function safeName(value, fallback = 'manga') {
  const clean = String(value || fallback)
    .replace(/[\\/:*?"<>|\u0000-\u001f]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 160)
  return clean || fallback
}

export function absolute(href, base) {
  try { return new URL(String(href || ''), String(base || '')).href } catch { return '' }
}

export function chapterNumber(value, fallback = 0) {
  const raw = String(value || '')
  const matches = [...raw.matchAll(/(?:chapter|ch\.?|episode|ep\.?)?\s*([0-9]+(?:\.[0-9]+)?)/gi)]
  if (!matches.length) return String(fallback || '')
  return String(matches.at(-1)[1])
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
    response:{ ok:true, status:Number(solution.status || 200), url:String(solution.url || url), headers:new Headers() },
  }
}

export async function fetchText(url, headers = {}, timeoutMs = 25000) {
  try {
    const response = await fetch(url, {
      headers:{ 'user-agent':MANGA_UA, accept:'text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8', ...headers },
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

export async function fetchJson(url, headers = {}, timeoutMs = 25000, init = {}) {
  const response = await fetch(url, {
    ...init,
    headers:{ 'user-agent':MANGA_UA, accept:'application/json, text/plain, */*', ...headers, ...(init.headers || {}) },
    redirect:'follow',
    signal:init.signal || AbortSignal.timeout(timeoutMs),
  })
  const text = await response.text()
  if (!response.ok) {
    const error = new Error('HTTP ' + response.status + ' for ' + new URL(url).hostname)
    error.status = response.status
    error.body = text.slice(0, 800)
    throw error
  }
  try { return { data:JSON.parse(text), response } }
  catch { throw new Error('Invalid JSON from ' + new URL(url).hostname) }
}

export async function load(url, headers = {}) {
  const { text, response } = await fetchText(url, headers)
  return { $, text, response }
  function $(selector) {
    return cheerio.load(text)(selector)
  }
}

export async function loadDocument(url, headers = {}) {
  const { text, response } = await fetchText(url, headers)
  return { $:cheerio.load(text), text, response }
}

export async function fetchBytes(url, headers = {}, timeoutMs = 60000) {
  const response = await fetch(url, {
    headers:{ 'user-agent':MANGA_UA, ...headers },
    redirect:'follow',
    signal:AbortSignal.timeout(timeoutMs),
  })
  if (!response.ok && response.status !== 206) throw new Error('Image HTTP ' + response.status + ' for ' + new URL(url).hostname)
  const data = Buffer.from(await response.arrayBuffer())
  if (!data.length) throw new Error('Empty image from ' + new URL(url).hostname)
  return { data, contentType:String(response.headers.get('content-type') || '') }
}

function imageExt(url, contentType = '') {
  const type = String(contentType).toLowerCase()
  if (type.includes('png')) return 'png'
  if (type.includes('webp')) return 'webp'
  if (type.includes('gif')) return 'gif'
  if (type.includes('avif')) return 'avif'
  if (type.includes('jpeg') || type.includes('jpg')) return 'jpg'
  const ext = /\.(jpe?g|png|webp|gif|avif)(?:$|[?#])/i.exec(String(url || ''))?.[1]?.toLowerCase()
  return ext === 'jpeg' ? 'jpg' : (ext || 'jpg')
}

export async function deliverCbz(context, {
  pages,
  title,
  chapterTitle = 'Chapter',
  headers = {},
}) {
  if (!context?.send) throw new Error('Manga delivery context is unavailable.')
  const list = (Array.isArray(pages) ? pages : [])
    .map(page => typeof page === 'string' ? { url:page } : page)
    .filter(page => page?.url)
  if (!list.length) throw new Error('No manga pages were resolved for this chapter.')

  const directory = await mkdtemp(join(tmpdir(), 'mscc-manga-'))
  const zipPath = join(directory, safeName(title) + ' - ' + safeName(chapterTitle) + '.cbz')
  const zip = new AdmZip()
  try {
    for (let i = 0; i < list.length; i += 1) {
      const page = list[i]
      const requestHeaders = { ...headers, ...(page.headers || {}) }
      const { data, contentType } = await fetchBytes(page.url, requestHeaders)
      const ext = imageExt(page.url, contentType)
      const fileName = String(i + 1).padStart(4, '0') + '.' + ext
      const pagePath = join(directory, fileName)
      await writeFile(pagePath, data)
      zip.addLocalFile(pagePath)
    }
    zip.writeZip(zipPath)
    await context.send({
      document:{ url:zipPath },
      mimetype:'application/vnd.comicbook+zip',
      fileName:safeName(title) + ' - ' + safeName(chapterTitle) + '.cbz',
    })
    return { delivered:true, pages:list.length }
  } finally {
    await rm(directory, { recursive:true, force:true }).catch(() => {})
  }
}

export function normalizedChapter(chapter, index = 0) {
  return {
    id:String(chapter?.id ?? chapter?.url ?? chapter?.chapterUrl ?? index + 1),
    number:String(chapter?.number ?? chapter?.chapterNumber ?? chapterNumber(chapter?.title || chapter?.name, index + 1)),
    title:String(chapter?.title || chapter?.name || ('Chapter ' + (index + 1))),
    url:String(chapter?.url || chapter?.chapterUrl || chapter?.id || ''),
    branch:chapter?.branch || null,
  }
}

export async function deliverRange({ listChapters, resolvePages, context, item, range, headers }) {
  const chapters = await listChapters(item)
  const low = Number(range?.start?.number)
  const high = Number(range?.end?.number)
  if (!Number.isFinite(low) || !Number.isFinite(high)) throw new Error('Invalid manga chapter range.')
  const min = Math.min(low, high)
  const max = Math.max(low, high)
  const selected = chapters.filter(ch => {
    const n = Number(ch.number)
    return Number.isFinite(n) && n >= min && n <= max
  })
  if (!selected.length) throw new Error('No chapters matched that range.')
  for (const chapter of selected) {
    const pages = await resolvePages(chapter, item)
    await deliverCbz(context, { pages, title:item?.title || 'Manga', chapterTitle:chapter.title, headers })
  }
  return { delivered:true, count:selected.length }
}

let nyoraCatalogCache = null
async function nyoraCatalog() {
  if (nyoraCatalogCache) return nyoraCatalogCache
  const { data } = await fetchJson(NYORA_BASE + '/sources/catalog', {}, 45000)
  nyoraCatalogCache = Array.isArray(data) ? data : (data?.entries || data?.sources || [])
  return nyoraCatalogCache
}

function norm(value) {
  return String(value || '').toLowerCase().replace(/[^a-z0-9]+/g, '')
}

async function resolveNyoraSource(aliases) {
  const catalog = await nyoraCatalog()
  const wanted = (Array.isArray(aliases) ? aliases : [aliases]).map(norm).filter(Boolean)
  const ranked = catalog.map(source => {
    const id = norm(String(source?.id || '').split(':').at(-1))
    const name = norm(source?.name || source?.title)
    let score = 0
    for (const value of wanted) {
      if (id === value || name === value) score = Math.max(score, 100)
      else if (id.includes(value) || name.includes(value) || value.includes(id) || value.includes(name)) score = Math.max(score, 50)
    }
    return { source, score }
  }).filter(row => row.score > 0).sort((a,b) => b.score - a.score)
  if (!ranked.length) throw new Error('Nyora catalog has no matching parser for ' + aliases.join(', '))
  return ranked[0].source
}

function nyoraEntries(data) {
  return (Array.isArray(data) ? data : (data?.entries || data?.items || []))
}

function nyoraItem(entry) {
  return {
    id:String(entry?.url || entry?.id || ''),
    title:String(entry?.title || entry?.name || 'Untitled'),
    description:String(entry?.description || entry?.subtitle || entry?.status || '').trim(),
    url:String(entry?.url || entry?.id || ''),
  }
}

export function createNyoraBridgeSource({
  id,
  name,
  aliases = [],
  description = '',
  fallbackOrder = 100,
}) {
  const sourceAliases = [name, id, ...aliases]
  let resolved = null
  const sourceId = async () => {
    if (!resolved) resolved = await resolveNyoraSource(sourceAliases)
    return String(resolved.id)
  }

  const search = async query => {
    const sid = await sourceId()
    const path = query
      ? '/sources/search?id=' + encodeURIComponent(sid) + '&q=' + encodeURIComponent(query) + '&page=1'
      : '/sources/popular?id=' + encodeURIComponent(sid) + '&page=1'
    const { data } = await fetchJson(NYORA_BASE + path, {}, 60000)
    return { items:nyoraEntries(data).slice(0, 50).map(nyoraItem) }
  }

  const chapters = async item => {
    const sid = await sourceId()
    const url = String(item?.id || item?.url || '')
    const { data } = await fetchJson(
      NYORA_BASE + '/manga/details?id=' + encodeURIComponent(sid) + '&url=' + encodeURIComponent(url),
      {}, 60000
    )
    const manga = data?.manga || item || {}
    return {
      title:String(manga?.title || item?.title || name),
      chapters:(data?.chapters || manga?.chapters || []).map(normalizedChapter),
    }
  }

  const pages = async chapter => {
    const sid = await sourceId()
    const url = String(chapter?.url || chapter?.id || '')
    const { data } = await fetchJson(
      NYORA_BASE + '/manga/pages?id=' + encodeURIComponent(sid) + '&url=' + encodeURIComponent(url),
      {}, 60000
    )
    return (data?.pages || []).map(page => ({
      url:String(page?.url || page?.imageUrl || ''),
      headers:page?.headers || {},
    })).filter(page => page.url)
  }

  return {
    id,
    name,
    description:description || (name + ' through the live Nyora parser catalog.'),
    fallbackOrder,
    brandAliases:[name, ...aliases],
    async run({ action, query, item, chapter, chapterId, range, context }) {
      if (action === 'search' || action === 'browse') return search(action === 'search' ? String(query || '').trim() : '')
      if (action === 'chapters') return chapters(item)
      if (action === 'options') return { qualities:['source'], deliveries:['document'] }
      if (action === 'download') {
        const target = chapter || { id:chapterId, url:chapterId, title:'Chapter' }
        const pageList = await pages(target)
        await deliverCbz(context, {
          pages:pageList,
          title:item?.title || name,
          chapterTitle:target?.title || ('Chapter ' + (target?.number || '')),
        })
        return { delivered:true }
      }
      if (action === 'downloadRange') {
        return deliverRange({
          listChapters:async () => (await chapters(item)).chapters,
          resolvePages:pages,
          context,
          item,
          range,
        })
      }
      throw new Error('Unsupported ' + name + ' action: ' + action)
    },
    _probe:{
      sourceId,
      search,
      chapters,
      pages,
    },
  }
}
