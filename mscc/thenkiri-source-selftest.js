import assert from 'node:assert/strict'
import { _test } from './providers/thenkiri.js'

const movie = _test.normalizePost({
  id:123,
  link:'https://thenkiri.com/example/',
  title:{ rendered:'Example Movie (2025) | Download Hollywood Movie' },
  content:{ rendered:'<p>Download now</p><a href="https://downloadwella.com/x/Example.Movie.%28THENKIRI.COM%29.mkv.html">Download Movie</a>' },
})
assert.equal(movie.id, 'thenkiri:123')
assert.equal(movie.type, 'movie')
assert.equal(movie.year, 2025)
assert.equal(movie.available, true)
assert.equal(movie.releaseLinks.length, 1)
assert.equal(movie.releaseLinks[0].host, 'downloadwella')
assert.equal(movie.releaseLinks[0].fileName, 'Example.Movie.(THENKIRI.COM).mkv')

const tv = _test.normalizePost({
  id:456,
  link:'https://thenkiri.com/show-s01/',
  title:{ rendered:'Example Show S01 (Complete) | Foreign TV Series' },
  content:{ rendered:[
    '<a href="https://downloadwella.com/a/Example.Show.S01E01.%28THENKIRI.COM%29.mkv.html">Download Episode 1</a>',
    '<a href="https://wetafiles.com/b/Example.Show.S01E02.%28THENKIRI.COM%29.mkv.html">Download Episode 2</a>',
    '<a href="https://example.com/ad">Not a release</a>',
  ].join('') },
})
assert.equal(tv.type, 'tv')
assert.equal(tv.available, true)
assert.equal(tv.releaseLinks.length, 2)
assert.equal(tv.releaseLinks[0].season, 1)
assert.equal(tv.releaseLinks[0].episode, 1)
assert.equal(tv.releaseLinks[1].host, 'wetafiles')
assert.equal(tv.releaseLinks[1].episode, 2)

const exactEpisode = _test.matchingLinks(tv.releaseLinks, { type:'tv', season:1, episode:2 })
assert.equal(exactEpisode.length, 1)
assert.equal(exactEpisode[0].host, 'wetafiles')
assert.equal(_test.matchingLinks(tv.releaseLinks, { type:'tv', season:1, episode:3 }).length, 0)
assert.equal(_test.extractYear('Movie (1968)'), 1968)
assert.equal(_test.inferType('Show S02E03', ''), 'tv')
assert.equal(_test.hostKind('https://www.downloadwella.com/abc/file.mkv.html'), 'downloadwella')
assert.equal(_test.hostKind('https://wetafiles.com/abc/file.mkv.html'), 'wetafiles')
assert.equal(_test.hostKind('https://example.com/file.mkv'), '')

console.log('PASS TheNkiri catalog/release-link fixtures')
