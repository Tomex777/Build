import { createDecipheriv } from 'node:crypto'
import { clean, fetchJson, probeMedia, safeFileName, sendAudio } from './_shared.js'

const API = 'https://rthmx.vercel.app'

function decryptMediaUrl(encrypted) {
  const key = Buffer.from('38346591', 'utf8')
  const key24 = Buffer.concat([key, key, key])
  const decipher = createDecipheriv('des-ede3', key24, null)
  const plain = Buffer.concat([
    decipher.update(Buffer.from(String(encrypted || ''), 'base64')),
    decipher.final(),
  ]).toString('utf8')
  return plain.replace(/_\d+\.mp4(?=($|\?))/i, '_320.mp4')
}

async function searchSaavn(query) {
  const { data } = await fetchJson(API + '/api/songs?q=' + encodeURIComponent(query))
  const rows = Array.isArray(data?.results) ? data.results : []
  const items = rows.slice(0, 25).flatMap(row => {
    const token = clean(row?.token || '', 500)
    const title = clean(row?.title || row?.name || '', 140)
    if (!token || !title) return []
    return [{
      id:token,
      title,
      artist:clean(row?.subtitle || row?.artist || '', 120),
      duration:clean(row?.duration || '', 24),
      cover:String(
        row?.image ||
        row?.image_url ||
        row?.thumbnail ||
        (Array.isArray(row?.images) ? row.images.at(-1)?.url || row.images.at(-1) : '') ||
        ''
      ),
      token,
    }]
  })
  if (!items.length) throw new Error('JioSaavn returned no music results.')
  return items
}

async function resolveSaavn(item) {
  const token = String(item?.token || item?.id || '').trim()
  if (!token) throw new Error('JioSaavn track token is missing.')
  const { data:song } = await fetchJson(API + '/api/song?token=' + encodeURIComponent(token))
  const encrypted = song?.more_info?.encrypted_media_url
  if (!encrypted) throw new Error('JioSaavn did not return a media URL.')
  const url = decryptMediaUrl(encrypted)
  const probed = await probeMedia(url, {
    headers:{ 'referer':'https://saavn-dl.pages.dev/' },
  })
  return {
    url:probed.url,
    mimetype:probed.mimetype || 'audio/mp4',
    title:clean(song?.title || item?.title || '', 140),
    artist:clean(song?.subtitle || song?.artist || item?.artist || '', 120),
  }
}

export default {
  id:'jiosaavn',
  name:'JioSaavn',
  description:'Automatic fallback using the currently verified Saavn catalog/media flow.',
  fallbackOrder:10,
  brandAliases:['Saavn'],

  async run({ action, query, item, track, context }) {
    if (action === 'search') return { items:await searchSaavn(clean(query, 180)) }
    if (action === 'download') {
      const chosen = track || item || {}
      const media = await resolveSaavn(chosen)
      return sendAudio(context, {
        url:media.url,
        mimetype:media.mimetype,
        title:media.title || chosen.title,
        artist:media.artist || chosen.artist,
        fileName:safeFileName(media.title || chosen.title, media.artist || chosen.artist, 'm4a'),
      })
    }
    throw new Error('Unsupported JioSaavn music action: ' + action)
  },

  _test:{ decryptMediaUrl },
}
