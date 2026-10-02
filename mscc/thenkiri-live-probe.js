import { browseTheNkiri, resolveTheNkiriRelease } from './providers/thenkiri.js'

async function firstLiveRelease(rows, type) {
  for (const item of rows.slice(0, 10)) {
    try {
      const release = await resolveTheNkiriRelease({ item, type })
      if (release.found && release.links.length) {
        return {
          id:item.id,
          title:item.title,
          sourcePostId:release.item?.sourcePostId || 0,
          linkCount:release.links.length,
          hosts:[...new Set(release.links.map(link => link.host))],
          sampleFile:release.links[0]?.fileName || '',
        }
      }
    } catch {}
  }
  return null
}

const movies = await browseTheNkiri('movie')
const tv = await browseTheNkiri('tv')
if (!movies.length && !tv.length) throw new Error('TheNkiri catalog returned no usable Movies/TV rows')

const movieRelease = await firstLiveRelease(movies, 'movie')
const tvRelease = await firstLiveRelease(tv, 'tv')
if (!movieRelease && !tvRelease) {
  throw new Error('TheNkiri catalog is live but no supported Downloadwella/WetaFiles release links were resolved from current posts')
}

const summary = {
  movieCount:movies.length,
  tvCount:tv.length,
  movieRelease,
  tvRelease,
}
console.log(JSON.stringify(summary,null,2))
