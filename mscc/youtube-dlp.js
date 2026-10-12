import { spawn } from 'node:child_process'
import { stat } from 'node:fs/promises'
import { basename, resolve, sep } from 'node:path'

const BASE = 'https://www.youtube.com/watch?v='
const DEFAULT_BINARY = '/opt/mscc/tools/yt-dlp/bin/yt-dlp'
const BASE_ARGS = ['--ignore-config', '--no-playlist', '--no-warnings', '--js-runtimes', 'node']
const ID_RE = /^[A-Za-z0-9_-]{11}$/
const TOR_PROXY = 'socks5://127.0.0.1:9050'

// This is an intentionally narrow media-only fallback. Neither WhatsApp nor
// Cortex traffic is proxied. Operators can opt out without changing code.
function torFallbackEnabled() {
  return process.env.MSCC_YOUTUBE_TOR_FALLBACK !== '0'
}

function argsForTransport(args, transport) {
  return transport === 'tor' ? ['--proxy', TOR_PROXY, ...args] : args
}

async function runYouTubeExtractor(args, opts = {}, { preferTor = false } = {}) {
  if (preferTor && torFallbackEnabled()) {
    return { output:await runDlp(argsForTransport(args, 'tor'), opts), transport:'tor' }
  }
  try {
    return { output:await runDlp(args, opts), transport:'direct' }
  } catch (error) {
    if (!torFallbackEnabled() || !isYouTubeAccessChallenge(error)) throw error
    // The previous production probe established that Azure's local Tor service
    // can resolve the same video. Retry only a YouTube bot challenge, not all
    // errors (bad IDs, private videos, file limits, or network issues).
    return { output:await runDlp(argsForTransport(args, 'tor'), opts), transport:'tor' }
  }
}


export function isYouTubeAccessChallenge(error) {
  const message = String(error?.message || error || '')
  return error?.code === 'YOUTUBE_ACCESS_CHALLENGE' ||
    /sign in to confirm.{0,80}(not a bot|you.re not a bot)|unusual traffic|automated queries|confirm.{0,40}not a bot/i.test(message)
}

export function youtubeChallengeMessage(id) {
  const videoUrl = ID_RE.test(String(id || '')) ? BASE + id : 'https://www.youtube.com/'
  return `YouTube is asking the MSCC server to verify itself, not you. I couldn't fetch this video from the server right now. You can watch it here: ${videoUrl}`
}

function downloadFailure(error) {
  if (isYouTubeAccessChallenge(error)) {
    const result = new Error('YouTube challenged the MSCC server while accessing the video.')
    result.code = 'YOUTUBE_ACCESS_CHALLENGE'
    return result
  }
  return error
}

function runDlp(args, { timeoutMs = 60000, maxOutput = 8 * 1024 * 1024 } = {}) {
  const executable = process.env.MSCC_YOUTUBE_YTDLP_BIN || DEFAULT_BINARY
  return new Promise((ok, fail) => {
    const child = spawn(executable, [...BASE_ARGS, ...args], { stdio:['ignore', 'pipe', 'pipe'], env:process.env })
    let stdout = ''
    let stderr = ''
    let complete = false
    const finish = (error, output) => {
      if (complete) return
      complete = true
      clearTimeout(timer)
      if (error) fail(downloadFailure(error))
      else ok(output)
    }
    const timer = setTimeout(() => {
      child.kill('SIGKILL')
      const error = new Error('YouTube extractor timed out.')
      error.code = 'YOUTUBE_EXTRACTOR_TIMEOUT'
      finish(error)
    }, timeoutMs)
    const append = (stream, chunk) => {
      const part = String(chunk || '')
      if (stream === 'stdout') {
        stdout += part
        if (stdout.length > maxOutput) {
          child.kill('SIGKILL')
          finish(new Error('YouTube extractor output was too large.'))
        }
      } else {
        stderr = (stderr + part).slice(-8192)
      }
    }
    child.stdout.on('data', chunk => append('stdout', chunk))
    child.stderr.on('data', chunk => append('stderr', chunk))
    child.once('error', error => {
      if (error.code === 'ENOENT') {
        const notInstalled = new Error('The optional YouTube extractor is not installed on MSCC.')
        notInstalled.code = 'YOUTUBE_EXTRACTOR_UNAVAILABLE'
        finish(notInstalled)
      } else finish(error)
    })
    child.once('close', code => {
      if (code === 0) finish(null, stdout)
      else finish(new Error(stderr.trim().slice(-700) || `YouTube extractor failed with status ${code}.`))
    })
  })
}

// yt-dlp has already resolved signed stream URLs while producing metadata.
// Re-use those URLs rather than making a second player request for download.
export function directFormatsFromDlp(info = {}) {
  const formats = Array.isArray(info.formats) ? info.formats : []
  return formats.filter(row => {
    const url = String(row?.url || '')
    const protocol = String(row?.protocol || 'https')
    const ext = String(row?.ext || '').toLowerCase()
    return /^https:\/\//i.test(url) &&
      ['https', 'http'].includes(protocol) &&
      ['mp4', 'm4a', 'webm'].includes(ext)
  }).map(row => {
    const vcodec = String(row.vcodec || 'none')
    const acodec = String(row.acodec || 'none')
    const hasVideo = vcodec !== 'none'
    const hasAudio = acodec !== 'none'
    const ext = String(row.ext || '').toLowerCase()
    const container = ext === 'm4a' ? 'mp4' : ext
    return {
      url:String(row.url),
      itag:Number(row.format_id) || 0,
      mimeType:(hasVideo ? 'video/' : 'audio/') + container,
      codecs:[vcodec, acodec].filter(value => value !== 'none').join(', '),
      container,
      width:Number(row.width || 0) || 0,
      height:Number(row.height || 0) || 0,
      bitrate:Math.round((Number(row.tbr || 0) || 0) * 1000),
      contentLength:Number(row.filesize || row.filesize_approx || 0) || 0,
      hasVideo,
      hasAudio,
    }
  }).filter(row => row.hasVideo || row.hasAudio)
}

export async function resolveYouTubeWithDlp(videoId) {
  if (!ID_RE.test(String(videoId || ''))) throw new Error('Invalid YouTube video ID.')
  const { output, transport } = await runYouTubeExtractor([
    '--dump-single-json', '--skip-download', BASE + videoId,
  ])
  let data
  try { data = JSON.parse(output) }
  catch { throw new Error('YouTube extractor returned invalid video information.') }

  const heights = [...new Set((Array.isArray(data.formats) ? data.formats : [])
    .filter(row => row?.vcodec && row.vcodec !== 'none')
    .map(row => Number(row.height || 0))
    .filter(height => Number.isInteger(height) && height >= 144 && height <= 4320))].sort((a,b) => a - b)
  if (!heights.length && Number(data.height) > 0) heights.push(Number(data.height))
  if (!heights.length) throw new Error('No downloadable video formats were provided.')

  return {
    details:{
      id:videoId,
      title:String(data.title || 'YouTube video').slice(0, 180),
      channel:String(data.channel || data.uploader || '').slice(0, 120),
      durationSeconds:Number(data.duration || 0) || 0,
      thumbnail:`https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`,
      url:BASE + videoId,
    },
    // Signed CDN URLs resolved using Tor must also be consumed using Tor.
    // Keep these on yt-dlp so we never accidentally fetch them directly.
    formats:transport === 'tor' ? [] : directFormatsFromDlp(data),
    transport,
    candidates:heights.map(height => ({
      quality:String(height), height, kind:'yt-dlp', container:'mp4', source:'yt-dlp',
      transport, video:null, audio:null,
    })),
  }
}

export async function downloadYouTubeWithDlp(videoId, height, outputDir, maxBytes, transport = 'direct') {
  if (!ID_RE.test(String(videoId || ''))) throw new Error('Invalid YouTube video ID.')
  const h = Number(height)
  if (!Number.isInteger(h) || h < 144 || h > 4320) throw new Error('Invalid YouTube quality.')
  const max = Math.max(1, Math.floor(Number(maxBytes) || 0))
  const template = resolve(outputDir, 'video.%(ext)s')
  const { output:text } = await runYouTubeExtractor([
    '--no-part',
    '--max-filesize', String(max),
    '--socket-timeout', '30',
    '--retries', '2',
    '--fragment-retries', '2',
    '-f', `bv*[height<=${h}]+ba/b[height<=${h}]`,
    '--merge-output-format', 'mp4',
    '-o', template,
    '--print', 'after_move:filepath',
    BASE + videoId,
  ], { timeoutMs:15 * 60 * 1000, maxOutput:16384 }, {
    preferTor:transport === 'tor',
  })
  const chosen = text.trim().split(/\r?\n/).filter(Boolean).at(-1) || ''
  const file = resolve(chosen)
  const folder = resolve(outputDir) + sep
  if (!chosen || !file.startsWith(folder) || !basename(file).startsWith('video.')) {
    throw new Error('YouTube extractor returned an unexpected file location.')
  }
  const info = await stat(file)
  if (!info.isFile() || !info.size || info.size > max) throw new Error('YouTube video exceeds the MSCC download limit.')
  const ext = file.toLowerCase().split('.').at(-1)
  return { file, extension:ext === 'mp4' ? 'mp4' : ext === 'webm' ? 'webm' : 'mkv', size:info.size }
}
