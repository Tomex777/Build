import { createWriteStream } from 'node:fs'
import { mkdir, rm, stat } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { Readable } from 'node:stream'
import { pipeline } from 'node:stream/promises'
import { spawn } from 'node:child_process'
import {
  MUSIC_UA,
  clean,
  extractBalancedJson,
  fetchJson,
  fetchText,
  probeMedia,
  textFromRuns,
  walkObjects,
} from './sources/music/_shared.js'

const WATCH = 'https://www.youtube.com/watch?v='
const MAX_SEARCH_RESULTS = 20
const MAX_DOWNLOAD_BYTES = Math.max(
  64 * 1024 * 1024,
  Number(process.env.MSCC_YOUTUBE_MAX_BYTES || 1_500_000_000) || 1_500_000_000,
)

function thumbnailFromRenderer(row = {}) {
  const thumbs = row?.thumbnail?.thumbnails
  if (!Array.isArray(thumbs) || !thumbs.length) return ''
  return String(thumbs[thumbs.length - 1]?.url || '').trim()
}

export function youtubeSearchItems(initialData) {
  const items = []
  const seen = new Set()
  walkObjects(initialData, object => {
    const row = object?.videoRenderer
    if (!row?.videoId || seen.has(row.videoId)) return
    const title = textFromRuns(row.title)
    const duration = textFromRuns(row.lengthText)
    if (!title || !duration) return

    seen.add(row.videoId)
    items.push({
      id:String(row.videoId),
      title,
      channel:textFromRuns(row.ownerText) || textFromRuns(row.longBylineText),
      duration,
      views:textFromRuns(row.viewCountText),
      published:textFromRuns(row.publishedTimeText),
      thumbnail:thumbnailFromRenderer(row) || `https://i.ytimg.com/vi/${row.videoId}/hqdefault.jpg`,
      url:WATCH + row.videoId,
    })
  })
  return items.slice(0, MAX_SEARCH_RESULTS)
}

export async function searchYouTubeVideos(query, { limit = MAX_SEARCH_RESULTS } = {}) {
  const term = clean(query, 180)
  if (!term) return []
  const url = 'https://www.youtube.com/results?search_query=' + encodeURIComponent(term)
  const { text } = await fetchText(url, {
    headers:{
      accept:'text/html,application/xhtml+xml',
      'accept-language':'en-US,en;q=0.9',
    },
  })
  const data =
    extractBalancedJson(text, 'var ytInitialData =') ||
    extractBalancedJson(text, 'ytInitialData =')
  return (data ? youtubeSearchItems(data) : []).slice(0, Math.max(1, Math.min(MAX_SEARCH_RESULTS, Number(limit) || MAX_SEARCH_RESULTS)))
}

function innertubeValue(html, name) {
  const escaped = String(name).replace(/[.*+?^$()|[\]\\{}]/g, '\\$&')
  return new RegExp('"' + escaped + '":"([^"]+)"').exec(String(html || ''))?.[1] || ''
}

async function playerResponse(apiKey, videoId, client, watchUrl) {
  const { data } = await fetchJson(
    'https://www.youtube.com/youtubei/v1/player?key=' + encodeURIComponent(apiKey) + '&prettyPrint=false',
    {
      method:'POST',
      headers:{
        origin:'https://www.youtube.com',
        referer:watchUrl,
      },
      body:{
        context:{ client },
        videoId,
        playbackContext:{ contentPlaybackContext:{ html5Preference:'HTML5_PREF_WANTS' } },
        contentCheckOk:true,
        racyCheckOk:true,
      },
      timeoutMs:20000,
    },
  )
  return data
}

function parseMime(value = '') {
  const raw = String(value || '')
  const mimeType = raw.split(';')[0].trim().toLowerCase()
  const codecs = /codecs="([^"]+)"/i.exec(raw)?.[1] || ''
  return {
    mimeType,
    codecs,
    container:mimeType.split('/')[1] || '',
  }
}

function directFormats(player = {}) {
  const streaming = player?.streamingData || {}
  const source = [
    ...(Array.isArray(streaming.formats) ? streaming.formats : []),
    ...(Array.isArray(streaming.adaptiveFormats) ? streaming.adaptiveFormats : []),
  ]

  return source
    .filter(format => typeof format?.url === 'string' && /^https:\/\//i.test(format.url))
    .map(format => {
      const mime = parseMime(format.mimeType)
      const hasVideo = mime.mimeType.startsWith('video/') || Number(format.width || 0) > 0 || Number(format.height || 0) > 0
      const hasAudio = mime.mimeType.startsWith('audio/') || Number(format.audioChannels || 0) > 0
      return {
        itag:Number(format.itag || 0) || 0,
        url:String(format.url),
        mimeType:mime.mimeType,
        codecs:mime.codecs,
        container:mime.container,
        width:Number(format.width || 0) || 0,
        height:Number(format.height || 0) || 0,
        fps:Number(format.fps || 0) || 0,
        bitrate:Number(format.bitrate || 0) || 0,
        contentLength:Number(format.contentLength || 0) || 0,
        audioChannels:Number(format.audioChannels || 0) || 0,
        hasVideo,
        hasAudio,
      }
    })
}

function formatScore(format = {}) {
  let score = Number(format.bitrate || 0)
  if (format.container === 'mp4') score += 10_000_000_000
  if (/avc1/i.test(format.codecs || '')) score += 5_000_000_000
  if (/mp4a/i.test(format.codecs || '')) score += 2_500_000_000
  return score
}

function bestOf(formats = []) {
  return [...formats].sort((a,b) => formatScore(b) - formatScore(a))[0] || null
}

export function buildVideoCandidates(formats = []) {
  const all = Array.isArray(formats) ? formats : []
  const direct = all.filter(format => format?.url && format?.hasVideo)
  const audioOnly = all.filter(format => format?.url && format?.hasAudio && !format?.hasVideo)

  const heights = [...new Set(direct.map(format => Number(format.height || 0)).filter(Boolean))].sort((a,b) => a - b)
  const candidates = []

  for (const height of heights) {
    const progressive = bestOf(direct.filter(format => format.height === height && format.hasAudio))
    if (progressive) {
      candidates.push({
        quality:String(height),
        height,
        kind:'progressive',
        container:progressive.container || 'mp4',
        video:progressive,
        audio:null,
      })
      continue
    }

    const video = bestOf(direct.filter(format => format.height === height && !format.hasAudio))
    const audio = video
      ? bestOf(audioOnly.filter(format => format.container === video.container)) ||
        (video.container === 'mp4' ? bestOf(audioOnly.filter(format => format.container === 'mp4')) : null)
      : null

    if (video && audio) {
      candidates.push({
        quality:String(height),
        height,
        kind:'adaptive',
        container:video.container || 'mp4',
        video,
        audio,
      })
    }
  }

  return candidates
}

export function selectVideoCandidate(candidates = [], quality = 'best') {
  const list = [...(Array.isArray(candidates) ? candidates : [])].filter(item => Number(item?.height || 0) > 0)
  if (!list.length) return null
  const requested = String(quality || 'best').trim().toLowerCase().replace(/p$/, '')
  if (requested === 'best' || requested === 'source') {
    return [...list].sort((a,b) => b.height - a.height)[0]
  }

  const wanted = requested === '4k' ? 2160 : Number(requested)
  if (!Number.isFinite(wanted) || wanted <= 0) return [...list].sort((a,b) => b.height - a.height)[0]
  const exact = list.find(item => item.height === wanted)
  if (exact) return exact

  const lower = list.filter(item => item.height <= wanted).sort((a,b) => b.height - a.height)[0]
  return lower || [...list].sort((a,b) => a.height - b.height)[0]
}

export async function resolveYouTubeVideo(videoId) {
  const id = String(videoId || '').trim()
  if (!/^[A-Za-z0-9_-]{11}$/.test(id)) throw new Error('Invalid YouTube video ID.')

  const watchUrl = WATCH + encodeURIComponent(id)
  const { text:watchHtml } = await fetchText(watchUrl, {
    headers:{
      accept:'text/html,application/xhtml+xml',
      'accept-language':'en-US,en;q=0.9',
    },
    timeoutMs:20000,
  })
  const apiKey = innertubeValue(watchHtml, 'INNERTUBE_API_KEY')
  const webVersion = innertubeValue(watchHtml, 'INNERTUBE_CONTEXT_CLIENT_VERSION')
  if (!apiKey) throw new Error('YouTube Innertube key was not found.')

  const clients = [
    {
      clientName:'ANDROID',
      clientVersion:'20.10.38',
      androidSdkVersion:35,
      hl:'en',
      gl:'US',
      userAgent:'com.google.android.youtube/20.10.38 (Linux; U; Android 16) gzip',
    },
    {
      clientName:'ANDROID_VR',
      clientVersion:'1.60.19',
      androidSdkVersion:35,
      hl:'en',
      gl:'US',
      userAgent:'com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 16) gzip',
    },
    {
      clientName:'WEB',
      clientVersion:webVersion || '2.20261002.00.00',
      hl:'en',
      gl:'US',
    },
  ]

  const byItag = new Map()
  let details = null
  let lastError = null

  for (const client of clients) {
    let player
    try {
      player = await playerResponse(apiKey, id, client, watchUrl)
    } catch (error) {
      lastError = error
      continue
    }

    if (player?.playabilityStatus?.status !== 'OK') {
      lastError = new Error(player?.playabilityStatus?.reason || 'YouTube playback is unavailable.')
      continue
    }

    if (!details && player?.videoDetails) {
      details = {
        id,
        title:clean(player.videoDetails.title, 180) || 'YouTube video',
        channel:clean(player.videoDetails.author, 120),
        durationSeconds:Number(player.videoDetails.lengthSeconds || 0) || 0,
        thumbnail:`https://i.ytimg.com/vi/${id}/hqdefault.jpg`,
        url:watchUrl,
      }
    }

    for (const format of directFormats(player)) {
      if (!format.itag || byItag.has(format.itag)) continue
      byItag.set(format.itag, format)
    }
  }

  const formats = [...byItag.values()]
  const candidates = buildVideoCandidates(formats)
  if (!candidates.length) {
    throw lastError || new Error('YouTube did not expose a directly usable video format.')
  }

  return {
    details:details || {
      id,
      title:'YouTube video',
      channel:'',
      durationSeconds:0,
      thumbnail:`https://i.ytimg.com/vi/${id}/hqdefault.jpg`,
      url:watchUrl,
    },
    formats,
    candidates,
  }
}

async function probeCandidate(candidate, watchUrl) {
  const headers = { referer:watchUrl }
  await probeMedia(candidate.video.url, { headers, timeoutMs:15000 })
  if (candidate.audio) await probeMedia(candidate.audio.url, { headers, timeoutMs:15000 })
}

async function downloadUrl(url, file, watchUrl) {
  const response = await fetch(url, {
    headers:{
      'user-agent':MUSIC_UA,
      accept:'*/*',
      referer:watchUrl,
    },
    redirect:'follow',
    signal:AbortSignal.timeout(30 * 60 * 1000),
  })
  if (!response.ok || !response.body) throw new Error(`YouTube media returned HTTP ${response.status}.`)

  const length = Number(response.headers.get('content-length') || 0) || 0
  if (length && length > MAX_DOWNLOAD_BYTES) {
    await response.body.cancel().catch(() => {})
    throw new Error('That YouTube video is too large for this MSCC download limit.')
  }

  await pipeline(Readable.fromWeb(response.body), createWriteStream(file))
  const info = await stat(file)
  if (!info.size) throw new Error('YouTube returned an empty media file.')
  if (info.size > MAX_DOWNLOAD_BYTES) throw new Error('That YouTube video is too large for this MSCC download limit.')
  return info.size
}

async function ffmpegMux(videoFile, audioFile, outputFile) {
  await new Promise((resolve, reject) => {
    const child = spawn('ffmpeg', [
      '-hide_banner',
      '-loglevel','error',
      '-y',
      '-i',videoFile,
      '-i',audioFile,
      '-map','0:v:0',
      '-map','1:a:0',
      '-c','copy',
      '-movflags','+faststart',
      outputFile,
    ], { stdio:['ignore','ignore','pipe'] })

    let errorText = ''
    child.stderr.on('data', chunk => {
      errorText += String(chunk || '')
      if (errorText.length > 4000) errorText = errorText.slice(-4000)
    })
    child.once('error', error => reject(
      error?.code === 'ENOENT'
        ? new Error('FFmpeg is not installed on this MSCC host.')
        : error
    ))
    child.once('close', code => {
      if (code === 0) resolve()
      else reject(new Error(clean(errorText, 500) || `FFmpeg exited with code ${code}.`))
    })
  })
}

function safeVideoName(title, extension = 'mp4') {
  const base = clean(title, 170)
    .replace(/[\\/:*?"<>|\x00-\x1f]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return `${base || 'YouTube video'}.${String(extension || 'mp4').replace(/[^a-z0-9]/gi, '') || 'mp4'}`
}

export async function prepareYouTubeVideo(videoId, {
  quality = 'best',
  onStage = null,
} = {}) {
  const resolved = await resolveYouTubeVideo(videoId)
  const candidate = selectVideoCandidate(resolved.candidates, quality)
  if (!candidate) throw new Error('No usable YouTube video quality was found.')

  const watchUrl = resolved.details.url || WATCH + videoId
  if (typeof onStage === 'function') await onStage('Checking media…')
  await probeCandidate(candidate, watchUrl)

  const dir = join(tmpdir(), 'mscc-youtube-' + randomUUID())
  await mkdir(dir, { recursive:true })
  const cleanup = async () => rm(dir, { recursive:true, force:true }).catch(() => {})

  try {
    const extension = candidate.container === 'webm' ? 'webm' : 'mp4'
    const output = join(dir, 'output.' + extension)

    if (candidate.kind === 'progressive') {
      if (typeof onStage === 'function') await onStage(`Downloading ${candidate.height}p…`)
      await downloadUrl(candidate.video.url, output, watchUrl)
    } else {
      if (typeof onStage === 'function') await onStage(`Downloading ${candidate.height}p video…`)
      const videoFile = join(dir, 'video.' + (candidate.video.container || 'mp4'))
      const audioFile = join(dir, 'audio.' + (candidate.audio.container === 'mp4' ? 'm4a' : candidate.audio.container || 'webm'))
      await downloadUrl(candidate.video.url, videoFile, watchUrl)
      if (typeof onStage === 'function') await onStage('Downloading audio…')
      await downloadUrl(candidate.audio.url, audioFile, watchUrl)
      if (typeof onStage === 'function') await onStage('Combining video + audio…')
      await ffmpegMux(videoFile, audioFile, output)
    }

    const info = await stat(output)
    return {
      ...resolved,
      candidate,
      file:output,
      fileName:safeVideoName(resolved.details.title, extension),
      mimetype:extension === 'webm' ? 'video/webm' : 'video/mp4',
      size:info.size,
      cleanup,
    }
  } catch (error) {
    await cleanup()
    throw error
  }
}
