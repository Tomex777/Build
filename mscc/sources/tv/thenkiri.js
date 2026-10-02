import { browseTheNkiri, inspectTheNkiriAvailability, searchTheNkiri } from '../../providers/thenkiri.js'
import { episodeRows, seasonRows } from '../../providers/screen-source-metadata.js'

export default {
  id:'thenkiri',
  name:'TheNkiri',
  description:'TheNkiri TV catalog and release availability.',
  fallbackOrder:30,

  async run({
    action, query, item, season, seasonNumber, episode, episodeId, selection, range, context,
  }) {
    if (action === 'search') return { items:await searchTheNkiri(query, 'tv') }
    if (action === 'browse') return { items:await browseTheNkiri('tv') }
    if (action === 'seasons') return { seasons:await seasonRows(context, item) }
    if (action === 'episodes') {
      const number = Number(seasonNumber || season?.number || 1)
      return { title:item?.title, seasonNumber:number, episodes:await episodeRows(context, item, number) }
    }
    if (action === 'options') {
      return { qualities:['source'], deliveries:['document'] }
    }
    if (action === 'download') {
      const s = Number(seasonNumber || season?.number || episode?.seasonNumber || 1)
      const e = Number(episode?.number || episodeId || 0)
      const availability = await inspectTheNkiriAvailability({
        title:item?.title,
        type:'tv',
        season:s,
        episode:e,
      })
      if (!availability.found) throw new Error('TheNkiri has no matching episode release right now.')
      return {
        text:'TheNkiri has a matching release for ' + (item?.title || 'that series') +
          ' S' + String(s).padStart(2, '0') + 'E' + String(e).padStart(2, '0') +
          '. Automated third-party file-host delivery is not enabled for this source.',
      }
    }
    if (action === 'downloadRange') {
      const s = Number(seasonNumber || season?.number || 1)
      const low = Number(range?.start?.number)
      const high = Number(range?.end?.number)
      if (!Number.isFinite(low) || !Number.isFinite(high)) throw new Error('TheNkiri TV range is invalid.')
      return {
        text:'TheNkiri range availability is catalog-only in this integration. Selected ' +
          (item?.title || 'series') + ' Season ' + s + ', episodes ' + low + '-' + high + '.',
      }
    }
    throw new Error('Unsupported TheNkiri TV action: ' + action)
  },
}
