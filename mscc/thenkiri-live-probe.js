import { browseTheNkiri, resolveTheNkiriFile, resolveTheNkiriRelease } from './providers/thenkiri.js'

async function firstLiveRelease(rows, type) {
  for (const item of rows.slice(0, 10)) {
    try {
      const release = await resolveTheNkiriRelease({ item, type })
      if (release.found && release.links.length) {
        return {
          item,
          release,
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

async function proveDirectFile(rows) {
  const failures = []
  for (const item of rows.slice(0, 8)) {
    let release
    try {
      release = await resolveTheNkiriRelease({ item, type:'movie' })
    } catch (error) {
      failures.push('release:' + String(error?.message || error))
      continue
    }
    for (const link of release.links.slice(0, 3)) {
      try {
        const direct = await resolveTheNkiriFile(link)
        const response = await fetch(direct.url, {
          headers:{
            ...direct.headers,
            Range:'bytes=0-0',
            'User-Agent':'Mozilla/5.0 (Linux; Android 16; SM-A165F) AppleWebKit/537.36 Chrome/140.0 Mobile Safari/537.36',
          },
          redirect:'follow',
          signal:AbortSignal.timeout(30_000),
        })
        const contentType = String(response.headers.get('content-type') || '')
        const contentRange = String(response.headers.get('content-range') || '')
        const contentLength = String(response.headers.get('content-length') || '')
        await response.body?.cancel().catch(() => {})
        if (![200,206].includes(response.status)) throw new Error('direct HTTP ' + response.status)
        if (/text\/html/i.test(contentType)) throw new Error('direct response was HTML')
        return {
          title:item.title,
          host:link.host,
          fileName:link.fileName,
          status:response.status,
          contentType,
          contentRange,
          contentLength,
        }
      } catch (error) {
        failures.push(link.host + ':' + String(error?.message || error))
      }
    }
  }
  throw new Error('No current TheNkiri movie reached direct file bytes. ' + failures.slice(0, 8).join(' | '))
}

const movies = await browseTheNkiri('movie')
const tv = await browseTheNkiri('tv')
if (!movies.length && !tv.length) throw new Error('TheNkiri catalog returned no usable Movies/TV rows')

const movieRelease = await firstLiveRelease(movies, 'movie')
const tvRelease = await firstLiveRelease(tv, 'tv')
if (!movieRelease && !tvRelease) {
  throw new Error('TheNkiri catalog is live but no supported Downloadwella/WetaFiles release links were resolved from current posts')
}

const directMovie = await proveDirectFile(movies)
const summary = {
  movieCount:movies.length,
  tvCount:tv.length,
  movieRelease:movieRelease?.summary || null,
  tvRelease:tvRelease?.summary || null,
  directMovie,
}
console.log(JSON.stringify(summary,null,2))
