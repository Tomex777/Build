import { clean, fetchJson, mimeForUrl, safeFileName, sendAudio } from './_shared.js'

const API = 'https://api.openverse.org/v1/audio/'

async function searchOpenverse(query) {
  const { data } = await fetchJson(API + '?q=' + encodeURIComponent(query) + '&page_size=25')
  const rows = Array.isArray(data?.results) ? data.results : []
  const items = rows.flatMap(row => {
    const url = String(row?.url || '').trim()
    const title = clean(row?.title || '', 140)
    if (!url || !title) return []
    return [{
      id:String(row?.id || url),
      title,
      artist:clean(row?.creator || '', 120),
      url,
      license:clean(row?.license || '', 80),
      licenseUrl:clean(row?.license_url || '', 300),
      provider:clean(row?.provider || row?.source || '', 80),
      filetype:clean(row?.filetype || '', 20),
    }]
  })
  if (!items.length) throw new Error('Openverse returned no audio results.')
  return items
}

export default {
  id:'openverse',
  name:'Openverse',
  description:'Open-license audio fallback with preserved license metadata.',
  fallbackOrder:50,

  async run({ action, query, item, track, context }) {
    if (action === 'search') return { items:await searchOpenverse(clean(query, 180)) }
    if (action === 'download') {
      const chosen = track || item || {}
      const url = String(chosen?.url || '').trim()
      if (!url) throw new Error('Openverse media URL is missing.')
      const extension = chosen.filetype || new URL(url).pathname.split('.').pop() || 'mp3'
      const sent = await sendAudio(context, {
        url,
        mimetype:mimeForUrl(url),
        title:chosen.title,
        artist:chosen.artist,
        fileName:safeFileName(chosen.title, chosen.artist, extension),
      })
      if (chosen.license && typeof context?.reply === 'function') {
        const bits = [chosen.artist ? 'Creator: ' + chosen.artist : '', 'License: ' + chosen.license].filter(Boolean)
        if (chosen.licenseUrl) bits.push(chosen.licenseUrl)
        await context.reply(bits.join('\n'))
      }
      return sent
    }
    throw new Error('Unsupported Openverse music action: ' + action)
  },
}
