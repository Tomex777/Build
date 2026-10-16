import {
  browseTheNkiri,
  resolveTheNkiriDownload,
  resolveTheNkiriRelease,
} from './providers/thenkiri.js'
import { inspectInput } from './providers/stream-copy.js'

async function firstLiveRelease(rows, type) {
  for (const item of rows.slice(0, 10)) {
    try {
      const release = await resolveTheNkiriRelease({ item, type })
      if (release.found && release.links.length) {
        return {
          item,
          summary:{
            id:item.id,
            title:item.title,
            sourcePostId:release.item?.sourcePostId || 0,
            linkCount:release.links.length,
            hosts:[...new Set(release.links.map(link => link.host))],
            sampleFile:release.links[0]?.fileName || '',
          },
        }
      }
    } catch {}
  }
  return null
}

async function provePlayableDownload(rows, { type, season = 0, episode = 0 }) {
  let lastError
  for (const item of rows.slice(0, 10)) {
    try {
      const resolved = await resolveTheNkiriDownload({
        item,
        title:item?.title,
        type,
        season,
        episode,
      })
      const inspected = await inspectInput(resolved.candidate.url, resolved.candidate.headers)
      if (!inspected.playable) throw new Error('Resolved release has no playable video stream.')
      return {
        id:item.id,
        title:item.title,
        sourcePostId:resolved.release.item?.sourcePostId || 0,
        host:resolved.link?.host || '',
        fileName:resolved.candidate.fileName || '',
        maxHeight:inspected.maxHeight,
        video:inspected.playable,
      }
    } catch (error) {
      lastError = error
    }
  }
  throw new Error(
    'TheNkiri ' + type + ' Downloadwella resolution/media probe failed: ' +
    String(lastError?.message || 'no candidate resolved'),
  )
}

const movies = await browseTheNkiri('movie')
const tv = await browseTheNkiri('tv')
if (!movies.length && !tv.length) {
  throw new Error('TheNkiri catalog returned no usable Movies/TV rows')
}

const movieRelease = await firstLiveRelease(movies, 'movie')
const tvRelease = await firstLiveRelease(tv, 'tv')
if (!movieRelease && !tvRelease) {
  throw new Error('TheNkiri catalog is live but no known movie/TV release links were resolved from current posts')
}

// A listing/link-only pass is not enough: verify the existing Downloadwella
// resolver returns a stream URL that FFprobe can actually read. We do not copy
// or store the full media in CI; FFprobe only inspects the remote stream.
const moviePlayable = await provePlayableDownload(movies, { type:'movie' })
const tvPlayable = await provePlayableDownload(tv, { type:'tv', season:1, episode:1 })

const summary = {
  movieCount:movies.length,
  tvCount:tv.length,
  movieRelease:movieRelease?.summary || null,
  tvRelease:tvRelease?.summary || null,
  moviePlayable,
  tvPlayable,
  complete:Boolean(moviePlayable.video && tvPlayable.video),
}
console.log(JSON.stringify(summary,null,2))
if (!summary.complete) process.exitCode = 1
