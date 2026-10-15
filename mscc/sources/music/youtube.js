import { spawn } from 'node:child_process'
import { createWriteStream } from 'node:fs'
import { mkdtemp, rm, stat } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { Readable, Transform } from 'node:stream'
import { pipeline } from 'node:stream/promises'
import { downloadYouTubeAudioWithDlp } from '../../youtube-dlp.js'
import { searchYouTubeVideos } from '../../youtube-video.js'
import {
  MUSIC_UA,
  clean,
  extractBalancedJson,
  extensionForMime,
  fetchJson,
  fetchText,
  probeMedia,
  safeFileName,
  sendAudio,
  textFromRuns,
  walkObjects,
} from './_shared.js'

function youtubeSearchItems(initialData) {
  const items = []
  const seen = new Set()
  walkObjects(initialData, object => {
    const row = object?.videoRenderer
    if (!row?.videoId || seen.has(row.videoId)) return
    const title = textFromRuns(row.title)
    if (!title) return
    const duration = textFromRuns(row.lengthText)
    if (!duration) return
    const artist = textFromRuns(row.ownerText) || textFromRuns(row.longBylineText)
    seen.add(row.videoId)
    items.push({
      id:row.videoId,
      title,
      artist,
      duration,
      url:'https://www.youtube.com/watch?v=' + row.videoId,
      cover:'https://i.ytimg.com/vi/' + row.videoId + '/hqdefault.jpg',
      rawVideoId:row.videoId,
    })
  })
  return items.slice(0, 25)
}

async function searchYouTube(query) {
  // Reuse the YouTube video resolver's HTML + Innertube search fallback.
  // A changed HTML payload used to make .song silently lose its main source.
  const videos = await searchYouTubeVideos(clean(query, 180), { limit:25 })
  const items = videos.map(video => ({
    id:video.id,
    title:video.title,
    artist:video.channel,
    duration:video.duration,
    url:video.url,
    cover:video.thumbnail,
    rawVideoId:video.id,
  }))
  if (!items.length) throw new Error('YouTube returned no usable music results.')
  return items
}

function innertubeValue(html, name) {
  const escaped = String(name).replace(/[.*+?^$()|[\]\\{}]/g, '\\$&')
  return new RegExp('"' + escaped + '":"([^"]+)"').exec(String(html || ''))?.[1] || ''
}

function audioFormats(player) {
  const streaming = player?.streamingData || {}
  return [
    ...(Array.isArray(streaming.adaptiveFormats) ? streaming.adaptiveFormats : []),
    ...(Array.isArray(streaming.formats) ? streaming.formats : []),
  ]
    .filter(format => /^audio\//i.test(String(format?.mimeType || '')) && typeof format?.url === 'string')
    .sort((a,b) => Number(b?.bitrate || 0) - Number(a?.bitrate || 0))
}

async function playerResponse(apiKey, videoId, client, watchUrl) {
  const { data } = await fetchJson(
    'https://www.youtube.com/youtubei/v1/player?key=' + encodeURIComponent(apiKey) + '&prettyPrint=false',
    {
      method:'POST',
      headers:{
        'origin':'https://www.youtube.com',
        'referer':watchUrl,
      },
      body:{
        context:{ client },
        videoId,
        playbackContext:{
          contentPlaybackContext:{
            html5Preference:'HTML5_PREF_WANTS',
          },
        },
        contentCheckOk:true,
        racyCheckOk:true,
      },
    },
  )
  return data
}

async function resolveYouTubeAudio(item) {
  const videoId = String(item?.rawVideoId || item?.id || '').trim()
  if (!/^[A-Za-z0-9_-]{11}$/.test(videoId)) throw new Error('Invalid YouTube video ID.')
  const watchUrl = 'https://www.youtube.com/watch?v=' + encodeURIComponent(videoId)
  const { text:watchHtml } = await fetchText(watchUrl, {
    headers:{
      'accept':'text/html,application/xhtml+xml',
      'accept-language':'en-US,en;q=0.9',
    },
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
      clientVersion:webVersion || '2.20260930.00.00',
      hl:'en',
      gl:'US',
    },
  ]

  let lastError = null
  for (const client of clients) {
    let player
    try {
      player = await playerResponse(apiKey, videoId, client, watchUrl)
    } catch (error) {
      lastError = error
      continue
    }
    if (player?.playabilityStatus?.status !== 'OK') {
      lastError = new Error(player?.playabilityStatus?.reason || 'YouTube playback is unavailable.')
      continue
    }
    for (const format of audioFormats(player)) {
      try {
        const probed = await probeMedia(format.url, {
          headers:{ 'referer':watchUrl },
        })
        return {
          url:probed.url,
          mimetype:probed.mimetype || String(format.mimeType || '').split(';')[0] || 'audio/webm',
          bitrate:Number(format.bitrate || 0) || 0,
        }
      } catch (error) {
        lastError = error
      }
    }
  }

  throw lastError || new Error('YouTube did not expose a directly usable audio format.')
}


const MAX_AUDIO_BYTES = 32 * 1024 * 1024

function audioMime(ext) {
  return ext === 'm4a' || ext === 'mp4' ? 'audio/mp4'
    : ext === 'webm' ? 'audio/webm'
    : ext === 'ogg' || ext === 'opus' ? 'audio/ogg'
    : 'audio/mpeg'
}

async function transcodeAudio(input, output) {
  await new Promise((resolve, reject) => {
    const child = spawn('ffmpeg', [
      '-nostdin', '-hide_banner', '-loglevel', 'error', '-y',
      '-i', input, '-vn', '-codec:a', 'libmp3lame', '-q:a', '5', output,
    ], { stdio:['ignore', 'ignore', 'pipe'] })
    let stderr = ''
    child.stderr.on('data', chunk => { stderr = (stderr + String(chunk)).slice(-600) })
    child.once('error', reject)
    child.once('close', code => code === 0 ? resolve() : reject(
      new Error('FFmpeg audio conversion failed (' + code + '): ' + stderr.replace(/https?:\\/\\/\\S+/g, '[URL]'))
    ))
  })
}

async function normalizeAudioFile(info, dir) {
  const ext = String(info.extension || '').toLowerCase()
  if (['mp3', 'm4a', 'mp4'].includes(ext)) {
    return { file:info.file, extension:ext }
  }
  // WhatsApp audio players are not consistently compatible with WebM/Opus.
  const output = join(dir, 'ready.mp3')
  await transcodeAudio(info.file, output)
  const converted = await stat(output)
  if (!converted.size || converted.size > MAX_AUDIO_BYTES) {
    throw new Error('Converted YouTube audio exceeds the MSCC download limit.')
  }
  return { file:output, extension:'mp3' }
}

// Baileys cannot reliably fetch short-lived signed googlevideo URLs itself.
// Download on the server with the same headers and send verified local bytes.
async function stageDirectAudio(url, mime, dir, videoId) {
  const response = await fetch(url, {
    headers:{
      'user-agent':MUSIC_UA,
      'referer':'https://www.youtube.com/watch?v=' + videoId,
      'accept':'*/*',
    },
    redirect:'follow',
    signal:AbortSignal.timeout(45000),
  })
  if (!response.ok || !response.body) throw new Error('YouTube audio CDN HTTP ' + response.status)
  const length = Number(response.headers.get('content-length') || 0)
  if (length > MAX_AUDIO_BYTES) {
    await response.body.cancel().catch(() => {})
    throw new Error('YouTube audio exceeds the MSCC download limit.')
  }
  const type = response.headers.get('content-type') || mime
  if (/text\\/html|application\\/json/i.test(type)) {
    await response.body.cancel().catch(() => {})
    throw new Error('YouTube audio CDN returned a non-audio response.')
  }
  const extension = extensionForMime(mime)
  const file = join(dir, 'direct.' + extension)
  let bytes = 0
  await pipeline(
    Readable.fromWeb(response.body),
    new Transform({
      transform(chunk, _encoding, done) {
        bytes += chunk.length
        done(bytes > MAX_AUDIO_BYTES ? new Error('YouTube audio exceeds the MSCC download limit.') : null, chunk)
      },
    }),
    createWriteStream(file),
  )
  if (!bytes) throw new Error('YouTube audio CDN returned an empty file.')
  return { file, extension }
}

async function deliverYouTubeAudio(context, chosen) {
  const videoId = String(chosen?.rawVideoId || chosen?.id || '').trim()
  if (!/^[A-Za-z0-9_-]{11}$/.test(videoId)) throw new Error('Invalid YouTube video ID.')
  const dir = await mkdtemp(join(tmpdir(), 'mscc-yt-audio-'))
  try {
    let firstFailure = null
    try {
      const downloaded = await downloadYouTubeAudioWithDlp(videoId, dir, MAX_AUDIO_BYTES)
      const ready = await normalizeAudioFile(downloaded, dir)
      return await sendAudio(context, {
        url:ready.file,
        mimetype:audioMime(ready.extension),
        title:chosen.title,
        artist:chosen.artist,
        fileName:safeFileName(chosen.title, chosen.artist, ready.extension === 'mp4' ? 'm4a' : ready.extension),
      })
    } catch (error) {
      firstFailure = error
      // Log only the error class/code, not signed CDN URLs, cookies or query tokens.
      console.warn('MSCC YouTube music extractor failed; retrying direct media:', error?.code || error?.name || 'Error')
    }

    try {
      const media = await resolveYouTubeAudio(chosen)
      const staged = await stageDirectAudio(media.url, media.mimetype, dir, videoId)
      const ready = await normalizeAudioFile(staged, dir)
      return await sendAudio(context, {
        url:ready.file,
        mimetype:audioMime(ready.extension),
        title:chosen.title,
        artist:chosen.artist,
        fileName:safeFileName(chosen.title, chosen.artist, ready.extension === 'mp4' ? 'm4a' : ready.extension),
      })
    } catch (error) {
      console.warn('MSCC YouTube music direct audio failed:', error?.code || error?.name || 'Error')
      const failure = new Error('YouTube audio download failed through extractor and direct media.')
      failure.code = 'YOUTUBE_MUSIC_DOWNLOAD_FAILED'
      failure.cause = { extractor:firstFailure, direct:error }
      throw failure
    }
  } finally {
    await rm(dir, { recursive:true, force:true })
  }
}

export default {
  id:'youtube',
  name:'YouTube',
  description:'Primary music source using YouTube search and direct audio formats when available.',
  primary:true,
  fallbackOrder:0,
  brandAliases:['YouTube Music'],

  async run({ action, query, item, track, context }) {
    if (action === 'search') {
      return { items:await searchYouTube(clean(query, 180)) }
    }
    if (action === 'download') {
      const chosen = track || item || {}
      return deliverYouTubeAudio(context, chosen)
    }
    throw new Error('Unsupported YouTube music action: ' + action)
  },

  _test:{ youtubeSearchItems },
}
