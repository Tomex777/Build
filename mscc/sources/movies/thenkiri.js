import { browseTheNkiri, materializeTheNkiriRelease, searchTheNkiri } from '../../providers/thenkiri.js'

export default {
  id:'thenkiri',
  name:'TheNkiri',
  description:'TheNkiri movie catalog and release availability.',
  fallbackOrder:30,

  async run({ action, query, item }) {
    if (action === 'search') return { items:await searchTheNkiri(query, 'movie') }
    if (action === 'browse') return { items:await browseTheNkiri('movie') }
    if (action === 'options') {
      return { qualities:['source'], deliveries:['document'] }
    }
    if (action === 'download') {
      const media = await materializeTheNkiriRelease({ item, title:item?.title, type:'movie' })
      try {
        const title = String(item?.title || 'movie')
          .replace(/[\\/:*?"<>|]+/g, ' ')
          .replace(/\s+/g, ' ')
          .trim()
          .slice(0,120) || 'movie'
        await context.send({
          document:{ url:media.path },
          mimetype:'video/x-matroska',
          fileName:title + '.mkv',
          caption:item?.title || undefined,
        })
        return { delivered:true }
      } finally {
        await media.cleanup()
      }
    }
    throw new Error('Unsupported TheNkiri movie action: ' + action)
  },
}
