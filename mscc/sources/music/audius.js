import { clean, fetchJson, probeMedia, safeFileName, sendAudio } from './_shared.js'

const API = 'https://api.audius.co'
const APP = 'MSCC'

async function searchAudius(query) {
  const { data } = await fetchJson(
    API + '/v1/tracks/search?query=' + encodeURIComponent(query) + '&limit=25&app_name=' + encodeURIComponent(APP),
  )
  const rows = Array.isArray(data?.data) ? data.data : []
  const items = rows.flatMap(row => {
    if (!row?.id || row?.is_streamable === false) return []
    return [{
      id:String(row.id),
      title:clean(row.title || '', 140),
      artist:clean(row.user?.name || row.user?.handle || '', 120),
      duration:Number(row.duration || 0) || 0,
      durationSeconds:Number(row.duration || 0) || 0,
      isDownloadable:row.is_downloadable === true,
    }]
  }).filter(item => item.title)
  if (!items.length) throw new Error('Audius returned no streamable music results.')
  return items
}

async function resolveAudius(item) {
  const id = String(item?.id || '').trim()
  if (!id) throw new Error('Audius track ID is missing.')
  const stream = API + '/v1/tracks/' + encodeURIComponent(id) + '/stream?app_name=' + encodeURIComponent(APP)
  const probed = await probeMedia(stream)
  return {
    url:probed.url,
    mimetype:probed.mimetype || 'audio/mpeg',
  }
}

export default {
  id:'audius',
  name:'Audius',
  description:'Public Audius catalog and full-track stream fallback.',
  fallbackOrder:20,

  async run({ action, query, item, track, context }) {
    if (action === 'search') return { items:await searchAudius(clean(query, 180)) }
    if (action === 'download') {
      const chosen = track || item || {}
      const media = await resolveAudius(chosen)
      return sendAudio(context, {
        url:media.url,
        mimetype:media.mimetype,
        title:chosen.title,
        artist:chosen.artist,
        fileName:safeFileName(chosen.title, chosen.artist, media.mimetype.includes('mp4') ? 'm4a' : 'mp3'),
      })
    }
    throw new Error('Unsupported Audius music action: ' + action)
  },
}
