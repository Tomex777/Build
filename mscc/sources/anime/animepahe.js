import { execFile } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { promisify } from 'node:util'
import {
  ANIME_UA,
  decodeHtml,
  deliverHls,
  deliverRemote,
} from './_delivery.js'

const execFileAsync = promisify(execFile)

const BASES = String(process.env.MSCC_ANIMEPAHE_BASES || 'https://animepahe.pw,https://animepahe.com,https://animepahe.org')
  .split(',')
  .map(value => value.trim().replace(/\/$/, ''))
  .filter(Boolean)
const FLARE_URL = String(
  process.env.MSCC_FLARESOLVERR_URL ||
  ('http://127.0.0.1:' + (process.env.MSCC_FLARE_PORT || '8191'))
).replace(/\/$/, '')
const FLARE_PROXY = String(process.env.MSCC_ANIMEPAHE_FLARE_PROXY || 'socks5://127.0.0.1:9050').trim()
const TOR_PROXY = String(process.env.MSCC_TOR_PROXY || 'socks5h://127.0.0.1:9050').trim()
const FLARE_TIMEOUT = Number(process.env.MSCC_ANIMEPAHE_FLARE_TIMEOUT_MS || 120000)
const HTTP_TIMEOUT = Number(process.env.MSCC_ANIMEPAHE_HTTP_TIMEOUT_MS || 60000)
const SESSION_ATTEMPTS = Math.max(1, Number(process.env.MSCC_ANIMEPAHE_SESSION_ATTEMPTS || 4))
const KWIK_TIMEOUT = Math.max(5, Number(process.env.MSCC_ANIMEPAHE_KWIK_TIMEOUT_SECONDS || 20))
const KWIK_PYTHON = String(process.env.MSCC_CURL_CFFI_PYTHON || '/opt/mscc-source-stack/venv/bin/python').trim()
const KWIK_HELPER = fileURLToPath(new URL('../../tools/resolve-kwik.py', import.meta.url))
const PROBE_QUERY = String(process.env.MSCC_ANIMEPAHE_PROBE_QUERY || 'Bleach').trim() || 'Bleach'

let browserState = null
let refreshPromise = null

function attrs(tag = '') {
  const out = {}
  for (const match of String(tag).matchAll(/([\w:-]+)\s*=\s*["']([^"']*)["']/g)) {
    out[match[1].toLowerCase()] = decodeHtml(match[2])
  }
  return out
}

function titleOf(text) {
  return decodeHtml(/<title\b[^>]*>([\s\S]*?)<\/title>/i.exec(String(text || ''))?.[1] || '')
}

function visibleText(text) {
  return decodeHtml(String(text || '')
    .replace(/<script\b[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style\b[\s\S]*?<\/style>/gi, ' '))
}

function looksBlocked(status, text) {
  const code = Number(status || 0)
  const title = titleOf(text)
  const visible = visibleText(text)
  if (/just a moment|attention required/i.test(title)) return true
  if (/verify you are human|checking your browser|performing security verification/i.test(visible)) return true
  if ([403,429,503].includes(code)) return true
  return false
}


function cookieString(cookies) {
  if (!Array.isArray(cookies)) return ''
  return cookies
    .filter(cookie => cookie?.name && cookie?.value != null)
    .map(cookie => String(cookie.name) + '=' + String(cookie.value))
    .join('; ')
}

function searchUrl(base, query) {
  return base + '/api?' + new URLSearchParams({ m:'search', q:String(query || '').trim() })
}

function parseFlareJsonBody(body) {
  let value = String(body || '').trim()
  const pre = /<pre[^>]*>([\s\S]*?)<\/pre>/i.exec(value)?.[1]
  if (pre != null) value = decodeHtml(pre)
  if (value.startsWith('<')) {
    const text = decodeHtml(value)
    const start = text.indexOf('{')
    if (start >= 0) value = text.slice(start)
  }
  return JSON.parse(value)
}

async function flareCommand(payload, timeoutMs = FLARE_TIMEOUT + 15000) {
  const response = await fetch(FLARE_URL + '/v1', {
    method:'POST',
    headers:{ 'content-type':'application/json' },
    body:JSON.stringify(payload),
    signal:AbortSignal.timeout(timeoutMs),
  })
  const raw = await response.text()
  if (!response.ok) throw new Error('FlareSolverr HTTP ' + response.status)
  let data
  try { data = JSON.parse(raw) }
  catch { throw new Error('Invalid FlareSolverr response') }
  return data
}

async function destroyBrowserSession(state = browserState) {
  if (!state?.id) return
  if (browserState?.id === state.id) browserState = null
  await flareCommand({ cmd:'sessions.destroy', session:state.id }, 15000).catch(() => {})
}

async function createBrowserSession(preferredBase = BASES[0]) {
  const token = randomUUID().replaceAll('-', '')
  const id = 'mscc-animepahe-' + token.slice(0, 20)
  const data = await flareCommand({
    cmd:'sessions.create',
    session:id,
    ...(FLARE_PROXY ? { proxy:{ url:FLARE_PROXY } } : {}),
  }, 60000)
  if (data?.status !== 'ok') {
    throw new Error('FlareSolverr could not create the AnimePahe browser session.')
  }
  return {
    id,
    base:preferredBase,
    flareProxy:FLARE_PROXY,
    torProxy:TOR_PROXY,
    cookies:'',
    userAgent:ANIME_UA,
    touchedAt:Date.now(),
  }
}

function applySolution(state, solution, base = state?.base) {
  if (!state) return
  state.base = String(base || state.base || BASES[0]).replace(/\/$/, '')
  state.cookies = cookieString(solution?.cookies) || state.cookies || ''
  state.userAgent = String(solution?.userAgent || state.userAgent || ANIME_UA)
  state.touchedAt = Date.now()
}

async function flareGet(url, state = browserState) {
  if (!state?.id) throw new Error('AnimePahe browser session is not available.')
  const data = await flareCommand({
    cmd:'request.get',
    url:String(url),
    session:state.id,
    maxTimeout:FLARE_TIMEOUT,
  })
  if (data?.status !== 'ok') {
    const error = new Error(String(data?.message || 'FlareSolverr could not open AnimePahe.'))
    error.code = 'animepahe-browser-blocked'
    throw error
  }
  const solution = data?.solution || {}
  const status = Number(solution.status || 0)
  const text = String(solution.response || '')
  if (status >= 400 || looksBlocked(status, text)) {
    const error = new Error('AnimePahe browser request was blocked.')
    error.code = 'animepahe-browser-blocked'
    error.status = status
    throw error
  }
  applySolution(state, solution, new URL(String(solution.url || url)).origin)
  return {
    text,
    finalUrl:String(solution.url || url),
    status:status || 200,
    solution,
  }
}

async function tryBrowserSearch(state, query, preferredBase = state?.base) {
  const order = [preferredBase, ...BASES].filter(Boolean)
  const seen = new Set()
  let last
  for (const rawBase of order) {
    const base = String(rawBase).replace(/\/$/, '')
    if (seen.has(base)) continue
    seen.add(base)
    try {
      const loaded = await flareGet(searchUrl(base, query), state)
      const data = parseFlareJsonBody(loaded.text)
      if (!Array.isArray(data?.data)) throw new Error('AnimePahe search returned invalid JSON data.')
      state.base = base
      return { data, base, loaded }
    } catch (error) {
      last = error
    }
  }
  throw last || new Error('AnimePahe search failed.')
}

async function refreshBrowserSession({ query = PROBE_QUERY, preferredBase = BASES[0] } = {}) {
  if (refreshPromise) return refreshPromise
  refreshPromise = (async () => {
    await destroyBrowserSession().catch(() => {})
    let last
    for (let attempt = 1; attempt <= SESSION_ATTEMPTS; attempt += 1) {
      const state = await createBrowserSession(preferredBase)
      try {
        const search = await tryBrowserSearch(state, query, preferredBase)
        browserState = state
        return { state, search }
      } catch (error) {
        last = error
        await destroyBrowserSession(state).catch(() => {})
      }
    }
    throw last || new Error('AnimePahe browser verification failed.')
  })()
  try { return await refreshPromise }
  finally { refreshPromise = null }
}

async function browserSearch(query) {
  const q = String(query || '').trim()
  if (!q) return { data:{ data:[] }, base:BASES[0] }
  if (browserState?.id) {
    try { return await tryBrowserSearch(browserState, q, browserState.base) }
    catch { await destroyBrowserSession().catch(() => {}) }
  }
  return (await refreshBrowserSession({ query:q })).search
}

async function curlRequest(url, {
  headers = {},
  binary = false,
  timeoutMs = HTTP_TIMEOUT,
  proxy = browserState?.torProxy || TOR_PROXY,
} = {}) {
  const seconds = Math.max(5, Math.ceil(Number(timeoutMs || HTTP_TIMEOUT) / 1000))
  const args = [
    '--silent','--show-error','--location','--compressed',
    '--connect-timeout','12',
    '--max-time',String(seconds),
    '--proxy',String(proxy || TOR_PROXY),
  ]
  for (const [name, value] of Object.entries(headers || {})) {
    if (value == null || String(value).trim() === '') continue
    args.push('--header', name + ': ' + String(value))
  }
  const marker = '\n__MSCC_PAHE_META__'
  args.push('--write-out', marker + '%{http_code}|%{url_effective}', String(url))

  let stdout
  try {
    const result = await execFileAsync('curl', args, {
      encoding:'buffer',
      timeout:timeoutMs + 5000,
      maxBuffer:64 * 1024 * 1024,
    })
    stdout = Buffer.from(result.stdout || [])
  } catch (error) {
    const detail = Buffer.isBuffer(error?.stderr) ? error.stderr.toString('utf8') : String(error?.stderr || error?.message || '')
    throw new Error('Tor request failed for ' + new URL(String(url)).hostname + (detail ? ': ' + detail.trim().slice(-300) : ''))
  }

  const markerBytes = Buffer.from(marker)
  const cut = stdout.lastIndexOf(markerBytes)
  if (cut < 0) throw new Error('Tor request metadata was missing.')
  const body = stdout.subarray(0, cut)
  const meta = stdout.subarray(cut + markerBytes.length).toString('utf8').trim()
  const pipe = meta.indexOf('|')
  const status = Number(pipe >= 0 ? meta.slice(0, pipe) : meta) || 0
  const finalUrl = pipe >= 0 ? meta.slice(pipe + 1) : String(url)
  return {
    status,
    finalUrl,
    bytes:body,
    text:binary ? '' : body.toString('utf8'),
  }
}

function requestHeaders(url, { referer = '', accept = 'text/html,application/xhtml+xml,application/json', state = browserState } = {}) {
  return {
    'user-agent':state?.userAgent || ANIME_UA,
    accept,
    referer:referer || new URL(String(url)).origin + '/',
    ...(state?.cookies ? { cookie:state.cookies } : {}),
  }
}

async function ensureStateForBase(base, query = PROBE_QUERY) {
  const normalized = String(base || BASES[0]).replace(/\/$/, '')
  if (browserState?.id && browserState.base === normalized) return browserState
  const refreshed = await refreshBrowserSession({ query, preferredBase:normalized })
  return refreshed.state
}

async function requestText(url, {
  referer = '',
  accept = 'text/html,application/xhtml+xml,application/json',
  retry = true,
} = {}) {
  const target = new URL(String(url))
  const targetBase = target.hostname.includes('animepahe') ? target.origin : (browserState?.base || BASES[0])
  let state = await ensureStateForBase(targetBase)
  let loaded
  try {
    loaded = await curlRequest(url, {
      headers:requestHeaders(url, { referer, accept, state }),
      proxy:state.torProxy,
    })
    if (loaded.status >= 200 && loaded.status < 400 && !looksBlocked(loaded.status, loaded.text)) {
      return { text:loaded.text, finalUrl:loaded.finalUrl, status:loaded.status, state }
    }
  } catch {}

  try {
    const browser = await flareGet(url, state)
    return { text:browser.text, finalUrl:browser.finalUrl, status:browser.status, state }
  } catch (error) {
    if (!retry) throw error
  }

  state = (await refreshBrowserSession({ query:PROBE_QUERY, preferredBase:targetBase })).state
  loaded = await curlRequest(url, {
    headers:requestHeaders(url, { referer, accept, state }),
    proxy:state.torProxy,
  })
  if (!(loaded.status >= 200 && loaded.status < 400) || looksBlocked(loaded.status, loaded.text)) {
    throw new Error('AnimePahe request remained blocked after browser refresh.')
  }
  return { text:loaded.text, finalUrl:loaded.finalUrl, status:loaded.status, state }
}

async function requestJson(url, options) {
  const loaded = await requestText(url, { ...options, accept:'application/json,text/plain,*/*' })
  try { return { data:parseFlareJsonBody(loaded.text), finalUrl:loaded.finalUrl, state:loaded.state } }
  catch {
    const error = new Error('AnimePahe returned HTML/non-JSON instead of API data.')
    error.bodyPreview = String(loaded.text || '').replace(/\s+/g, ' ').slice(0, 240)
    throw error
  }
}

function parseSearch(data, base) {
  const rows = Array.isArray(data?.data) ? data.data : []
  return rows.map(row => ({
    id:base + '/anime/' + String(row.session || row.id || ''),
    title:String(row.title || '').trim(),
    description:[row.type, row.year].filter(Boolean).join(' • '),
  })).filter(row => row.title && !row.id.endsWith('/anime/'))
}

function animeSession(item) {
  const raw = String(item?.id || item?.url || '')
  const fromUrl = /\/anime\/([^/?#]+)/.exec(raw)?.[1]
  return fromUrl || raw.split(':').at(-1) || ''
}

function animeBase(item) {
  const raw = String(item?.id || item?.url || '')
  try {
    const parsed = new URL(raw)
    if (parsed.hostname.includes('animepahe')) return parsed.origin
  } catch {}
  return browserState?.base || BASES[0]
}

function episodeId(anime, episode, number, base = '') {
  return 'pahe:' + encodeURIComponent(anime) + ':' + encodeURIComponent(episode) + ':' + encodeURIComponent(String(number || '')) + ':' + encodeURIComponent(String(base || ''))
}

function decodeEpisode(value) {
  const match = /^pahe:([^:]+):([^:]+):([^:]*)(?::(.*))?$/.exec(String(value || ''))
  if (!match) return null
  return {
    anime:decodeURIComponent(match[1]),
    episode:decodeURIComponent(match[2]),
    number:decodeURIComponent(match[3]),
    base:decodeURIComponent(match[4] || ''),
  }
}

async function getEpisodes(item) {
  const session = animeSession(item)
  if (!session) throw new Error('AnimePahe anime session is missing.')
  const base = animeBase(item)
  const releaseId = session
  const first = await requestJson(
    base + '/api?' + new URLSearchParams({ m:'release', id:releaseId, sort:'episode_asc', page:'1' }),
    { referer:base + '/anime/' + session },
  )
  const rows = Array.isArray(first.data?.data) ? [...first.data.data] : []
  const lastPage = Math.max(1, Number(first.data?.last_page || 1))

  for (let pageNo = 2; pageNo <= lastPage; pageNo += 1) {
    const pageResult = await requestJson(
      base + '/api?' + new URLSearchParams({ m:'release', id:releaseId, sort:'episode_asc', page:String(pageNo) }),
      { referer:base + '/anime/' + session },
    )
    if (Array.isArray(pageResult.data?.data)) rows.push(...pageResult.data.data)
  }

  const episodes = rows.map(row => ({
    id:episodeId(session, String(row.session || row.id || ''), row.episode, base),
    number:String(row.episode ?? ''),
    title:String(row.title || ('Episode ' + row.episode)),
    audio:String(row.audio || ''),
  })).filter(row => row.number && decodeEpisode(row.id)?.episode)

  return { title:item?.title || 'AnimePahe', episodes }
}

function parseSources(html) {
  const rows = []
  for (const match of String(html || '').matchAll(/<button\b([^>]*)>/gi)) {
    const data = attrs(match[1])
    const url = String(data['data-src'] || '').trim()
    if (!url) continue
    rows.push({
      url,
      quality:String(data['data-resolution'] || '').replace(/[^0-9]/g, ''),
      fansub:String(data['data-fansub'] || ''),
      audio:String(data['data-audio'] || ''),
    })
  }
  if (!rows.length) {
    for (const match of String(html || '').matchAll(/https:\/\/kwik\.(?:cx|gg|si|me|net|in|cc|link)\/e\/[A-Za-z0-9_-]+/gi)) {
      rows.push({ url:match[0], quality:'', fansub:'', audio:'' })
    }
  }
  return [...new Map(rows.map(row => [row.url, row])).values()]
}

function baseValue(token, radix) {
  const chars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
  let value = 0
  for (const ch of String(token)) {
    const digit = chars.indexOf(ch)
    if (digit < 0 || digit >= radix) return NaN
    value = value * radix + digit
  }
  return value
}

function unescapeJs(value) {
  return String(value)
    .replace(/\\x([0-9a-f]{2})/gi, (_, hex) => String.fromCharCode(Number.parseInt(hex, 16)))
    .replace(/\\u([0-9a-f]{4})/gi, (_, hex) => String.fromCharCode(Number.parseInt(hex, 16)))
    .replace(/\\n/g, '\n')
    .replace(/\\r/g, '\r')
    .replace(/\\t/g, '\t')
    .replace(/\\'/g, "'")
    .replace(/\\\\/g, '\\')
}

function unpackPacker(script) {
  const match = /}\(\s*'((?:\\.|[^'])*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'((?:\\.|[^'])*)'\.split\('\|'\)/s.exec(String(script || ''))
  if (!match) return ''
  const payload = unescapeJs(match[1])
  const radix = Number(match[2])
  const count = Number(match[3])
  const words = unescapeJs(match[4]).split('|')
  if (!radix || !count) return ''
  return payload.replace(/\b[0-9A-Za-z]+\b/g, token => {
    const index = baseValue(token, radix)
    return Number.isInteger(index) && index >= 0 && index < count && words[index] ? words[index] : token
  })
}

function mediaFromText(text) {
  const value = String(text || '').replace(/\\\//g, '/')
  const hls = /https?:\/\/[^"'<>\s\\]+\.m3u8(?:\?[^"'<>\s\\]*)?/i.exec(value)?.[0]
  if (hls) return hls
  return /https?:\/\/[^"'<>\s\\]+\.(?:mp4|mkv|webm)(?:\?[^"'<>\s\\]*)?/i.exec(value)?.[0] || ''
}

async function resolveKwik(url, { base, state }) {
  const proxy = state?.torProxy || TOR_PROXY
  let stdout
  try {
    const result = await execFileAsync(KWIK_PYTHON, [
      KWIK_HELPER,
      '--url',String(url),
      '--proxy',String(proxy),
      '--referer',String(base || BASES[0]) + '/',
      '--timeout',String(KWIK_TIMEOUT),
    ], {
      encoding:'utf8',
      timeout:(KWIK_TIMEOUT * 8 + 20) * 1000,
      maxBuffer:4 * 1024 * 1024,
    })
    stdout = String(result.stdout || '').trim()
  } catch (error) {
    const detail = String(error?.stderr || error?.message || '').trim().slice(-1000)
    const wrapped = new Error('AnimePahe/Kwik resolution failed' + (detail ? ': ' + detail : '.'))
    wrapped.code = error?.code === 'ENOENT' ? 'curl-cffi-helper-missing' : 'kwik-resolution-failed'
    throw wrapped
  }
  let data
  try { data = JSON.parse(stdout) }
  catch { throw new Error('AnimePahe/Kwik resolver returned invalid output.') }
  if (!data?.url) throw new Error('AnimePahe/Kwik stream could not be resolved.')
  return data
}

function pickSource(rows, quality, preferredAudio = 'jpn') {
  if (!rows.length) return null
  const wanted = Number(String(quality || '').replace(/[^0-9]/g, ''))
  const preferred = rows.filter(row => String(row.audio || '').toLowerCase() === preferredAudio)
  const pool = preferred.length ? preferred : rows
  const ranked = [...pool].sort((a,b) => Number(a.quality || 0) - Number(b.quality || 0))
  if (!wanted) return ranked.at(-1)
  return ranked.filter(row => Number(row.quality || 0) <= wanted).at(-1)
    || ranked.find(row => Number(row.quality || 0) >= wanted)
    || ranked.at(-1)
}

async function resolveMedia(episode, quality) {
  const decoded = decodeEpisode(episode?.id)
  if (!decoded) throw new Error('AnimePahe episode reference is invalid.')
  const base = decoded.base || browserState?.base || BASES[0]
  const page = await requestText(
    base + '/play/' + encodeURIComponent(decoded.anime) + '/' + encodeURIComponent(decoded.episode),
    { referer:base + '/anime/' + decoded.anime },
  )
  const sources = parseSources(page.text)
  const selected = pickSource(sources, quality, 'jpn')
  if (!selected?.url) throw new Error('AnimePahe returned no episode sources.')
  const state = page.state || browserState
  if (/^https?:\/\/kwik\./i.test(selected.url)) {
    const resolved = await resolveKwik(selected.url, { base, state })
    return {
      url:resolved.url,
      decoded,
      selected,
      headers:{
        'user-agent':String(resolved.userAgent || ANIME_UA),
        referer:String(resolved.referer || selected.url),
        ...(resolved.cookie ? { cookie:String(resolved.cookie) } : {}),
      },
      proxy:state?.torProxy || TOR_PROXY,
    }
  }
  return {
    url:selected.url,
    decoded,
    selected,
    headers:{ 'user-agent':state?.userAgent || ANIME_UA, referer:base + '/' },
    proxy:state?.torProxy || TOR_PROXY,
  }
}

async function torText(target, headers, proxy) {
  const loaded = await curlRequest(target, { headers, proxy, timeoutMs:60000 })
  if (!(loaded.status >= 200 && loaded.status < 400)) {
    throw new Error('AnimePahe media HTTP ' + loaded.status + ' for ' + new URL(String(target)).hostname)
  }
  return loaded.text
}

async function torBytes(target, headers, proxy) {
  const loaded = await curlRequest(target, { headers, proxy, binary:true, timeoutMs:120000 })
  if (!(loaded.status >= 200 && loaded.status < 400) && loaded.status !== 206) {
    throw new Error('AnimePahe media HTTP ' + loaded.status + ' for ' + new URL(String(target)).hostname)
  }
  return loaded.bytes
}

async function deliver(context, item, episode, quality, delivery) {
  const media = await resolveMedia(episode, quality)
  const { url, decoded, headers, proxy } = media
  const title = String(item?.title || 'AnimePahe') + ' - Episode ' + String(episode?.number || decoded.number || '')
  const path = (() => { try { return new URL(url).pathname.toLowerCase() } catch { return '' } })()

  if (path.includes('.m3u8') || url.includes('.m3u8')) {
    return deliverHls(context, {
      url,
      headers,
      title,
      quality,
      delivery,
      concurrency:Number(process.env.MSCC_ANIMEPAHE_HLS_CONCURRENCY || 8),
      fetchTextFn:(target, requestHeaders) => torText(target, requestHeaders, proxy),
      fetchBytesFn:(target, requestHeaders) => torBytes(target, requestHeaders, proxy),
    })
  }
  return deliverRemote(context, {
    url,
    headers,
    title,
    extension:/\.(mkv|webm|mp4)(?:$|\?)/i.exec(url)?.[1]?.toLowerCase() || 'mp4',
    mimetype:'video/mp4',
    delivery,
  })
}

export default {
  id:'animepahe',
  name:'AnimePahe',
  description:'AnimePahe search/releases with Tor-isolated browser clearance and Kwik/HLS resolution.',
  fallbackOrder:20,
  brandAliases:['Anime Pahe'],

  async run({ action, query, item, episode, episodeId:episodeRef, range, quality = 'source', delivery = 'document', context }) {
    if (action === 'search') {
      const q = String(query || '').trim()
      if (!q) return { items:[] }
      const { data, base } = await browserSearch(q)
      return { items:parseSearch(data, base) }
    }

    if (action === 'browse') {
      const search = await browserSearch(PROBE_QUERY)
      const base = search.base
      const { data } = await requestJson(base + '/api?' + new URLSearchParams({ m:'airing', page:'1' }), { referer:base + '/' })
      return { items:parseSearch(data, base) }
    }
    if (action === 'episodes') return getEpisodes(item)
    if (action === 'options') return { qualities:['source','1080','720','480','360'], deliveries:['document','video'] }

    if (action === 'download') {
      return deliver(context, item, episode || { id:episodeRef }, quality, delivery)
    }

    if (action === 'downloadRange') {
      const listing = await getEpisodes(item)
      const start = Number(range?.start?.number)
      const end = Number(range?.end?.number)
      const low = Math.min(start, end)
      const high = Math.max(start, end)
      const selected = listing.episodes.filter(row => {
        const number = Number(row.number)
        return Number.isFinite(number) && number >= low && number <= high
      })
      if (!selected.length) throw new Error('No AnimePahe episodes matched that range.')
      for (const row of selected) await deliver(context, item, row, quality, delivery)
      return { delivered:true, count:selected.length }
    }

    throw new Error('Unsupported AnimePahe action: ' + action)
  },

  _test:{
    parseSearch,
    parseSources,
    animeSession,
    episodeId,
    decodeEpisode,
    unpackPacker,
    mediaFromText,
    pickSource,
    looksBlocked,
    searchUrl,
  },
  _probe:{ getEpisodes, resolveMedia },
}
