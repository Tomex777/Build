import { clean, fetchJson, mimeForUrl, safeFileName, sendAudio, walkObjects } from './_shared.js'

const ORIGIN = 'https://musicdex.org'

function trackCandidates(data) {
  const out = []
  const seen = new Set()
  walkObjects(data, object => {
    const id = object?.id
    const title = clean(object?.title || object?.name || '', 140)
    const url = String(object?.url || '').trim()
    if (id == null || !title || !url || seen.has(String(id))) return
    if (!/^https?:\/\//i.test(url) && !url.startsWith('/')) return
    seen.add(String(id))
    const artists = Array.isArray(object?.artists)
      ? object.artists.map(value => value?.name || value?.title || value).filter(Boolean).join(', ')
      : object?.artist?.name || object?.artist || ''
    out.push({
      id:String(id),
      title,
      artist:clean(artists, 120),
      album:clean(object?.album?.name || object?.album || '', 120),
      duration:Number(object?.duration || 0) || 0,
      durationSeconds:Number(object?.duration || 0) || 0,
      url:url.startsWith('http') ? url : ORIGIN + '/' + url.replace(/^\//, ''),
    })
  })
  return out.slice(0, 25)
}

async function searchMusicDex(query) {
  const params = new URLSearchParams({ searchQuery:query, perPage:'25' })
  const { data } = await fetchJson(ORIGIN + '/secure/tracks?' + params)
  const items = trackCandidates(data)
  if (!items.length) throw new Error('MusicDex returned no playable soundtrack results.')
  return items
}

export default {
  id:'musicdex',
  name:'MusicDex',
  description:'Anime soundtrack specialist fallback with direct track media.',
  fallbackOrder:70,

  async run({ action, query, item, track, context }) {
    if (action === 'search') return { items:await searchMusicDex(clean(query, 180)) }
    if (action === 'download') {
      const chosen = track || item || {}
      const url = String(chosen?.url || '').trim()
      if (!url) throw new Error('MusicDex media URL is missing.')
      return sendAudio(context, {
        url,
        mimetype:mimeForUrl(url),
        title:chosen.title,
        artist:chosen.artist,
        fileName:safeFileName(chosen.title, chosen.artist, new URL(url).pathname.split('.').pop() || 'mp3'),
      })
    }
    throw new Error('Unsupported MusicDex music action: ' + action)
  },

  _test:{ trackCandidates },
}
