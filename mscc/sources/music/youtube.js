import {
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
      rawVideoId:row.videoId,
    })
  })
  return items.slice(0, 25)
}

async function searchYouTube(query) {
  const url = 'https://www.youtube.com/results?search_query=' + encodeURIComponent(query)
  const { text } = await fetchText(url, {
    headers:{
      'accept':'text/html,application/xhtml+xml',
      'accept-language':'en-US,en;q=0.9',
    },
  })
  const data =
    extractBalancedJson(text, 'var ytInitialData =') ||
    extractBalancedJson(text, 'ytInitialData =')
  const items = data ? youtubeSearchItems(data) : []
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
      const media = await resolveYouTubeAudio(chosen)
      const mime = media.mimetype || 'audio/webm'
      return sendAudio(context, {
        url:media.url,
        mimetype:mime,
        title:chosen.title,
        artist:chosen.artist,
        fileName:safeFileName(chosen.title, chosen.artist, extensionForMime(mime)),
      })
    }
    throw new Error('Unsupported YouTube music action: ' + action)
  },

  _test:{ youtubeSearchItems },
}
