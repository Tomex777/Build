import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { extname, join, resolve, sep } from 'node:path'
import { load as loadHtml } from 'cheerio'
import WebTorrent from 'webtorrent'

const BASES = String(process.env.MSCC_NYAA_BASES || 'https://nyaa.si')
  .split(',')
  .map(value => value.trim().replace(/\/$/, ''))
  .filter(Boolean)
const CACHE_TTL_MS = Math.max(15_000, Number(process.env.MSCC_NYAA_CACHE_TTL_MS || 5 * 60_000))
const LIST_PAGES = Math.max(1, Math.min(6, Number(process.env.MSCC_NYAA_LIST_PAGES || 3)))
const SEARCH_TIMEOUT_MS = Math.max(5_000, Number(process.env.MSCC_NYAA_SEARCH_TIMEOUT_MS || 25_000))
const METADATA_TIMEOUT_MS = Math.max(10_000, Number(process.env.MSCC_NYAA_METADATA_TIMEOUT_MS || 90_000))
const DOWNLOAD_TIMEOUT_MS = Math.max(60_000, Number(process.env.MSCC_NYAA_DOWNLOAD_TIMEOUT_MS || 45 * 60_000))
const MAX_FILE_BYTES = Math.max(64, Number(process.env.MSCC_NYAA_MAX_FILE_MB || 650)) * 1024 * 1024
const VIDEO_EXTENSIONS = new Set(['.mkv','.mp4','.webm','.m4v'])
const cache = new Map()
let torrentQueue = Promise.resolve()

const compactGroups = new Set([
  'asw', 'judas', 'ember', 'trix', 'dkb', 'anime time', 'neohevc',
])
const subtitleGroups = new Set([
  'subsplease', 'erai-raws', 'judas', 'asw', 'ember', 'trix', 'yameii', 'dkb', 'anime time',
])

function state(prefix, value) {
  return prefix + Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
}

function unstate(prefix, value) {
  const raw = String(value || '')
  if (!raw.startsWith(prefix)) return null
  try {
    const parsed = JSON.parse(Buffer.from(raw.slice(prefix.length), 'base64url').toString('utf8'))
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch {
    return null
  }
}

function parseSize(value) {
  const match = /^\s*([\d.]+)\s*([KMGT]?i?B)\s*$/i.exec(String(value || ''))
  if (!match) return 0
  const number = Number(match[1])
  if (!Number.isFinite(number)) return 0
  const unit = match[2].toUpperCase()
  const factor = unit.startsWith('T') ? 1024 ** 4
    : unit.startsWith('G') ? 1024 ** 3
      : unit.startsWith('M') ? 1024 ** 2
        : unit.startsWith('K') ? 1024
          : 1
  return Math.round(number * factor)
}

function qualityOf(name) {
  const match = /\b(2160|1440|1080|720|576|540|480|360)p\b/i.exec(String(name || ''))
    || /\[(2160|1440|1080|720|576|540|480|360)\]/i.exec(String(name || ''))
  return match?.[1] || ''
}

function codecOf(name) {
  const value = String(name || '')
  if (/\bAV1\b/i.test(value)) return 'av1'
  if (/\b(?:HEVC|H[ ._-]?265|x265)\b/i.test(value)) return 'hevc'
  if (/\b(?:AVC|H[ ._-]?264|x264)\b/i.test(value)) return 'avc'
  if (/\bVP9\b/i.test(value)) return 'vp9'
  return ''
}

function releaseGroup(name) {
  return /^\s*\[([^\]]+)\]/.exec(String(name || ''))?.[1]?.trim() || ''
}

function subtitleHint(name) {
  const value = String(name || '')
  const group = releaseGroup(value).toLowerCase()
  return /\b(?:multi[ ._-]*subs?|eng(?:lish)?[ ._-]*subs?|subbed|softsubs?|closed[ ._-]*captions?|\bCC\b)\b/i.test(value)
    || subtitleGroups.has(group)
}

function audioHint(name) {
  const value = String(name || '')
  if (/\b(?:dual[ ._-]*audio|multi[ ._-]*audio)\b/i.test(value)) return 'multi'
  if (/\b(?:english[ ._-]*dub|eng[ ._-]*dub|dubbed)\b/i.test(value)) return 'english'
  if (/\b(?:jpn|japanese)[ ._-]*(?:audio)?\b/i.test(value)) return 'japanese'
  return ''
}

function numberText(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return ''
  return Number.isInteger(number) ? String(number) : String(number).replace(/0+$/, '').replace(/\.$/, '')
}

function expandRange(start, end) {
  const a = Number(start)
  const b = Number(end)
  if (!Number.isInteger(a) || !Number.isInteger(b) || a < 0 || b < a || b - a > 250) return []
  return Array.from({ length:b - a + 1 }, (_, index) => String(a + index))
}

function episodeNumbers(name) {
  const value = String(name || '')
  const found = new Set()

  for (const match of value.matchAll(/\bS\d{1,3}E(\d{1,4})(?:\s*[-~]\s*E?(\d{1,4}))?\b/gi)) {
    if (match[2]) for (const number of expandRange(match[1], match[2])) found.add(number)
    else found.add(numberText(match[1]))
  }

  for (const match of value.matchAll(/\b(?:Episode|Ep)\s*[ ._#-]*\s*(\d{1,4}(?:\.\d+)?)(?:\s*[-~]\s*(\d{1,4}))?\b/gi)) {
    if (match[2] && !String(match[1]).includes('.')) {
      for (const number of expandRange(match[1], match[2])) found.add(number)
    } else {
      found.add(numberText(match[1]))
    }
  }

  for (const match of value.matchAll(/(?:^|\s)-\s*(\d{1,4}(?:\.\d+)?)(?:v\d+)?(?=\s|\[|\(|$)/g)) {
    found.add(numberText(match[1]))
  }

  if (!found.size) {
    for (const match of value.matchAll(/\[(\d{1,4}(?:\.\d+)?)(?:v\d+)?\]/g)) {
      const number = Number(match[1])
      if (Number.isFinite(number) && ![360,480,540,576,720,1080,1440,2160].includes(number)) {
        found.add(numberText(match[1]))
      }
    }
  }

  return [...found].filter(Boolean).sort((a,b) => Number(a) - Number(b))
}

function seriesTitleFromRelease(name) {
  return String(name || '')
    .replace(/^\s*\[[^\]]+\]\s*/, '')
    .replace(/\bS\d{1,3}E\d{1,4}.*$/i, '')
    .replace(/\b(?:Episode|Ep)\s*[ ._#-]*\s*\d{1,4}.*$/i, '')
    .replace(/\s+-\s+\d{1,4}(?:\.\d+)?(?:v\d+)?(?:\s|\[|\(|$).*$/i, '')
    .replace(/\s*\[[^\]]+\]\s*$/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function normalizedTokens(value) {
  return String(value || '')
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
    .split(/\s+/)
    .filter(token => token.length >= 2 && !['the','and','season','part'].includes(token))
}

function titleMatchScore(name, query) {
  const wanted = normalizedTokens(query)
  if (!wanted.length) return 1
  const haystack = new Set(normalizedTokens(name))
  const hits = wanted.filter(token => haystack.has(token)).length
  return hits / wanted.length
}

function normalizeRelease(row) {
  const name = String(row?.name || '').trim()
  const group = releaseGroup(name)
  const numbers = episodeNumbers(name)
  const bytes = parseSize(row?.size)
  return {
    id:Number.isFinite(Number(row?.id)) ? String(row.id) : '',
    name,
    size:String(row?.size || ''),
    bytes,
    seeders:Number(row?.seeders || 0),
    leechers:Number(row?.leechers || 0),
    downloads:Number(row?.downloads || 0),
    date:row?.date || null,
    magnet:String(row?.magnet || ''),
    category:String(row?.category || ''),
    quality:qualityOf(name),
    codec:codecOf(name),
    group,
    episodes:numbers,
    subtitleHint:subtitleHint(name),
    audioHint:audioHint(name),
  }
}

function isEnglishAnime(release) {
  const category = String(release?.category || '').toLowerCase()
  return !category || category.includes('anime - english-translated') || category.includes('english-translated')
}

function perEpisodeBytes(release) {
  const count = Math.max(1, release?.episodes?.length || 1)
  return Number(release?.bytes || 0) / count
}

function compactScore(release) {
  const quality = String(release?.quality || '')
  const mb = perEpisodeBytes(release) / (1024 * 1024)
  if (!mb) return 0

  if (quality === '720') {
    if (mb >= 90 && mb <= 120) return 260
    if (mb >= 80 && mb <= 160) return 210 - Math.abs(mb - 105) * 0.8
    if (mb <= 300) return 120 - Math.abs(mb - 130) * 0.25
    if (mb <= 450) return 30
    return -100
  }

  if (quality === '1080') {
    if (mb >= 170 && mb <= 300) return 230
    if (mb >= 130 && mb <= 400) return 175 - Math.abs(mb - 220) * 0.25
    if (mb <= 550) return 70
    return -80
  }

  return mb <= 350 ? 40 : -20
}

function releaseScore(release, { query = '', episode = '', quality = '' } = {}) {
  const targetQuality = String(quality || '').replace(/[^0-9]/g, '')
  if (targetQuality && release.quality && release.quality !== targetQuality) return -Infinity
  if (episode && !release.episodes.includes(String(episode))) return -Infinity
  if (query && titleMatchScore(release.name, query) < 0.55) return -Infinity
  if (!release.magnet || !isEnglishAnime(release)) return -Infinity

  let score = 0
  if (targetQuality && release.quality === targetQuality) score += 600
  if (!targetQuality && release.quality === '720') score += 80
  score += compactScore(release)
  if (release.codec === 'av1') score += 95
  else if (release.codec === 'hevc') score += 80
  else if (release.codec === 'vp9') score += 45
  else if (release.codec === 'avc') score += 10

  const group = String(release.group || '').toLowerCase()
  if (compactGroups.has(group)) score += 55
  if (release.subtitleHint) score += 60
  if (release.seeders > 0) score += Math.min(120, Math.log2(release.seeders + 1) * 18)
  else score -= 180
  score += Math.min(40, Math.log10(release.downloads + 1) * 10)
  if ((release.episodes?.length || 0) > 1) score -= 25
  return score
}

function rankReleases(releases, options = {}) {
  return releases
    .map(release => ({ release, score:releaseScore(release, options) }))
    .filter(row => Number.isFinite(row.score))
    .sort((a,b) => b.score - a.score || b.release.seeders - a.release.seeders || a.release.bytes - b.release.bytes)
    .map(row => row.release)
}

function cacheKey(base, query, page, sort) {
  return [base, query.toLowerCase(), page, sort].join('|')
}

function nyaaSearchUrl(base, query, { page = 1, sort = 'date', rss = false } = {}) {
  const params = new URLSearchParams({
    q:String(query || ''),
    c:'1_0',
    f:'0',
  })
  if (rss) {
    params.set('page', 'rss')
  } else {
    params.set('p', String(Math.max(1, Number(page) || 1)))
    params.set('s', sort === 'date' ? 'id' : String(sort || 'id'))
    params.set('o', 'desc')
  }
  return base + '/?' + params
}

function parseNyaaHtml(html) {
  const $ = loadHtml(String(html || ''))
  const rows = []
  $('table tbody tr').each((_, element) => {
    const tr = $(element)
    const titleLink = tr.find('td:nth-child(2) a[href^="/view/"]').last()
    const href = String(titleLink.attr('href') || '')
    const id = /\/view\/(\d+)/.exec(href)?.[1] || ''
    const name = titleLink.text().trim() || String(titleLink.attr('title') || '').trim()
    const magnet = String(tr.find('a[href^="magnet:"]').first().attr('href') || '').trim()
    const size = tr.find('td:nth-child(4)').text().trim()
    const timestamp = Number(tr.find('td:nth-child(5)').attr('data-timestamp') || 0)
    const seeders = Number.parseInt(tr.find('td:nth-child(6)').text().trim(), 10)
    const leechers = Number.parseInt(tr.find('td:nth-child(7)').text().trim(), 10)
    const downloads = Number.parseInt(tr.find('td:nth-child(8)').text().trim(), 10)
    const category = String(tr.find('td:nth-child(1) a').first().attr('title') || '').trim()
    if (!name || !magnet) return
    rows.push({
      id,
      name,
      magnet,
      size,
      category,
      date:timestamp > 0 ? new Date(timestamp * 1000) : null,
      seeders:Number.isFinite(seeders) ? seeders : 0,
      leechers:Number.isFinite(leechers) ? leechers : 0,
      downloads:Number.isFinite(downloads) ? downloads : 0,
    })
  })
  return rows
}

function parseNyaaRss(xml) {
  const $ = loadHtml(String(xml || ''), { xmlMode:true })
  const rows = []
  $('item').each((_, element) => {
    const item = $(element)
    const name = item.find('title').text().trim()
    const hash = item.find('nyaa\\:infoHash').text().trim()
    const magnet = hash ? 'magnet:?xt=urn:btih:' + hash + '&dn=' + encodeURIComponent(name) : ''
    const guid = item.find('guid').text().trim()
    const id = /\/view\/(\d+)/.exec(guid)?.[1] || ''
    const seeders = Number.parseInt(item.find('nyaa\\:seeders').text().trim(), 10)
    const leechers = Number.parseInt(item.find('nyaa\\:leechers').text().trim(), 10)
    const downloads = Number.parseInt(item.find('nyaa\\:downloads').text().trim(), 10)
    if (!name || !magnet) return
    rows.push({
      id,
      name,
      magnet,
      size:item.find('nyaa\\:size').text().trim(),
      category:item.find('nyaa\\:category').text().trim(),
      date:item.find('pubDate').text().trim() || null,
      seeders:Number.isFinite(seeders) ? seeders : 0,
      leechers:Number.isFinite(leechers) ? leechers : 0,
      downloads:Number.isFinite(downloads) ? downloads : 0,
    })
  })
  return rows
}

async function fetchNyaaText(url, accept) {
  const response = await fetch(url, {
    headers:{
      'user-agent':'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36',
      accept,
    },
    redirect:'follow',
    signal:AbortSignal.timeout(SEARCH_TIMEOUT_MS),
  })
  const text = await response.text()
  if (!response.ok) throw new Error('Nyaa HTTP ' + response.status)
  if (/cloudflare|captcha|just a moment|verify you are human/i.test(text)) {
    throw new Error('Nyaa returned a browser verification page.')
  }
  return text
}

async function queryBase(base, query, { page = 1, sort = 'date' } = {}) {
  const key = cacheKey(base, query, page, sort)
  const cached = cache.get(key)
  if (cached && cached.expiresAt > Date.now()) return cached.rows

  let htmlError
  try {
    const html = await fetchNyaaText(
      nyaaSearchUrl(base, query, { page, sort }),
      'text/html,application/xhtml+xml',
    )
    const rows = parseNyaaHtml(html)
      .map(normalizeRelease)
      .filter(row => row.name && row.magnet && isEnglishAnime(row))
    if (rows.length || Number(page) > 1) {
      cache.set(key, { rows, expiresAt:Date.now() + CACHE_TTL_MS })
      return rows
    }
  } catch (error) {
    htmlError = error
  }

  if (Number(page) > 1) throw htmlError || new Error('Nyaa HTML search returned no page data.')

  try {
    const xml = await fetchNyaaText(
      nyaaSearchUrl(base, query, { rss:true }),
      'application/rss+xml,application/xml,text/xml;q=0.9,*/*;q=0.1',
    )
    const rows = parseNyaaRss(xml)
      .map(normalizeRelease)
      .filter(row => row.name && row.magnet && isEnglishAnime(row))
    cache.set(key, { rows, expiresAt:Date.now() + CACHE_TTL_MS })
    return rows
  } catch (rssError) {
    throw htmlError || rssError
  }
}

async function searchReleases(query, { pages = 1, sort = 'date' } = {}) {
  const clean = String(query || '').trim()
  if (!clean) return []
  let lastError
  for (const base of BASES) {
    try {
      const rows = []
      for (let page = 1; page <= pages; page += 1) {
        const pageRows = await queryBase(base, clean, { page, sort })
        rows.push(...pageRows)
        if (pageRows.length < 50) break
      }
      const unique = new Map()
      for (const row of rows) {
        const key = row.id || row.magnet
        if (key && !unique.has(key)) unique.set(key, row)
      }
      return [...unique.values()]
    } catch (error) {
      lastError = error
    }
  }
  throw lastError || new Error('Nyaa is unavailable.')
}

function encodeSeries({ query, title }) {
  return state('nyaa-series:', { query:String(query || '').trim(), title:String(title || query || '').trim() })
}

function decodeSeries(value) {
  return unstate('nyaa-series:', value)
}

function encodeEpisode({ query, title, number }) {
  return state('nyaa-episode:', { query:String(query || '').trim(), title:String(title || query || '').trim(), number:String(number || '') })
}

function decodeEpisode(value) {
  return unstate('nyaa-episode:', value)
}

function episodeFromInput(item, episode, episodeId) {
  return decodeEpisode(episodeId || episode?.id) || {
    ...decodeSeries(item?.id),
    number:String(episode?.number || ''),
  }
}

async function episodeCandidates(data, quality = '') {
  const number = String(data?.number || '')
  const query = String(data?.query || data?.title || '').trim()
  if (!query || !number) return []
  const padded = Number.isInteger(Number(number)) && Number(number) < 100 ? String(Number(number)).padStart(2, '0') : number
  const direct = await searchReleases(`${query} ${padded}`, { pages:2, sort:'seeders' })
  let matches = direct.filter(row => row.episodes.includes(number) && titleMatchScore(row.name, query) >= 0.55)
  if (!matches.length) {
    const broad = await searchReleases(query, { pages:LIST_PAGES, sort:'date' })
    matches = broad.filter(row => row.episodes.includes(number) && titleMatchScore(row.name, query) >= 0.55)
  }
  return rankReleases(matches, { query, episode:number, quality })
}

async function listEpisodes(item) {
  const data = decodeSeries(item?.id)
  const query = String(data?.query || item?.title || '').trim()
  if (!query) throw new Error('Nyaa title reference is invalid.')
  const releases = await searchReleases(query, { pages:LIST_PAGES, sort:'date' })
  const byNumber = new Map()
  for (const release of releases) {
    if (titleMatchScore(release.name, query) < 0.55) continue
    for (const number of release.episodes) {
      const bucket = byNumber.get(number) || []
      bucket.push(release)
      byNumber.set(number, bucket)
    }
  }
  const episodes = [...byNumber.entries()]
    .sort((a,b) => Number(a[0]) - Number(b[0]))
    .map(([number, rows]) => {
      const ranked = rankReleases(rows, { query, episode:number })
      const best = ranked[0]
      const compact = best?.size ? ` • ${best.size}` : ''
      return {
        id:encodeEpisode({ query, title:data?.title || item?.title || query, number }),
        number,
        title:`Episode ${number}${compact}`,
      }
    })
  if (!episodes.length) throw new Error('Nyaa returned no episode releases for that title.')
  return { title:data?.title || item?.title || query, episodes }
}

function availableQualities(releases) {
  const wanted = ['720','1080','480','360','1440','2160']
  const found = new Set(releases.map(row => row.quality).filter(Boolean))
  return wanted.filter(value => found.has(value))
}

function playableFiles(torrent) {
  return (torrent?.files || []).filter(file => VIDEO_EXTENSIONS.has(extname(file.name || '').toLowerCase()))
}

function fileEpisodeScore(file, episode) {
  const name = String(file?.name || file?.path || '')
  if (/\b(>:sample|preview|trailer|ncop|nced|creditless)\b/i.test(name)) return -500
  const numbers = episodeNumbers(name)
  let score = Number(file?.length || file?.size || 0) / (1024 * 1024)
  if (numbers.includes(String(episode))) score += 10_000
  else if (numbers.length) score -= 1_000
  return score
}

function pickVideoFile(torrent, episode) {
  return playableFiles(torrent)
    .sort((a,b) => fileEpisodeScore(b, episode) - fileEpisodeScore(a, episode))[0] || null
}

async function destroyClient(client) {
  if (!client) return
  await new Promise(resolve => {
    try { client.destroy(() => resolve()) }
    catch { resolve() }
  })
}

async function metadataFor(client, release, path) {
  return new Promise((resolve, reject) => {
    let done = false
    const finish = (fn, value) => {
      if (done) return
      done = true
      clearTimeout(timer)
      fn(value)
    }
    const timer = setTimeout(() => finish(reject, new Error('Nyaa torrent metadata timed out.')), METADATA_TIMEOUT_MS)
    let torrent
    try {
      torrent = client.add(release.magnet, { path, deselect:true }, ready => finish(resolve, ready))
      torrent.once('error', error => finish(reject, error))
    } catch (error) {
      finish(reject, error)
    }
  })
}

async function downloadSelectedFile(release, episode) {
  const root = await mkdtemp(join(tmpdir(), 'mscc-nyaa-'))
  const client = new WebTorrent({ maxConns:32 })
  try {
    const torrent = await metadataFor(client, release, root)
    const file = pickVideoFile(torrent, episode)
    if (!file) throw new Error('The selected Nyaa release contains no playable video file.')
    const bytes = Number(file.length || file.size || 0)
    if (bytes > MAX_FILE_BYTES) {
      const mb = Math.ceil(bytes / (1024 * 1024))
      throw new Error(`Selected Nyaa file is ${mb} MB, above the configured compact-file ceiling.`)
    }

    for (const entry of torrent.files || []) entry.deselect?.()
    file.select?.(10)

    await new Promise((resolve, reject) => {
      let finished = false
      const finish = (fn, value) => {
        if (finished) return
        finished = true
        clearTimeout(timer)
        fn(value)
      }
      const timer = setTimeout(() => finish(reject, new Error('Nyaa torrent download timed out.')), DOWNLOAD_TIMEOUT_MS)
      if (file.progress >= 1) return finish(resolve)
      file.once('done', () => finish(resolve))
      torrent.once('error', error => finish(reject, error))
    })

    const safeRoot = resolve(root)
    const outputPath = resolve(safeRoot, String(file.path || file.name || ''))
    if (!outputPath.startsWith(safeRoot + sep)) {
      throw new Error('Nyaa torrent file path escaped the temporary download directory.')
    }

    return {
      path:outputPath,
      root,
      file,
      torrent,
      client,
      cleanup:async () => {
        await destroyClient(client)
        await rm(root, { recursive:true, force:true }).catch(() => {})
      },
    }
  } catch (error) {
    await destroyClient(client)
    await rm(root, { recursive:true, force:true }).catch(() => {})
    throw error
  }
}

function mimeFor(name) {
  const extension = extname(String(name || '')).toLowerCase()
  if (extension === '.mkv') return 'video/x-matroska'
  if (extension === '.webm') return 'video/webm'
  if (extension === '.m4v' || extension === '.mp4') return 'video/mp4'
  return 'application/octet-stream'
}

async function withTorrentSlot(work) {
  const run = torrentQueue.then(work, work)
  torrentQueue = run.catch(() => {})
  return run
}

async function deliverEpisode({ data, quality, context }) {
  const candidates = await episodeCandidates(data, quality === 'source' ? '' : quality)
  if (!candidates.length) {
    throw new Error(`No healthy ${quality && quality !== 'source' ? quality + 'p ' : ''}Nyaa release matched Episode ${data.number}.`)
  }

  let lastError
  for (const release of candidates.slice(0, 5)) {
    try {
      return await withTorrentSlot(async () => {
        const downloaded = await downloadSelectedFile(release, data.number)
        try {
          await context.send({
            document:{ url:downloaded.path },
            mimetype:mimeFor(downloaded.file.name),
            fileName:downloaded.file.name,
            caption:data.title ? `${data.title} — Episode ${data.number}` : undefined,
          })
          return { delivered:true, release:release.name, size:release.size }
        } finally {
          await downloaded.cleanup()
        }
      })
    } catch (error) {
      lastError = error
    }
  }
  throw lastError || new Error('Nyaa releases were found, but none could be downloaded.')
}

export default {
  id:'nyaa',
  name:'Nyaa',
  description:'Compact anime releases selected by quality, size, subtitles and swarm health.',
  fallbackOrder:50,
  brandAliases:['Nyaa.si'],

  async run({ action, query, originalQuery, item, episode, episodeId, range, quality = 'source', context }) {
    if (action === 'search') {
      const clean = String(query || '').trim()
      if (!clean) return { items:[] }
      const releases = await searchReleases(clean, { pages:1, sort:'date' })
      const matching = releases.filter(row => titleMatchScore(row.name, clean) >= 0.55)
      const episodeCount = new Set(matching.flatMap(row => row.episodes)).size
      if (!matching.length || !episodeCount) return { items:[] }
      const title = String(originalQuery || clean).trim()
      return {
        items:[{
          id:encodeSeries({ query:clean, title }),
          title,
          description:`${episodeCount} episode${episodeCount === 1 ? '' : 's'} with matching releases`,
        }],
      }
    }

    if (action === 'browse') {
      const latest = await searchReleases('720p', { pages:1, sort:'date' }).catch(() => [])
      const seen = new Set()
      const items = []
      for (const release of latest) {
        const title = seriesTitleFromRelease(release.name)
        const key = title.toLowerCase()
        if (!title || title.length < 2 || seen.has(key)) continue
        seen.add(key)
        items.push({ id:encodeSeries({ query:title, title }), title })
        if (items.length >= 20) break
      }
      return { items }
    }

    if (action === 'episodes') return listEpisodes(item)

    if (action === 'options') {
      const data = episodeFromInput(item, episode, episodeId)
      if (!data?.number) return { qualities:['720','1080'], deliveries:['document'] }
      const releases = await episodeCandidates(data)
      const qualities = availableQualities(releases)
      return {
        qualities:qualities.length ? qualities : ['720','1080'],
        deliveries:['document'],
      }
    }

    if (action === 'download') {
      const data = episodeFromInput(item, episode, episodeId)
      if (!data?.query || !data?.number) throw new Error('Nyaa episode reference is invalid.')
      return deliverEpisode({ data, quality, context })
    }

    if (action === 'downloadRange') {
      const series = decodeSeries(item?.id) || { query:item?.title, title:item?.title }
      const start = Number(range?.start?.number)
      const end = Number(range?.end?.number)
      if (!Number.isFinite(start) || !Number.isFinite(end)) throw new Error('Invalid Nyaa episode range.')
      const low = Math.min(start, end)
      const high = Math.max(start, end)
      let count = 0
      for (let number = low; number <= high; number += 1) {
        await deliverEpisode({
          data:{ query:series.query, title:series.title || item?.title, number:String(number) },
          quality,
          context,
        })
        count += 1
      }
      return { delivered:true, count }
    }

    throw new Error('Unsupported Nyaa action: ' + action)
  },

  _test:{
    parseSize,
    qualityOf,
    codecOf,
    releaseGroup,
    subtitleHint,
    audioHint,
    episodeNumbers,
    seriesTitleFromRelease,
    titleMatchScore,
    normalizeRelease,
    nyaaSearchUrl,
    parseNyaaHtml,
    parseNyaaRss,
    compactScore,
    releaseScore,
    rankReleases,
    encodeSeries,
    decodeSeries,
    encodeEpisode,
    decodeEpisode,
    availableQualities,
    pickVideoFile,
  },
  _probe:{ searchReleases, episodeCandidates },
}
