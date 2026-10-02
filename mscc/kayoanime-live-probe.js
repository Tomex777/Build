import { spawnSync } from 'node:child_process'
import source from './sources/anime/kayoanime.js'

const QUERY = process.env.MSCC_LIVE_QUERY || 'Re:ZERO'
const UA = 'Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36'
const SAMPLE_BYTES = 4 * 1024 * 1024

function must(condition, message) {
  if (!condition) throw new Error(message)
}

function pickAnime(items) {
  return items.find(item => /re\s*:?\s*zero|rezero/i.test(String(item?.title || '')))
    || items[0]
}

function pickEpisode(episodes) {
  return episodes.find(ep => /\.(mkv|mp4|webm|m4v)$/i.test(String(ep?.title || '')))
    || episodes[0]
}

async function rangeProbe(url, start = 0, size = SAMPLE_BYTES, headers = {}) {
  const end = start + size - 1
  const began = performance.now()
  const response = await fetch(url, {
    headers:{
      'user-agent':UA,
      range:`bytes=${start}-${end}`,
      accept:'*/*',
      ...headers,
    },
    redirect:'follow',
    signal:AbortSignal.timeout(45000),
  })
  const headersAt = performance.now()
  must(response.ok, `media probe HTTP ${response.status}`)

  let bytes = 0
  const first = []
  const reader = response.body?.getReader()
  must(reader, 'media probe had no response body')
  while (bytes < size) {
    const { done, value } = await reader.read()
    if (done) break
    if (first.length < 16) {
      for (const b of value.slice(0, 16 - first.length)) first.push(b)
    }
    bytes += value.byteLength
  }
  await reader.cancel().catch(() => {})
  const ended = performance.now()
  const seconds = Math.max(0.001, (ended - headersAt) / 1000)
  const mbps = (bytes * 8 / 1_000_000) / seconds
  const contentRange = response.headers.get('content-range') || ''
  const totalMatch = /\/(\d+)$/.exec(contentRange)
  return {
    status:response.status,
    finalUrl:response.url,
    contentType:response.headers.get('content-type') || '',
    contentLength:response.headers.get('content-length') || '',
    contentRange,
    acceptRanges:response.headers.get('accept-ranges') || '',
    totalBytes:totalMatch ? Number(totalMatch[1]) : 0,
    bytesRead:bytes,
    ttfbMs:Math.round(headersAt - began),
    sampleSeconds:Number(seconds.toFixed(3)),
    throughputMbps:Number(mbps.toFixed(2)),
    firstBytes:first,
    rangeHonored:response.status === 206 && /^bytes /i.test(contentRange),
  }
}

function runTool(command, args, timeout = 90000) {
  const result = spawnSync(command, args, {
    encoding:'utf8',
    timeout,
    maxBuffer:4 * 1024 * 1024,
  })
  return {
    ok:result.status === 0,
    status:result.status,
    signal:result.signal || '',
    stdout:String(result.stdout || '').trim(),
    stderr:String(result.stderr || '').trim().slice(-4000),
  }
}

const searchStart = performance.now()
const search = await source.run({ action:'search', query:QUERY, context:{} })
const searchMs = Math.round(performance.now() - searchStart)
must(Array.isArray(search.items) && search.items.length, 'KayoAnime live search returned no results')

const anime = pickAnime(search.items)
const episodeStart = performance.now()
const episodeResult = await source.run({ action:'episodes', item:anime, context:{} })
const episodesMs = Math.round(performance.now() - episodeStart)
must(Array.isArray(episodeResult.episodes) && episodeResult.episodes.length, 'KayoAnime live episode listing returned no playable episodes')

const episode = pickEpisode(episodeResult.episodes)
const descriptor = source._probe?.mediaDescriptor?.(episode.id)
must(descriptor?.url, 'KayoAnime could not build a media descriptor')
const decodedEpisode = source._test?.decodeEpisode?.(episode.id)
must(decodedEpisode?.id, 'KayoAnime could not decode the selected Drive episode')
const resolvedDrive = await source._probe?.resolveDriveDownload?.(decodedEpisode)
must(resolvedDrive?.url, 'KayoAnime could not resolve the Google Drive confirmation handoff')
const media = {
  ...descriptor,
  url:resolvedDrive.url,
  headers:{ ...(descriptor.headers || {}), ...(resolvedDrive.headers || {}) },
}
const startRange = await rangeProbe(media.url, 0, SAMPLE_BYTES, media.headers)
must(startRange.bytesRead >= 512 * 1024, 'KayoAnime media returned too little data')
must(startRange.rangeHonored, 'KayoAnime media host did not honor byte ranges; seeking would be unreliable')

const signature = startRange.firstBytes.slice(0, 4)
const isMkv = signature.length === 4 && signature[0] === 0x1a && signature[1] === 0x45 && signature[2] === 0xdf && signature[3] === 0xa3
const isMp4 = String.fromCharCode(...startRange.firstBytes.slice(4, 8)) === 'ftyp'
must(isMkv || isMp4 || /video|octet-stream/i.test(startRange.contentType), 'KayoAnime media did not look like a video container')

let seekRange = null
if (startRange.totalBytes > SAMPLE_BYTES * 4) {
  const middle = Math.max(0, Math.floor(startRange.totalBytes * 0.45))
  seekRange = await rangeProbe(media.url, middle, SAMPLE_BYTES, media.headers)
  must(seekRange.rangeHonored, 'KayoAnime middle-range seek probe failed')
  must(seekRange.bytesRead >= 512 * 1024, 'KayoAnime middle-range seek returned too little data')
}

const mediaUserAgent = media.headers?.['User-Agent'] || media.headers?.['user-agent'] || UA
const mediaHeaderBlob = Object.entries(media.headers || {})
  .filter(([key]) => key.toLowerCase() !== 'user-agent')
  .map(([key, value]) => key + ': ' + String(value))
  .join('\\r\\n')
const mediaInputArgs = ['-user_agent', mediaUserAgent]
if (mediaHeaderBlob) mediaInputArgs.push('-headers', mediaHeaderBlob + '\\r\\n')

const ffprobe = runTool('ffprobe', [
  '-v','error',
  ...mediaInputArgs,
  '-show_entries','format=format_name,duration,size,bit_rate:stream=index,codec_type,codec_name,width,height',
  '-of','json',
  media.url,
], 120000)
must(ffprobe.ok, 'ffprobe could not inspect KayoAnime media: ' + ffprobe.stderr)

let probeJson={}
try { probeJson=JSON.parse(ffprobe.stdout) } catch {}
const duration=Number(probeJson?.format?.duration || 0)
const bitRate=Number(probeJson?.format?.bit_rate || 0)
const bitrateMbps=bitRate > 0 ? bitRate / 1_000_000 : 0
const headroom=bitrateMbps > 0 ? startRange.throughputMbps / bitrateMbps : 0

const decodeStart=runTool('ffmpeg',[
  '-v','error',
  ...mediaInputArgs,
  '-i',media.url,
  '-t','8',
  '-map','0:v:0',
  '-f','null','-',
],120000)
must(decodeStart.ok,'ffmpeg could not decode the start of KayoAnime media: '+decodeStart.stderr)

let decodeSeek={ok:true,status:0,stderr:''}
if (duration > 180) {
  const seekSeconds=Math.min(300,Math.max(60,Math.floor(duration*0.4)))
  decodeSeek=runTool('ffmpeg',[
    '-v','error',
    ...mediaInputArgs,
    '-ss',String(seekSeconds),
    '-i',media.url,
    '-t','6',
    '-map','0:v:0',
    '-f','null','-',
  ],120000)
  must(decodeSeek.ok,'ffmpeg seek/decode failed for KayoAnime media: '+decodeSeek.stderr)
}

const report={
  source:source.name,
  query:QUERY,
  searchMs,
  resultCount:search.items.length,
  selectedAnime:anime.title,
  episodesMs,
  episodeCount:episodeResult.episodes.length,
  selectedEpisode:episode.title,
  media:{
    fileName:media.fileName,
    mimetype:media.mimetype,
    host:new URL(media.url).hostname,
  },
  transport:{
    startRange,
    seekRange,
    bitrateMbps:Number(bitrateMbps.toFixed(2)),
    throughputHeadroom:headroom ? Number(headroom.toFixed(2)) : null,
  },
  playback:{
    ffprobe:true,
    durationSeconds:duration ? Number(duration.toFixed(1)) : null,
    streams:probeJson.streams || [],
    decodeStart:true,
    decodeSeek:decodeSeek.ok,
  },
  verdict:'PASS',
}

console.log(JSON.stringify(report,null,2))
