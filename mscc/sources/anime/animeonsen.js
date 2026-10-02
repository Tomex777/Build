import { spawnSync } from 'node:child_process'
import {
  ANIME_UA,
  deliverHls,
  deliverRemote,
  deliverStreamWithFfmpeg,
  fetchJson,
  fetchText,
} from './_delivery.js'

const BASE = 'https://www.animeonsen.xyz'
const API = 'https://api.animeonsen.xyz/v4'
const SEARCH = 'https://search.animeonsen.xyz'
const AUTH = 'https://auth.animeonsen.xyz/oauth/token'
const DEFAULT_CLIENT_ID = 'f296be26-28b5-4358-b5a1-6259575e23b7'
const DEFAULT_CLIENT_SECRET = '349038c4157d0480784753841217270c3c5b35f4281eaee029de21cb04084235'
const AO_USER_AGENT = 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.3'

function aoHeaders(extra = {}) {
  return {
    'user-agent':AO_USER_AGENT,
    accept:'application/json, text/plain, */*',
    'accept-language':'en-US,en;q=0.9',
    referer:BASE + '/',
    origin:BASE,
    'sec-fetch-dest':'empty',
    'sec-fetch-mode':'cors',
    'sec-fetch-site':'same-site',
    ...extra,
  }
}

let tokenCache = { value:'', cookie:'', viaTor:false, expiresAt:0 }
let searchTokenCache = { value:'', expiresAt:0 }

const FLARE_URL = String(
  process.env.MSCC_FLARESOLVERR_URL ||
  ('http://127.0.0.1:' + (process.env.MSCC_FLARE_PORT || '8191'))
).replace(/\/$/, '')
const FLARE_TIMEOUT = Number(process.env.MSCC_ANIMEONSEN_FLARE_TIMEOUT_MS || 60000)
const TOR_PROXY = String(
  process.env.MSCC_TOR_PROXY ||
  ('socks5h://127.0.0.1:' + (process.env.MSCC_TOR_PORT || '9050'))
)

function curlConfigValue(value) {
  return JSON.stringify(String(value ?? ''))
}

function curlJsonViaTor(url, headers = {}) {
  const config = [
    'silent',
    'show-error',
    'location',
    'fail-with-body',
    'max-time = 35',
    'connect-timeout = 12',
    'proxy = ' + curlConfigValue(TOR_PROXY),
    'url = ' + curlConfigValue(url),
    ...Object.entries(headers)
      .filter(([, value]) => value != null && String(value).trim())
      .map(([key, value]) => 'header = ' + curlConfigValue(key + ': ' + String(value).trim())),
  ].join('\n') + '\n'

  const result = spawnSync('curl', ['--config', '-'], {
    input:config,
    encoding:'utf8',
    timeout:45000,
    maxBuffer:4 * 1024 * 1024,
  })
  if (result.status !== 0) {
    const error = new Error('AnimeOnsen Tor API request failed')
    error.cause = String(result.stderr || '').trim().slice(-500)
    throw error
  }
  const text = String(result.stdout || '')
  if (!text || text.trimStart().startsWith('<')) {
    throw new Error('AnimeOnsen Tor API returned HTML')
  }
  try { return JSON.parse(text) } catch { throw new Error('AnimeOnsen Tor API returned invalid JSON') }
}

function decodeSessionToken(cookieValue) {
  try {
    const decoded = decodeURIComponent(String(cookieValue || ''))
    const binary = Buffer.from(decoded, 'base64').toString('latin1')
    const token = [...binary]
      .map(character => String.fromCharCode(character.charCodeAt(0) + 1))
      .join('')
    return token.split('.').length === 3 ? token : ''
  } catch {
    return ''
  }
}

function sessionCookieFromSetCookies(values = []) {
  for (const value of values) {
    const match = /(?:^|[,;]\s*)ao\.session=([^;,]+)/i.exec(String(value || ''))
    if (match?.[1]) return match[1]
  }
  return ''
}

async function flareCommand(payload) {
  const response = await fetch(FLARE_URL + '/v1', {
    method:'POST',
    headers:{ 'content-type':'application/json' },
    body:JSON.stringify({ maxTimeout:FLARE_TIMEOUT, ...payload }),
    signal:AbortSignal.timeout(FLARE_TIMEOUT + 10000),
  })
  const text = await response.text()
  if (!response.ok) throw new Error('FlareSolverr HTTP ' + response.status)
  let data
  try { data = JSON.parse(text) } catch { throw new Error('Invalid FlareSolverr response') }
  if (data?.status !== 'ok') throw new Error('FlareSolverr request failed')
  return data
}

async function homeSessionToken({ browserOnly = false } = {}) {
  if (!browserOnly) {
    try {
      const response = await fetch(BASE + '/', {
        headers:{
          'user-agent':AO_USER_AGENT,
          accept:'text/html,application/xhtml+xml',
          'accept-language':'en-US,en;q=0.9',
        },
        redirect:'follow',
        signal:AbortSignal.timeout(25000),
      })
      const setCookies = typeof response.headers.getSetCookie === 'function'
        ? response.headers.getSetCookie()
        : [response.headers.get('set-cookie') || '']
      const cookie = sessionCookieFromSetCookies(setCookies)
      const token = decodeSessionToken(cookie)
      if (cookie && token) return { token, cookie, viaTor:false }
    } catch {}
  }

  try {
    const data = await flareCommand({ cmd:'request.get', url:BASE + '/' })
    const solution = data?.solution || {}
    const cookieRow = (Array.isArray(solution.cookies) ? solution.cookies : [])
      .find(row => String(row?.name || '').toLowerCase() === 'ao.session')
    const cookie = String(cookieRow?.value || '')
    const token = decodeSessionToken(cookie)
    if (cookie && token) return { token, cookie, viaTor:true }
  } catch {}

  return null
}

async function flarePostForm(url, postData) {
  let session = ''
  try {
    const created = await flareCommand({ cmd:'sessions.create' })
    session = String(created?.session || '').trim()

    if (session) {
      await flareCommand({
        cmd:'request.get',
        url:BASE + '/',
        session,
      }).catch(() => {})
    }

    const data = await flareCommand({
      cmd:'request.post',
      url,
      postData,
      ...(session ? { session } : {}),
    })
    const solution = data?.solution || {}
    const status = Number(solution.status || 0)
    if (status && status >= 400) {
      const error = new Error('AnimeOnsen browser-backed auth HTTP ' + status)
      error.status = status
      throw error
    }
    const body = String(solution.response || '')
    if (!body || body.trimStart().startsWith('<')) {
      const error = new Error('AnimeOnsen browser-backed auth returned HTML')
      error.status = status || 0
      throw error
    }
    return body
  } finally {
    if (session) {
      await flareCommand({ cmd:'sessions.destroy', session }).catch(() => {})
    }
  }
}

async function accessToken() {
  if (tokenCache.value && tokenCache.expiresAt > Date.now() + 30000) return tokenCache.value

  const siteSession = await homeSessionToken()
  if (siteSession?.token) {
    tokenCache = {
      value:siteSession.token,
      cookie:siteSession.cookie,
      viaTor:siteSession.viaTor === true,
      expiresAt:Date.now() + 25 * 60_000,
    }
    return siteSession.token
  }

  const clientId = String(process.env.MSCC_ANIMEONSEN_CLIENT_ID || DEFAULT_CLIENT_ID)
  const clientSecret = String(process.env.MSCC_ANIMEONSEN_CLIENT_SECRET || DEFAULT_CLIENT_SECRET)
  const postData = new URLSearchParams({
    client_id:clientId,
    client_secret:clientSecret,
    grant_type:'client_credentials',
  }).toString()

  let text = ''
  let status = 0
  let authViaTor = false
  try {
    const response = await fetch(AUTH, {
      method:'POST',
      headers:aoHeaders({
        'content-type':'application/x-www-form-urlencoded',
        accept:'application/json',
      }),
      body:postData,
      signal:AbortSignal.timeout(25000),
    })
    status = response.status
    text = await response.text()
  } catch {}

  if (![200,201].includes(status)) {
    if (![0,403,429,503].includes(status)) {
      const error = new Error('AnimeOnsen auth HTTP ' + status)
      error.status = status
      throw error
    }
    try {
      text = await flarePostForm(AUTH, postData)
      status = 200
      authViaTor = true
    } catch (flareError) {
      const error = new Error('AnimeOnsen auth HTTP ' + (status || flareError?.status || 0))
      error.status = status || Number(flareError?.status || 0)
      throw error
    }
  }

  let data
  try { data = JSON.parse(text) } catch { throw new Error('AnimeOnsen auth returned invalid JSON') }
  const token = String(data?.access_token || '').trim()
  if (!token) throw new Error('AnimeOnsen auth returned no access token.')
  const expires = Math.max(60, Number(data?.expires_in || 3600))
  tokenCache = { value:token, cookie:'', viaTor:authViaTor, expiresAt:Date.now() + expires * 1000 }
  return token
}

async function apiJson(path, { retry = true } = {}) {
  const token = await accessToken()
  const url = API + path
  const headers = aoHeaders({
    authorization:'Bearer ' + token,
    ...(tokenCache.cookie ? { cookie:'ao.session=' + tokenCache.cookie } : {}),
  })

  if (tokenCache.viaTor) {
    try {
      return { data:curlJsonViaTor(url, headers), response:null }
    } catch {}
  }

  try {
    return await fetchJson(url, headers, 30000)
  } catch (directError) {
    if (retry) {
      const browserSession = await homeSessionToken({ browserOnly:true })
      if (browserSession?.token) {
        tokenCache = {
          value:browserSession.token,
          cookie:browserSession.cookie,
          viaTor:true,
          expiresAt:Date.now() + 25 * 60_000,
        }
        const torHeaders = aoHeaders({
          authorization:'Bearer ' + browserSession.token,
          cookie:'ao.session=' + browserSession.cookie,
        })
        try {
          return { data:curlJsonViaTor(url, torHeaders), response:null }
        } catch {}
      }

      if ([401,403].includes(Number(directError?.status || 0))) {
        tokenCache = { value:'', cookie:'', viaTor:false, expiresAt:0 }
        return apiJson(path, { retry:false })
      }
    }
    throw directError
  }
}

async function searchToken({ force = false } = {}) {
  if (!force && searchTokenCache.value && searchTokenCache.expiresAt > Date.now() + 30000) {
    return searchTokenCache.value
  }
  const { text } = await fetchText(BASE + '/', {
    'user-agent':AO_USER_AGENT,
    accept:'text/html,application/xhtml+xml',
    'accept-language':'en-US,en;q=0.9',
    referer:BASE + '/',
  }, 30000)
  const token = /<meta\b[^>]*name=["']ao-search-token["'][^>]*content=["']([^"']+)["']/i.exec(text)?.[1]
    || /<meta\b[^>]*content=["']([^"']+)["'][^>]*name=["']ao-search-token["']/i.exec(text)?.[1]
    || ''
  if (!token) throw new Error('AnimeOnsen search token is unavailable.')
  searchTokenCache = { value:token, expiresAt:Date.now() + 30 * 60_000 }
  return token
}

async function searchJson(query, { retry = true } = {}) {
  const token = await searchToken()
  const response = await fetch(SEARCH + '/indexes/content/search', {
    method:'POST',
    headers:aoHeaders({
      'content-type':'application/json',
      authorization:'Bearer ' + token,
    }),
    body:JSON.stringify({ q:String(query || '').trim() }),
    signal:AbortSignal.timeout(30000),
  })
  const text = await response.text()
  if ((response.status === 401 || response.status === 403) && retry) {
    searchTokenCache = { value:'', expiresAt:0 }
    await searchToken({ force:true })
    return searchJson(query, { retry:false })
  }
  if (!response.ok) {
    const error = new Error('AnimeOnsen search HTTP ' + response.status)
    error.status = response.status
    throw error
  }
  try { return JSON.parse(text) } catch { throw new Error('AnimeOnsen search returned invalid JSON.') }
}

function searchTerms(query) {
  return String(query || '')
    .toLowerCase()
    .split(/[^a-z0-9]+/)
    .filter(token => token.length >= 2)
}

async function catalogSearch(query) {
  const terms = searchTerms(query)
  if (!terms.length) return []
  const matches = []
  const seen = new Set()
  for (let start = 0; start < 600 && matches.length < 25; start += 50) {
    const { data } = await apiJson('/content/index?' + new URLSearchParams({
      start:String(start),
      limit:'50',
    }))
    const rows = Array.isArray(data?.content) ? data.content : []
    if (!rows.length) break
    for (const row of rows) {
      const haystack = [
        row?.content_title_en,
        row?.content_title,
        row?.content_title_jp,
      ].filter(Boolean).join(' ').toLowerCase()
      if (!terms.every(term => haystack.includes(term))) continue
      const id = idOf(row)
      if (!id || seen.has(id)) continue
      seen.add(id)
      matches.push(row)
      if (matches.length >= 25) break
    }
    if (rows.length < 50) break
  }
  return matches
}

function titleOf(row = {}) {
  return String(row.content_title_en || row.content_title || row.content_title_jp || row.title || '').trim()
}

function idOf(row = {}) {
  return String(row.content_id || row.id || '').trim()
}

function parseSearch(data) {
  const rows = Array.isArray(data?.hits) ? data.hits
    : Array.isArray(data?.result) ? data.result
      : Array.isArray(data?.content) ? data.content
        : Array.isArray(data) ? data : []
  return rows.map(row => ({
    id:idOf(row),
    title:titleOf(row),
    description:[
      row?.mal_data?.type || row?.type || '',
      row?.mal_data?.status || row?.status || '',
    ].filter(Boolean).join(' • '),
  })).filter(row => row.id && row.title)
}

function parseEpisodes(data, contentId) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return []
  return Object.entries(data).map(([number, meta]) => {
    const title = String(
      meta?.contentTitle_episode_en ||
      meta?.content_title_episode_en ||
      meta?.title ||
      ''
    ).trim()
    return {
      id:'ao:' + encodeURIComponent(contentId) + ':' + encodeURIComponent(number),
      number:String(number),
      title:title ? 'Episode ' + number + ': ' + title : 'Episode ' + number,
    }
  }).sort((a,b) => Number(a.number) - Number(b.number))
}

function decodeEpisode(id) {
  const match = /^ao:([^:]+):(.+)$/.exec(String(id || ''))
  if (!match) return null
  return {
    contentId:decodeURIComponent(match[1]),
    number:decodeURIComponent(match[2]),
  }
}

function sourceTitle(item, episode) {
  const base = String(item?.title || 'AnimeOnsen')
  const number = String(episode?.number || decodeEpisode(episode?.id)?.number || '')
  return number ? base + ' - Episode ' + number : base
}

async function resolveVideo(episode) {
  const decoded = decodeEpisode(episode?.id)
  if (!decoded) throw new Error('AnimeOnsen episode reference is invalid.')
  const { data } = await apiJson(
    '/content/' + encodeURIComponent(decoded.contentId) +
    '/video/' + encodeURIComponent(decoded.number)
  )
  const stream = String(data?.uri?.stream || '').trim()
  if (!stream) throw new Error('AnimeOnsen returned no playable stream.')
  return { stream, data, decoded }
}

async function deliver(context, item, episode, quality, delivery) {
  const { stream } = await resolveVideo(episode)
  const headers = {
    'user-agent':ANIME_UA,
    referer:BASE + '/',
    origin:BASE,
  }
  const title = sourceTitle(item, episode)
  const pathname = (() => {
    try { return new URL(stream).pathname.toLowerCase() } catch { return '' }
  })()

  if (pathname.endsWith('.m3u8') || stream.includes('.m3u8')) {
    return deliverHls(context, { url:stream, headers, title, quality, delivery })
  }
  if (pathname.endsWith('.mp4') || pathname.endsWith('.webm') || pathname.endsWith('.mkv')) {
    const extension = pathname.split('.').pop() || 'mp4'
    return deliverRemote(context, {
      url:stream,
      headers,
      title,
      extension,
      mimetype:extension === 'webm' ? 'video/webm' : extension === 'mkv' ? 'video/x-matroska' : 'video/mp4',
      delivery,
    })
  }
  return deliverStreamWithFfmpeg(context, { url:stream, headers, title, delivery })
}

export default {
  id:'animeonsen',
  name:'AnimeOnsen',
  description:'AnimeOnsen OAuth API for search, episodes, subtitles and stream delivery.',
  fallbackOrder:40,

  async run({ action, query, item, episode, episodeId, range, quality = 'source', delivery = 'document', context }) {
    if (action === 'search') {
      const q = String(query || '').trim()
      if (!q) return { items:[] }
      try {
        const data = await searchJson(q)
        const items = parseSearch(data)
        if (items.length) return { items }
      } catch (error) {
        if (![401,403,404,429].includes(Number(error?.status || 0))) throw error
      }
      const rows = await catalogSearch(q)
      return { items:parseSearch(rows) }
    }

    if (action === 'browse') {
      const { data } = await apiJson('/content/index?start=0&limit=20')
      return { items:parseSearch(data) }
    }

    if (action === 'episodes') {
      const contentId = String(item?.id || '').trim()
      if (!contentId) throw new Error('AnimeOnsen content ID is missing.')
      const { data } = await apiJson('/content/' + encodeURIComponent(contentId) + '/episodes')
      return { title:item?.title || 'AnimeOnsen', episodes:parseEpisodes(data, contentId) }
    }

    if (action === 'options') {
      return { qualities:['source','720'], deliveries:['document','video'] }
    }

    if (action === 'download') {
      const chosen = episode || { id:episodeId }
      return deliver(context, item, chosen, quality, delivery)
    }

    if (action === 'downloadRange') {
      const contentId = String(item?.id || '').trim()
      const { data } = await apiJson('/content/' + encodeURIComponent(contentId) + '/episodes')
      const episodes = parseEpisodes(data, contentId)
      const start = Number(range?.start?.number)
      const end = Number(range?.end?.number)
      const low = Math.min(start, end)
      const high = Math.max(start, end)
      const selected = episodes.filter(row => {
        const number = Number(row.number)
        return Number.isFinite(number) && number >= low && number <= high
      })
      if (!selected.length) throw new Error('No AnimeOnsen episodes matched that range.')
      for (const row of selected) await deliver(context, item, row, quality, delivery)
      return { delivered:true, count:selected.length }
    }

    throw new Error('Unsupported AnimeOnsen action: ' + action)
  },

  _test:{ parseSearch, parseEpisodes, decodeEpisode, titleOf, searchTerms, decodeSessionToken },
  _probe:{ resolveVideo },
}
