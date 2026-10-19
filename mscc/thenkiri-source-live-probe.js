import assert from 'node:assert/strict'
import {
  resolveTheNkiriDownload,
  resolveTheNkiriRelease,
  searchTheNkiri,
} from './providers/thenkiri.js'

const movieRows = await searchTheNkiri('Seriously Single Too', 'movie')
const movie = movieRows.find(row => /seriously single too/i.test(row.title))
assert.ok(movie, 'TheNkiri live movie search must find Seriously Single Too')

const movieDownload = await resolveTheNkiriDownload({
  item:movie,
  title:movie.title,
  type:'movie',
})
assert.equal(movieDownload.release.found, true, 'movie release must resolve')
assert.equal(movieDownload.link.host, 'downloadwella', 'movie must select the supported Downloadwella host')
assert.match(movieDownload.candidate.url, /^https?:\/\//i, 'Downloadwella must resolve to a direct URL')
assert.notEqual(
  movieDownload.candidate.url,
  movieDownload.link.url,
  'Downloadwella must not return the HTML landing page as the direct file URL',
)

const seriesRows = await searchTheNkiri('Hollywood Arts', 'tv')
const series = seriesRows.find(row => /hollywood arts/i.test(row.title))
assert.ok(series, 'TheNkiri live TV search must find Hollywood Arts')

const episodeRelease = await resolveTheNkiriRelease({
  item:series,
  title:series.title,
  type:'tv',
  season:1,
  episode:1,
})
assert.equal(episodeRelease.found, true, 'TV season/episode 1 must resolve')
assert.ok(
  episodeRelease.links.some(link => link.season === 1 && link.episode === 1),
  'TV resolver must return the exact S01E01 release, not just the series page',
)

console.log('PASS Thenkiri live search, movie release, Downloadwella direct-link creation and S01E01 resolution')
