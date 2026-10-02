import { clean, fetchJson, probeMedia, safeFileName, sendAudio } from './_shared.js'

const API = 'https://tracks.monochrome.st'

async function searchMonochrome(query) {
  const { data } = await fetchJson(API + '/search/tracks?q=' + encodeURIComponent(query) + '&limit=25')
  const rows = Array.isArray(data)
    ? data
    : Array.isArray(data?.tracks)
      ? data.tracks
      : Array.isArray(data?.items)
        ? data.items
        : Array.isArray(data?.data?.tracks)
          ? data.data.tracks
          : []
  const items = rows.flatMap(row => {
    const id = row?.trackId || row?.id || row?.recordingId
    const title = clean(row?.title || row?.name || '', 140)
    if (!id || !title) return []
    const artists = Array.isArray(row?.artistNames)
      ? row.artistNames.join(', ')
      : row?.artistNames || row?.artist || ''
    return [{
      id:String(id),
      title,
      artist:clean(artists, 120),
      album:clean(row?.releaseTitle || row?.album || '', 120),
      isrc:clean(row?.isrc || '', 40),
    }]
  })
  if (!items.length) throw new Error('Monochrome returned no music results.')
  return items
}

export default {
  id:'monochrome',
  name:'Monochrome',
  description:'Lossless direct-track fallback with FLAC delivery.',
  fallbackOrder:30,

  async run({ action, query, item, track, context }) {
    if (action === 'search') return { items:await searchMonochrome(clean(query, 180)) }
    if (action === 'download') {
      const chosen = track || item || {}
      const id = String(chosen?.id || '').trim()
      if (!id) throw new Error('Monochrome track ID is missing.')
      const media = await probeMedia(API + '/track/' + encodeURIComponent(id))
      return sendAudio(context, {
        url:media.url,
        mimetype:media.mimetype || 'audio/flac',
        title:chosen.title,
        artist:chosen.artist,
        fileName:safeFileName(chosen.title, chosen.artist, 'flac'),
      })
    }
    throw new Error('Unsupported Monochrome music action: ' + action)
  },
}
