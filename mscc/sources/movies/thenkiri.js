import { browseTheNkiri, materializeTheNkiriRelease, searchTheNkiri } from '../../providers/thenkiri.js'

function cleanName(value) {
  return String(value || 'movie')
    .replace(/[\\/:*?"<>|]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0,120) || 'movie'
}

async function deliverMovie({ item, context, materialize = materializeTheNkiriRelease }) {
  if (typeof context?.send !== 'function') {
    throw new Error('TheNkiri movie delivery requires a message context.')
  }
  const media = await materialize({ item, title:item?.title, type:'movie' })
  try {
    const title = cleanName(item?.title)
    await context.send({
      document:{ url:media.path },
      mimetype:'video/x-matroska',
      fileName:title + '.mkv',
      caption:item?.title || undefined,
    })
    return { delivered:true, fileName:title + '.mkv' }
  } finally {
    await media.cleanup()
  }
}

export default {
  id:'thenkiri',
  name:'TheNkiri',
  description:'TheNkiri movie catalog and verified Downloadwella release delivery.',
  fallbackOrder:30,

  async run({ action, query, item, context }) {
    if (action === 'search') return { items:await searchTheNkiri(query, 'movie') }
    if (action === 'browse') return { items:await browseTheNkiri('movie') }
    if (action === 'options') {
      return { qualities:['source'], deliveries:['document'] }
    }
    if (action === 'download') return deliverMovie({ item, context })
    throw new Error('Unsupported TheNkiri movie action: ' + action)
  },
}

export const _test = { cleanName, deliverMovie }
