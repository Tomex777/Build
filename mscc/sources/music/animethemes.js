import { clean, fetchJson, firstHttpUrl, mimeForUrl, safeFileName, sendAudio, walkObjects } from './_shared.js'

const API = 'https://api.animethemes.moe'

function songRows(data) {
  const rows = []
  const seen = new Set()
  walkObjects(data, object => {
    const title = clean(object?.title || object?.name || '', 140)
    if (!title) return
    const media = firstHttpUrl(object, (url, owner) => {
      const mime = String(owner?.mimetype || '')
      return /animethemes\.moe/i.test(url) && (
        /audio/i.test(mime) ||
        /\.(ogg|mp3|m4a|flac|opus)(?:$|\?)/i.test(url)
      )
    })
    if (!media || seen.has(media)) return

    let artist = ''
    if (Array.isArray(object?.artists)) {
      artist = object.artists.map(value => value?.name || value?.title || value).filter(Boolean).join(', ')
    } else if (object?.artist) {
      artist = object.artist?.name || object.artist
    }

    const id = String(object?.id ?? media)
    seen.add(media)
    rows.push({
      id,
      title,
      artist:clean(artist, 120),
      url:media,
    })
  })
  return rows.slice(0, 25)
}

async function searchAnimeThemes(query) {
  const params = new URLSearchParams({
    q:query,
    'page[size]':'25',
    include:'artists,animethemes.anime,animethemes.animethemeentries.videos.audio',
  })
  const { data } = await fetchJson(API + '/song?' + params)
  const items = songRows(data)
  if (!items.length) throw new Error('AnimeThemes returned no playable theme results.')
  return items
}

export default {
  id:'animethemes',
  name:'AnimeThemes',
  description:'Anime opening and ending specialist fallback.',
  fallbackOrder:80,
  brandAliases:['Anime Themes'],

  async run({ action, query, item, track, context }) {
    if (action === 'search') return { items:await searchAnimeThemes(clean(query, 180)) }
    if (action === 'download') {
      const chosen = track || item || {}
      const url = String(chosen?.url || '').trim()
      if (!url) throw new Error('AnimeThemes media URL is missing.')
      const mime = mimeForUrl(url, 'audio/ogg')
      return sendAudio(context, {
        url,
        mimetype:mime,
        title:chosen.title,
        artist:chosen.artist,
        fileName:safeFileName(chosen.title, chosen.artist, new URL(url).pathname.split('.').pop() || 'ogg'),
      })
    }
    throw new Error('Unsupported AnimeThemes music action: ' + action)
  },

  _test:{ songRows },
}
