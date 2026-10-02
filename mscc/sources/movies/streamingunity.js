import {
  browseTitles,
  materializeStream,
  qualityList,
  resolveStream,
  searchTitles,
} from '../../providers/streamingunity.js'

function cleanName(value) {
  return String(value || 'movie')
    .replace(/[\\/:*?"<>|]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0,120) || 'movie'
}

async function deliver(item, quality, delivery, context) {
  const stream = await resolveStream(item)
  const media = await materializeStream(stream, quality, delivery)
  try {
    const title = cleanName(item?.title)
    if (delivery === 'video') {
      await context.send({
        video:{ url:media.path },
        mimetype:'video/mp4',
        caption:item?.title || undefined,
      })
    } else {
      await context.send({
        document:{ url:media.path },
        mimetype:'video/x-matroska',
        fileName:title + '.mkv',
        caption:item?.title || undefined,
      })
    }
    return { delivered:true }
  } finally {
    await media.cleanup()
  }
}

export default {
  id:'streamingunity',
  name:'StreamingUnity',
  description:'Movies resolved to live HLS and delivered without video re-encoding.',
  fallbackOrder:10,
  brandAliases:['StreamingCommunity'],

  async run({ action, query, item, quality = 'source', delivery = 'document', context }) {
    if (action === 'search') {
      return { items:await searchTitles(query, 'movie') }
    }
    if (action === 'browse') {
      return { items:await browseTitles('movie') }
    }
    if (action === 'options') {
      const stream = await resolveStream(item)
      return {
        qualities:qualityList(stream),
        deliveries:['document','video'],
      }
    }
    if (action === 'download') {
      return deliver(item, quality, delivery, context)
    }
    throw new Error('Unsupported StreamingUnity movie action: ' + action)
  },
}
