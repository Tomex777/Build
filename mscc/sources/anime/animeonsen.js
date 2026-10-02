import {
  ANIME_UA,
  deliverHls,
  deliverRemote,
  deliverStreamWithFfmpeg,
  fetchJson,
} from './_delivery.js'

const BASE = 'https://www.animeonsen.xyz'
const API = 'https://api.animeonsen.xyz/v4'
const SEARCH = 'https://search.animeonsen.xyz'
const AUTH = 'https://auth.animeonsen.xyz/oauth/token'
const DEFAULT_CLIENT_ID = 'f296be26-28b5-4358-b5a1-6259575e23b7'
const DEFAULT_CLIENT_SECRET = '349038c4157d0480784753841217270c3c5b35f4281eaee029de21cb04084235'

let tokenCache = { value:'', expiresAt:0 }
let searchTokenCache = { value:'', expiresAt:0 }

async function accessToken() {
  if (tokenCache.value && tokenCache.expiresAt > Date.now() + 30000) return tokenCache.value

  const clientId = String(process.env.MSCC_ANIMEONSEN_CLIENT_ID || DEFAULT_CLIENT_ID)
  const clientSecret = String(process.env.MSCC_ANIMEONSEN_CLIENT_SECRET || DEFAULT_CLIENT_SECRET)
  const response = await fetch(AUTH, {
    method:'POST',
    headers:{
      'content-type':'application/x-www-form-urlencoded',
      'user-agent':ANIME_UA,
      accept:'application/json',
      origin:BASE,
      referer:BASE + '/',
    },
    body:new URLSearchParams({
      client_id:clientId,
      client_secret:clientSecret,
      grant_type:'client_credentials',
    }).toString(),
    signal:AbortSignal.timeout(25000),
  })
  const text = await response.text()
  if (!response.ok) throw new Error('AnimeOnsen auth HTTP ' + response.status)
  let data
  try { data = JSON.parse(text) } catch { throw new Error('AnimeOnsen auth returned invalid JSON') }
  const token = String(data?.access_token || '').trim()
  if (!token) throw new Error('AnimeOnsen auth returned no access token.')
  const expires = Math.max(60, Number(data?.expires_in || 3600))
  tokenCache = { value:token, expiresAt:Date.now() + expires * 1000 }
  return token
}

async function apiJson(path, { retry = true } = {}) {
  const token = await accessToken()
  try {
    return await fetchJson(API + path, {
      authorization:'Bearer ' + token,
      'user-agent':ANIME_UA,
      accept:'application/json, text/plain, */*',
      referer:BASE + '/',
      origin:BASE,
    }, 30000)
  } catch (error) {
    if (retry && error?.status === 401) {
      tokenCache = { value:'', expiresAt:0 }
      return apiJson(path, { retry:false })
    }
    throw error
  }
}

async function searchToken({ force = false } = {}) {
  if (!force && searchTokenCache.value && searchTokenCache.expiresAt > Date.now() + 30000) {
    return searchTokenCache.value
  }
  const { text } = await import('./_delivery.js').then(module =>
    module.fetchText(BASE + '/', {
      'user-agent':ANIME_UA,
      accept:'text/html,application/xhtml+xml',
      referer:BASE + '/',
    }, 30000)
  )
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
    headers:{
      'content-type':'application/json',
      accept:'application/json, text/plain, */*',
      authorization:'Bearer ' + token,
      'user-agent':ANIME_UA,
      origin:BASE,
      referer:BASE + '/',
    },
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
      const data = await searchJson(q)
      return { items:parseSearch(data) }
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

  _test:{ parseSearch, parseEpisodes, decodeEpisode, titleOf },
  _probe:{ resolveVideo },
}
