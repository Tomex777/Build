import { clean, fetchJson, safeFileName, sendAudio } from './_shared.js'

const API = 'https://commons.wikimedia.org/w/api.php'

function metaValue(meta, key) {
  const value = meta?.[key]
  return clean(typeof value === 'object' ? value?.value : value || '', 300)
}

async function searchCommons(query) {
  const params = new URLSearchParams({
    action:'query',
    generator:'search',
    gsrsearch:query + ' audio',
    gsrnamespace:'6',
    gsrlimit:'50',
    prop:'imageinfo',
    iiprop:'url|mime|size|extmetadata',
    format:'json',
    formatversion:'2',
    origin:'*',
  })
  const { data } = await fetchJson(API + '?' + params)
  const pages = Array.isArray(data?.query?.pages) ? data.query.pages : []
  const items = []
  for (const page of pages) {
    const info = Array.isArray(page?.imageinfo) ? page.imageinfo[0] : null
    const mime = String(info?.mime || '')
    const url = String(info?.url || '')
    if (!url || !mime.startsWith('audio/')) continue
    const meta = info?.extmetadata || {}
    items.push({
      id:String(page?.pageid || url),
      title:clean(String(page?.title || '').replace(/^File:/i, ''), 140),
      artist:metaValue(meta, 'Artist') || metaValue(meta, 'Credit'),
      url,
      mimetype:mime,
      license:metaValue(meta, 'LicenseShortName'),
      licenseUrl:metaValue(meta, 'LicenseUrl'),
      description:metaValue(meta, 'ImageDescription'),
    })
    if (items.length >= 25) break
  }
  if (!items.length) throw new Error('Wikimedia Commons returned no audio results.')
  return items
}

export default {
  id:'wikimedia',
  name:'Wikimedia Commons',
  description:'Open-license Commons audio fallback with attribution metadata.',
  fallbackOrder:60,
  brandAliases:['Wikimedia'],

  async run({ action, query, item, track, context }) {
    if (action === 'search') return { items:await searchCommons(clean(query, 160)) }
    if (action === 'download') {
      const chosen = track || item || {}
      const url = String(chosen?.url || '').trim()
      if (!url) throw new Error('Wikimedia media URL is missing.')
      const extension = new URL(url).pathname.split('.').pop() || 'ogg'
      const sent = await sendAudio(context, {
        url,
        mimetype:chosen.mimetype || 'audio/ogg',
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
    throw new Error('Unsupported Wikimedia music action: ' + action)
  },
}
