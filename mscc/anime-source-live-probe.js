import { spawnSync } from 'node:child_process'
import animePahe from './sources/anime/animepahe.js'
import animeSogo from './sources/anime/animesogo.js'
import animeOnsen from './sources/anime/animeonsen.js'

const SOURCES = {
  animepahe:animePahe,
  animesogo:animeSogo,
  animeonsen:animeOnsen,
}
const id = String(process.argv[2] || '').trim().toLowerCase()
const source = SOURCES[id]
if (!source) throw new Error('Usage: node anime-source-live-probe.js <animepahe|animesogo|animeonsen>')
const query = process.env.MSCC_LIVE_QUERY || 'Bleach'
const UA = 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36'

function must(value, message) {
  if (!value) throw new Error(message)
}

function pick(items) {
  return items.find(item => /bleach/i.test(String(item?.title || ''))) || items[0]
}

function ffHeaders(headers = {}) {
  const ua = headers['user-agent'] || headers['User-Agent'] || UA
  const rest = Object.entries(headers)
    .filter(([key, value]) => key.toLowerCase() !== 'user-agent' && value != null && String(value).trim())
    .map(([key, value]) => `${key}: ${String(value).trim()}`)
    .join('\r\n')
  const args = ['-user_agent', String(ua)]
  if (rest) args.push('-headers', rest + '\r\n')
  return args
}

function run(command, args, timeout = 90000) {
  const result = spawnSync(command, args, {
    encoding:'utf8',
    timeout,
    maxBuffer:8 * 1024 * 1024,
  })
  return {
    ok:result.status === 0,
    status:result.status,
    signal:result.signal || '',
    stdout:String(result.stdout || '').trim(),
    stderr:String(result.stderr || '').trim().slice(-6000),
  }
}

async function verifyDeliveredMedia(payload) {
  const media = payload?.document || payload?.video
  const target = typeof media === 'string' ? media : media?.url
  must(target, `${source.name} runtime delivery returned no media target`)
  const headers = typeof media === 'object' && media?.headers ? media.headers : {}

  const probe = run('ffprobe', [
    '-v','error',
    ...ffHeaders(headers),
    '-show_entries','format=format_name,duration,bit_rate:stream=index,codec_type,codec_name,width,height',
    '-of','json',
    target,
  ], 120000)
  must(probe.ok, `${source.name} runtime ffprobe failed: ${probe.stderr}`)

  let json = {}
  try { json = JSON.parse(probe.stdout) } catch {}
  must(
    Array.isArray(json?.streams) && json.streams.some(row => row.codec_type === 'video'),
    `${source.name} runtime delivery returned no video stream`,
  )

  const decode = run('ffmpeg', [
    '-v','error',
    ...ffHeaders(headers),
    '-i',target,
    '-t','5',
    '-map','0:v:0',
    '-f','null','-',
  ], 120000)
  must(decode.ok, `${source.name} runtime delivery decode failed: ${decode.stderr}`)

  return {
    targetKind:/^https?:\/\//i.test(String(target)) ? 'remote' : 'local',
    format:json.format || {},
    streams:json.streams || [],
    decodeSeconds:5,
  }
}

async function resolve(episode) {
  if (id === 'animepahe') {
    const media = await source._probe.resolveMedia(episode, 'source')
    return {
      url:media.url,
      headers:{ 'user-agent':UA, referer:'https://animepahe.pw/' },
    }
  }
  if (id === 'animesogo') {
    const media = await source._probe.resolveStream(episode)
    return {
      url:media.stream,
      headers:{
        'user-agent':UA,
        referer:media.referer || 'https://animesogo.to/',
        origin:new URL(media.referer || 'https://animesogo.to/').origin,
      },
    }
  }
  if (id === 'animeonsen') {
    const media = await source._probe.resolveVideo(episode)
    return {
      url:media.stream,
      headers:{
        'user-agent':UA,
        referer:'https://www.animeonsen.xyz/',
        origin:'https://www.animeonsen.xyz',
      },
    }
  }
  throw new Error('No probe resolver for ' + id)
}

const began = performance.now()
const search = await source.run({ action:'search', query, context:{} })
must(Array.isArray(search?.items) && search.items.length, `${source.name} search returned no results`)
const anime = pick(search.items)

const listing = await source.run({ action:'episodes', item:anime, context:{} })
must(Array.isArray(listing?.episodes) && listing.episodes.length, `${source.name} episode listing returned no episodes`)
const episode = listing.episodes.find(row => Number(row?.number) === 1) || listing.episodes[0]

const stream = await resolve(episode)
must(stream?.url && /^https?:\/\//i.test(stream.url), `${source.name} returned no stream URL`)

const probe = run('ffprobe', [
  '-v','error',
  ...ffHeaders(stream.headers || {}),
  '-show_entries','format=format_name,duration,bit_rate:stream=index,codec_type,codec_name,width,height',
  '-of','json',
  stream.url,
], 120000)
must(probe.ok, `${source.name} ffprobe failed: ${probe.stderr}`)

let probeJson = {}
try { probeJson = JSON.parse(probe.stdout) } catch {}
must(
  Array.isArray(probeJson?.streams) && probeJson.streams.some(row => row.codec_type === 'video'),
  `${source.name} returned no video stream`,
)

let runtimeProof = null
const delivered = await source.run({
  action:'download',
  item:anime,
  episode,
  episodeId:episode.id,
  quality:'source',
  delivery:'document',
  context:{
    send:async payload => {
      runtimeProof = await verifyDeliveredMedia(payload)
    },
  },
})
must(delivered?.delivered === true, `${source.name} runtime delivery did not complete`)
must(runtimeProof, `${source.name} runtime delivery was not inspected`)

console.log(JSON.stringify({
  source:source.name,
  query,
  resultCount:search.items.length,
  selectedAnime:anime.title,
  episodeCount:listing.episodes.length,
  selectedEpisode:episode.title,
  streamHost:new URL(stream.url).hostname,
  streamPath:new URL(stream.url).pathname,
  elapsedMs:Math.round(performance.now() - began),
  ffprobe:{
    format:probeJson.format || {},
    streams:probeJson.streams || [],
  },
  runtimeDelivery:runtimeProof,
  verdict:'PASS',
}, null, 2))
