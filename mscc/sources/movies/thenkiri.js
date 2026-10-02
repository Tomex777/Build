import { browseTheNkiri, inspectTheNkiriAvailability, searchTheNkiri } from '../../providers/thenkiri.js'

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
      const availability = await inspectTheNkiriAvailability({
        title:item?.title,
        type:'movie',
      })
      if (!availability.found) throw new Error('TheNkiri has no matching movie release right now.')
      return {
        text:'TheNkiri has a matching release for ' + (item?.title || 'that movie') + '. Automated third-party file-host delivery is not enabled for this source.',
      }
    }
    throw new Error('Unsupported TheNkiri movie action: ' + action)
  },
}
