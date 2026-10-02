import { clean, fetchJson, mimeForUrl, safeFileName, sendAudio } from './_shared.js'

const API = 'https://archive.org'

function encodeArchivePath(name) {
  return String(name || '').split('/').map(encodeURIComponent).join('/')
}

function audioFile(files = []) {
  const playable = files.filter(file => {
    const name = String(file?.name || '').toLowerCase()
    const size = Number(file?.size || 0) || 0
    return size > 65536 && /\.(mp3|m4a|ogg|oga|flac|wav)$/i.test(name)
  })
  playable.sort((a,b) => {
    const af = String(a?.format || '')
    const bf = String(b?.format || '')
    const ap = /VBR MP3|MP3/i.test(af) ? 0 : 1
    const bp = /VBR MP3|MP3/i.test(bf) ? 0 : 1
    return ap - bp || Number(b?.size || 0) - Number(a?.size || 0)
  })
  return playable[0] || null
}

async function resolveArchiveDocs(query) {
  const q = 'mediatype:audio AND (' + query.replace(/[()"]/g, ' ') + ')'
  const url = API + '/advancedsearch.php?' + new URLSearchParams({
    q,
    'fl[]':['identifier','title','creator'],
    rows:'12',
    page:'1',
    output:'json',
  }).toString().replace(/fl%5B%5D=identifier%2Ctitle%2Ccreator/, 'fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator')
  const { data } = await fetchJson(url)
  const docs = Array.isArray(data?.response?.docs) ? data.response.docs : []
  const items = []
  for (const doc of docs.slice(0, 10)) {
    const identifier = String(doc?.identifier || '').trim()
    if (!identifier) continue
    let metadata
    try {
      metadata = (await fetchJson(API + '/metadata/' + encodeURIComponent(identifier))).data
    } catch {
      continue
    }
    const file = audioFile(Array.isArray(metadata?.files) ? metadata.files : [])
    if (!file) continue
    const title = clean(doc?.title || metadata?.metadata?.title || identifier, 140)
    const creator = Array.isArray(doc?.creator) ? doc.creator.join(', ') : doc?.creator
    const artist = clean(creator || metadata?.metadata?.creator || '', 120)
    const mediaUrl = API + '/download/' + encodeURIComponent(identifier) + '/' + encodeArchivePath(file.name)
    items.push({
      id:identifier + '::' + file.name,
      title,
      artist,
      url:mediaUrl,
      fileName:file.name,
      format:clean(file.format || '', 80),
    })
  }
  if (!items.length) throw new Error('Internet Archive returned no downloadable audio results.')
  return items
}

export default {
  id:'internet-archive',
  name:'Internet Archive',
  description:'Public archive fallback using direct audio files.',
  fallbackOrder:40,
  brandAliases:['Archive.org'],

  async run({ action, query, item, track, context }) {
    if (action === 'search') return { items:await resolveArchiveDocs(clean(query, 160)) }
    if (action === 'download') {
      const chosen = track || item || {}
      const url = String(chosen?.url || '').trim()
      if (!url) throw new Error('Internet Archive media URL is missing.')
      const mime = mimeForUrl(url)
      return sendAudio(context, {
        url,
        mimetype:mime,
        title:chosen.title,
        artist:chosen.artist,
        fileName:safeFileName(chosen.title, chosen.artist, chosen.fileName?.split('.').pop() || 'mp3'),
      })
    }
    throw new Error('Unsupported Internet Archive music action: ' + action)
  },
}
