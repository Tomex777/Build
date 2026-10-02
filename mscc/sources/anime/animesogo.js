import { createDecipheriv, createHmac } from 'node:crypto'
import {
  ANIME_UA,
  decodeHtml,
  deliverHls,
  deliverRemote,
  fetchJson,
  fetchText,
} from './_delivery.js'

const BASE = 'https://animesogo.to'
const MEGAPLAY_AES_KEY = 'i?LMTAx0Q6,:}50U'
const MEGAPLAY_AES_IV = "W0;27ToaUpl_P%'c"
const MEGAPLAY_TOKEN_SECRET = 'MpCdnT0k3n!9f2K#xQ7vL5mR8wN1pY4s'

function absolute(value, base = BASE + '/') {
  try { return new URL(String(value || ''), base).href } catch { return '' }
}

function attr(tag, name) {
  const match = new RegExp('\\b' + name + '\\s*=\\s*["\\\']([^"\\\']*)["\\\']', 'i').exec(String(tag || ''))
  return decodeHtml(match?.[1] || '')
}

function parseSearch(html) {
  const items = []
  const seen = new Set()
  const rx = /<a\b([^>]*)>([\s\S]*?)<\/a>/gi
  for (const match of String(html || '').matchAll(rx)) {
    const data = match[1]
    const href = attr(data, 'href')
    const classes = attr(data, 'class')
    if (!href || !/\/watch\//i.test(href)) continue
    if (classes && !/\bname\b|\bd-title\b/i.test(classes)) continue
    const title = decodeHtml(match[2])
    if (!title || title.length < 2) continue
    const url = absolute(href)
    const key = url.split('#')[0].split('?')[0]
    if (!key || seen.has(key)) continue
    seen.add(key)
    items.push({ id:key, title })
  }
  return items
}

function rc4(key, input) {
  const keyBytes = Buffer.from(String(key), 'utf8')
  const inputBytes = Buffer.from(String(input), 'utf8')
  const s = Array.from({ length:256 }, (_, i) => i)
  let j = 0
  for (let i = 0; i < 256; i += 1) {
    j = (j + s[i] + keyBytes[i % keyBytes.length]) & 255
    ;[s[i], s[j]] = [s[j], s[i]]
  }
  const out = Buffer.alloc(inputBytes.length)
  let i = 0
  j = 0
  for (let n = 0; n < inputBytes.length; n += 1) {
    i = (i + 1) & 255
    j = (j + s[i]) & 255
    ;[s[i], s[j]] = [s[j], s[i]]
    out[n] = inputBytes[n] ^ s[(s[i] + s[j]) & 255]
  }
  return out
}

function b64url(bytes) {
  return Buffer.from(bytes).toString('base64url')
}

function exchange(input, from, to) {
  return [...String(input)].map(ch => {
    const index = from.indexOf(ch)
    return index >= 0 ? to[index] : ch
  }).join('')
}

function vrfEncrypt(input) {
  let value = String(input)
  value = exchange(value, 'AP6GeR8H0lwUz1', 'UAz8Gwl10P6ReH')
  value = b64url(rc4('ItFKjuWokn4ZpB', value))
  value = b64url(rc4('fOyt97QWFB3', value))
  value = exchange(value, '1majSlPQd2M5', 'da1l2jSmP5QM')
  value = exchange(value, 'CPYvHj09Au3', '0jHA9CPYu3v')
  value = [...value].reverse().join('')
  value = b64url(rc4('736y1uTJpBLUX', value))
  value = b64url(Buffer.from(value, 'utf8'))
  return encodeURIComponent(value)
}

async function requestPage(url, headers = {}) {
  return fetchText(url, {
    'user-agent':ANIME_UA,
    referer:BASE + '/',
    ...headers,
  }, 30000)
}

async function ajaxResult(url, referer) {
  const { text } = await requestPage(url, {
    accept:'application/json, text/javascript, */*; q=0.01',
    'x-requested-with':'XMLHttpRequest',
    referer,
  })
  try {
    const data = JSON.parse(text)
    return typeof data?.result === 'string' ? data.result : data?.result || ''
  } catch {
    return text
  }
}

function slugOf(item) {
  const raw = String(item?.id || item?.url || '')
  return /\/watch\/([^/?#]+)/i.exec(raw)?.[1] || raw.replace(/^\/+|\/+$/g, '')
}

function parseAnimeId(html) {
  const watch = /<[^>]+id=["']watch-main["'][^>]*>/i.exec(String(html || ''))?.[0] || ''
  return attr(watch, 'data-id') || attr(watch, 'data-tip')
    || /\bdata-(?:id|tip)=["']([^"']+)["']/i.exec(String(html || ''))?.[1] || ''
}

function encodeEpisode(data) {
  return 'sogo:' + Buffer.from(JSON.stringify(data), 'utf8').toString('base64url')
}

function decodeEpisode(value) {
  const raw = String(value || '')
  if (!raw.startsWith('sogo:')) return null
  try {
    const data = JSON.parse(Buffer.from(raw.slice(5), 'base64url').toString('utf8'))
    return data && typeof data === 'object' ? data : null
  } catch {
    return null
  }
}

function parseEpisodes(html, slug) {
  const episodes = []
  for (const match of String(html || '').matchAll(/<a\b([^>]*)>([\s\S]*?)<\/a>/gi)) {
    const tag = match[1]
    const number = attr(tag, 'data-num')
    const serverIds = attr(tag, 'data-ids')
    if (!number || !serverIds) continue
    const titleText = decodeHtml(match[2])
    episodes.push({
      id:encodeEpisode({
        slug,
        number,
        serverIds,
        mal:attr(tag, 'data-mal'),
        timestamp:attr(tag, 'data-timestamp'),
      }),
      number,
      title:titleText && titleText !== number ? titleText : 'Episode ' + number,
    })
  }
  return episodes.sort((a,b) => Number(a.number) - Number(b.number))
}

async function listEpisodes(item) {
  const slug = slugOf(item)
  if (!slug) throw new Error('AnimeSogo title reference is invalid.')
  const episodeOne = BASE + '/watch/' + encodeURIComponent(slug) + '/ep-1'
  const { text } = await requestPage(episodeOne)
  const animeId = parseAnimeId(text)
  if (!animeId) throw new Error('AnimeSogo anime ID was not found.')

  let fragment = await ajaxResult(
    BASE + '/ajax/episode/list/' + encodeURIComponent(animeId),
    episodeOne,
  )
  let episodes = parseEpisodes(String(fragment || ''), slug)

  if (!episodes.length) {
    fragment = await ajaxResult(
      BASE + '/ajax/episode/list/' + encodeURIComponent(animeId) + '?vrf=' + vrfEncrypt(animeId),
      episodeOne,
    )
    episodes = parseEpisodes(String(fragment || ''), slug)
  }

  return { title:item?.title || slug, episodes }
}

function parseServerList(html) {
  const servers = []
  for (const match of String(html || '').matchAll(/<(?:a|li)\b([^>]*)>([\s\S]*?)<\/(?:a|li)>/gi)) {
    const id = attr(match[1], 'data-link-id')
    if (!id) continue
    const name = decodeHtml(match[2]) || 'Server'
    servers.push({ id, name })
  }
  const rank = name => {
    const value = String(name || '').toLowerCase().replace(/[^a-z0-9]/g, '')
    if (value.includes('hd1')) return 0
    if (value.includes('hd2')) return 1
    if (value.includes('hd3')) return 2
    if (value.includes('vidplay')) return 3
    if (value.includes('kiwi')) return 4
    return 10
  }
  return servers.sort((a,b) => rank(a.name) - rank(b.name))
}

async function embedUrl(linkId, episodeUrl) {
  const { text } = await requestPage(BASE + '/ajax/server?get=' + encodeURIComponent(linkId), {
    accept:'application/json, text/javascript, */*; q=0.01',
    'x-requested-with':'XMLHttpRequest',
    referer:episodeUrl,
  })
  try {
    const data = JSON.parse(text)
    return String(data?.result?.url || data?.result || '').trim()
  } catch {
    return ''
  }
}

function decodeMewcdn(url, html) {
  try {
    const fragment = new URL(url).hash.slice(1)
    if (!fragment) return ''
    let stream = Buffer.from(fragment, 'base64').toString('utf8').trim()
    const mapBlock = /var\s+HOST_MAP\s*=\s*\{([^}]+)\}/i.exec(String(html || ''))?.[1] || ''
    for (const match of mapBlock.matchAll(/'([^']+)'\s*:\s*'([^']+)'/g)) {
      if (stream.includes(match[1])) {
        stream = stream.replace(match[1], match[2])
        break
      }
    }
    return /^https?:\/\//i.test(stream) ? stream : ''
  } catch {
    return ''
  }
}

function decodeMegaPlay(enc, source) {
  let stream = ''
  let decrypted = false
  if (enc) {
    try {
      const key = Buffer.alloc(32)
      Buffer.from(MEGAPLAY_AES_KEY, 'utf8').copy(key)
      const iv = Buffer.from(MEGAPLAY_AES_IV, 'utf8')
      const encrypted = Buffer.from(String(enc).replace(/-/g, '+').replace(/_/g, '/'), 'base64')
      const decipher = createDecipheriv('aes-256-cbc', key, iv)
      const text = Buffer.concat([decipher.update(encrypted), decipher.final()]).toString('utf8')
      stream = /"file"\s*:\s*"([^"]+)"/i.exec(text)?.[1] || ''
      decrypted = Boolean(stream)
    } catch {}
  }

  if (!stream) {
    if (typeof source === 'string') stream = source
    else if (Array.isArray(source)) stream = String(source[0]?.file || source[0] || '')
    else if (source && typeof source === 'object') stream = String(source.file || '')
  }
  if (!stream) return ''

  if (!decrypted || /[?&]token=/i.test(stream)) return stream

  const path = /\/([a-f0-9]{32})\/([a-f0-9]{32})\//i.exec(stream)
  if (!path) return stream
  const pathKey = path[1].toLowerCase() + '/' + path[2].toLowerCase()
  const expiry = Math.floor(Date.now() / 1000) + 90
  const payload = expiry + '|' + pathKey
  const signature = createHmac('sha256', MEGAPLAY_TOKEN_SECRET).update(payload).digest('base64url')
  const token = Buffer.from(payload, 'utf8').toString('base64url') + '.' + signature
  const url = new URL(stream)
  url.searchParams.set('token', token)
  return url.href
}

async function resolveMegaPlay(embed) {
  const loaded = await requestPage(embed, { referer:BASE + '/' })
  const mediaId = /\bdata-id=["']([^"']+)["']/i.exec(loaded.text)?.[1]
    || /File\s+(\d+)/i.exec(loaded.text)?.[1]
  if (!mediaId) throw new Error('AnimeSogo MegaPlay media ID was not found.')

  const sourceUrl = new URL(embed)
  const getSources = new URL('/stream/getSources', sourceUrl.origin)
  getSources.searchParams.set('id', mediaId)
  if (sourceUrl.searchParams.get('s')) getSources.searchParams.set('s', sourceUrl.searchParams.get('s'))

  const { data } = await fetchJson(getSources.href, {
    'user-agent':ANIME_UA,
    referer:embed,
    origin:sourceUrl.origin,
    'x-requested-with':'XMLHttpRequest',
  }, 30000)
  const stream = decodeMegaPlay(data?.enc, data?.sources)
  if (!stream) throw new Error('AnimeSogo MegaPlay returned no stream.')
  return { stream, referer:sourceUrl.origin + '/' }
}

async function resolveStream(episode) {
  const decoded = decodeEpisode(episode?.id)
  if (!decoded?.serverIds) throw new Error('AnimeSogo episode reference is invalid.')
  const episodeUrl = BASE + '/watch/' + encodeURIComponent(decoded.slug) + '/ep-' + encodeURIComponent(decoded.number)
  const fragment = await ajaxResult(
    BASE + '/ajax/server/list?servers=' + encodeURIComponent(decoded.serverIds),
    episodeUrl,
  )
  const servers = parseServerList(String(fragment || ''))
  if (!servers.length) throw new Error('AnimeSogo returned no video servers.')

  let lastError
  for (const server of servers) {
    try {
      const embed = await embedUrl(server.id, episodeUrl)
      if (!embed) continue
      if (/\.m3u8(?:$|\?)/i.test(embed)) return { stream:embed, referer:BASE + '/', server:server.name }

      if (/mewcdn\.online\/player\/plyr\.php/i.test(embed)) {
        const loaded = await requestPage(embed, { referer:BASE + '/' })
        const stream = decodeMewcdn(embed, loaded.text)
        if (stream) return { stream, referer:'https://mewcdn.online/', server:server.name }
      }

      if (/megaplay\./i.test(embed) || /\/stream\//i.test(embed) || /hd-?\d/i.test(server.name)) {
        const resolved = await resolveMegaPlay(embed)
        return { ...resolved, server:server.name }
      }
    } catch (error) {
      lastError = error
    }
  }
  throw lastError || new Error('AnimeSogo could not resolve a playable server.')
}

async function deliver(context, item, episode, quality, delivery) {
  const { stream, referer } = await resolveStream(episode)
  const decoded = decodeEpisode(episode?.id)
  const title = String(item?.title || 'AnimeSogo') + ' - Episode ' + String(episode?.number || decoded?.number || '')
  const headers = { 'user-agent':ANIME_UA, referer, origin:new URL(referer).origin }
  if (/\.m3u8(?:$|\?)/i.test(stream)) {
    return deliverHls(context, { url:stream, headers, title, quality, delivery })
  }
  const extension = /\.(mp4|webm|mkv)(?:$|\?)/i.exec(stream)?.[1]?.toLowerCase()
  if (extension) {
    return deliverRemote(context, {
      url:stream,
      headers,
      title,
      extension,
      mimetype:extension === 'mkv' ? 'video/x-matroska' : extension === 'webm' ? 'video/webm' : 'video/mp4',
      delivery,
    })
  }
  throw new Error('AnimeSogo resolved an unsupported stream type.')
}

export default {
  id:'animesogo',
  name:'AnimeSogo',
  description:'AnimeSogo/Anikoto search, episode AJAX and multi-server stream resolution.',
  fallbackOrder:30,

  async run({ action, query, item, episode, episodeId, range, quality = 'source', delivery = 'document', context }) {
    if (action === 'search') {
      const q = String(query || '').trim()
      if (!q) return { items:[] }
      let loaded = await requestPage(BASE + '/filter?' + new URLSearchParams({ keyword:q }))
      let items = parseSearch(loaded.text)
      if (!items.length) {
        loaded = await requestPage(BASE + '/filter?keyword=' + encodeURIComponent(q) + '&vrf=' + vrfEncrypt(q))
        items = parseSearch(loaded.text)
      }
      return { items }
    }

    if (action === 'browse') {
      const { text } = await requestPage(BASE + '/most-viewed/')
      return { items:parseSearch(text) }
    }

    if (action === 'episodes') return listEpisodes(item)
    if (action === 'options') return { qualities:['source','1080','720','480','360'], deliveries:['document','video'] }

    if (action === 'download') {
      return deliver(context, item, episode || { id:episodeId }, quality, delivery)
    }

    if (action === 'downloadRange') {
      const listing = await listEpisodes(item)
      const start = Number(range?.start?.number)
      const end = Number(range?.end?.number)
      const low = Math.min(start, end)
      const high = Math.max(start, end)
      const selected = listing.episodes.filter(row => {
        const number = Number(row.number)
        return Number.isFinite(number) && number >= low && number <= high
      })
      if (!selected.length) throw new Error('No AnimeSogo episodes matched that range.')
      for (const row of selected) await deliver(context, item, row, quality, delivery)
      return { delivered:true, count:selected.length }
    }

    throw new Error('Unsupported AnimeSogo action: ' + action)
  },

  _test:{
    parseSearch,
    parseAnimeId,
    parseEpisodes,
    parseServerList,
    encodeEpisode,
    decodeEpisode,
    vrfEncrypt,
    decodeMegaPlay,
    decodeMewcdn,
  },
}
