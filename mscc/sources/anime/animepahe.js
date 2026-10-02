import {
  ANIME_UA,
  decodeHtml,
  deliverHls,
  deliverRemote,
} from './_delivery.js'

const BASES = String(process.env.MSCC_ANIMEPAHE_BASES || 'https://animepahe.com,https://animepahe.ng,https://animepahe.ch')
  .split(',')
  .map(value => value.trim().replace(/\/$/, ''))
  .filter(Boolean)
const FLARE_URL = String(
  process.env.MSCC_FLARESOLVERR_URL ||
  ('http://127.0.0.1:' + (process.env.MSCC_FLARE_PORT || '8191'))
).replace(/\/$/, '')
const FLARE_TIMEOUT = Number(process.env.MSCC_ANIMEPAHE_FLARE_TIMEOUT_MS || 60000)

function attrs(tag = '') {
  const out = {}
  for (const match of String(tag).matchAll(/([\w:-]+)\s*=\s*["']([^"']*)["']/g)) {
    out[match[1].toLowerCase()] = decodeHtml(match[2])
  }
  return out
}

function looksBlocked(status, text) {
  return status === 403 || status === 429 || /cloudflare|captcha|challenge|just a moment|verify you are human/i.test(String(text || ''))
}

async function flareGet(url) {
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
  if (data?.status !== 'ok') throw new Error('FlareSolverr could not open AnimePahe.')
  const solution = data?.solution || {}
  if (Number(solution.status || 0) >= 400) throw new Error('AnimePahe HTTP ' + solution.status)
  return {
    text:String(solution.response || ''),
    finalUrl:String(solution.url || url),
  }
}

async function requestText(url, { referer = '', accept = 'text/html,application/xhtml+xml,application/json' } = {}) {
  try {
    const response = await fetch(url, {
      headers:{
        'user-agent':ANIME_UA,
        accept,
        referer:referer || new URL(url).origin + '/',
        ...(process.env.MSCC_ANIMEPAHE_COOKIE ? { cookie:process.env.MSCC_ANIMEPAHE_COOKIE } : {}),
      },
      redirect:'follow',
      signal:AbortSignal.timeout(20000),
    })
    const text = await response.text()
    if (response.ok && !looksBlocked(response.status, text)) {
      return { text, finalUrl:response.url || url }
    }
    if (!looksBlocked(response.status, text)) throw new Error('AnimePahe HTTP ' + response.status)
  } catch (error) {
    if (error?.status && error.status !== 403 && error.status !== 429) throw error
  }
  return flareGet(url)
}

async function requestJson(url, options) {
  const { text, finalUrl } = await requestText(url, { ...options, accept:'application/json,text/plain,*/*' })
  try { return { data:JSON.parse(text), finalUrl } }
  catch { throw new Error('AnimePahe returned invalid JSON.') }
}

async function withBase(work) {
  let last
  for (const base of BASES) {
    try {
      return await work(base)
    } catch (error) {
      last = error
    }
  }
  throw last || new Error('AnimePahe is unavailable.')
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

function episodeId(anime, episode, number) {
  return 'pahe:' + encodeURIComponent(anime) + ':' + encodeURIComponent(episode) + ':' + encodeURIComponent(String(number || ''))
}

function decodeEpisode(value) {
  const match = /^pahe:([^:]+):([^:]+):(.*)$/.exec(String(value || ''))
  if (!match) return null
  return {
    anime:decodeURIComponent(match[1]),
    episode:decodeURIComponent(match[2]),
    number:decodeURIComponent(match[3]),
  }
}

async function getEpisodes(item) {
  const session = animeSession(item)
  if (!session) throw new Error('AnimePahe anime session is missing.')

  return withBase(async base => {
    const page = await requestText(base + '/anime/' + encodeURIComponent(session), { referer:base + '/' })
    const og = /<meta\b[^>]*property=["']og:url["'][^>]*content=["']([^"']+)["']/i.exec(page.text)
      || /<meta\b[^>]*content=["']([^"']+)["'][^>]*property=["']og:url["']/i.exec(page.text)
    const releaseId = String(og?.[1] || '').split('/').filter(Boolean).at(-1) || session

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
      id:episodeId(session, String(row.session || row.id || ''), row.episode),
      number:String(row.episode ?? ''),
      title:String(row.title || ('Episode ' + row.episode)),
    })).filter(row => row.number && decodeEpisode(row.id)?.episode)

    return { title:item?.title || 'AnimePahe', episodes }
  })
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
    for (const match of String(html || '').matchAll(/https:\/\/kwik\.(?:si|cx|link)\/e\/[A-Za-z0-9_-]+/gi)) {
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

async function resolveKwik(url) {
  const loaded = await requestText(url, { referer:url })
  let media = mediaFromText(loaded.text)
  if (media) return media

  for (const match of loaded.text.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)) {
    const unpacked = unpackPacker(match[1])
    media = mediaFromText(unpacked)
    if (media) return media
  }
  throw new Error('AnimePahe/Kwik stream could not be resolved.')
}

function pickSource(rows, quality) {
  if (!rows.length) return null
  const wanted = Number(String(quality || '').replace(/[^0-9]/g, ''))
  const ranked = [...rows].sort((a,b) => Number(a.quality || 0) - Number(b.quality || 0))
  if (!wanted) return ranked.at(-1)
  return ranked.filter(row => Number(row.quality || 0) <= wanted).at(-1)
    || ranked.find(row => Number(row.quality || 0) >= wanted)
    || ranked.at(-1)
}

async function resolveMedia(episode, quality) {
  const decoded = decodeEpisode(episode?.id)
  if (!decoded) throw new Error('AnimePahe episode reference is invalid.')

  return withBase(async base => {
    const page = await requestText(
      base + '/play/' + encodeURIComponent(decoded.anime) + '/' + encodeURIComponent(decoded.episode),
      { referer:base + '/anime/' + decoded.anime },
    )
    const sources = parseSources(page.text)
    const selected = pickSource(sources, quality)
    if (!selected?.url) throw new Error('AnimePahe returned no episode sources.')
    const url = /^https?:\/\/kwik\./i.test(selected.url) ? await resolveKwik(selected.url) : selected.url
    return { url, decoded, selected }
  })
}

async function deliver(context, item, episode, quality, delivery) {
  const { url, decoded } = await resolveMedia(episode, quality)
  const title = String(item?.title || 'AnimePahe') + ' - Episode ' + String(episode?.number || decoded.number || '')
  const path = (() => { try { return new URL(url).pathname.toLowerCase() } catch { return '' } })()
  const headers = { 'user-agent':ANIME_UA, referer:BASES[0] + '/' }

  if (path.includes('.m3u8') || url.includes('.m3u8')) {
    return deliverHls(context, { url, headers, title, quality, delivery })
  }
  const extension = /\.(mkv|webm|mp4)(?:$|\?)/i.exec(url)?.[1]?.toLowerCase() || 'mp4'
  return deliverRemote(context, {
    url,
    headers,
    title,
    extension,
    mimetype:extension === 'mkv' ? 'video/x-matroska' : extension === 'webm' ? 'video/webm' : 'video/mp4',
    delivery,
  })
}

export default {
  id:'animepahe',
  name:'AnimePahe',
  description:'AnimePahe search/releases with Cloudflare-aware episode source resolution.',
  fallbackOrder:20,
  brandAliases:['Anime Pahe'],

  async run({ action, query, item, episode, episodeId, range, quality = 'source', delivery = 'document', context }) {
    if (action === 'search') {
      const q = String(query || '').trim()
      if (!q) return { items:[] }
      return withBase(async base => {
        const { data } = await requestJson(base + '/api?' + new URLSearchParams({ m:'search', q }), { referer:base + '/' })
        return { items:parseSearch(data, base) }
      })
    }

    if (action === 'browse') return { items:[] }
    if (action === 'episodes') return getEpisodes(item)
    if (action === 'options') return { qualities:['source','1080','720','480','360'], deliveries:['document','video'] }

    if (action === 'download') {
      return deliver(context, item, episode || { id:episodeId }, quality, delivery)
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

  _test:{ parseSearch, parseSources, animeSession, episodeId, decodeEpisode, unpackPacker, mediaFromText, pickSource },
}
