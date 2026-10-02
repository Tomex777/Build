import assert from 'node:assert/strict'
import { _test } from './providers/thenkiri.js'

const movie = _test.normalizePost({
  id:123,
  link:'https://thenkiri.com/example/',
  title:{ rendered:'Example Movie (2025) | Download Hollywood Movie' },
  content:{ rendered:'<p>Download now</p><a href="https://downloadwella.com/x/file.mkv.html">Download Movie</a>' },
})
assert.equal(movie.id, 'thenkiri:123')
assert.equal(movie.type, 'movie')
assert.equal(movie.year, 2025)
assert.equal(movie.available, true)

const tv = _test.normalizePost({
  id:456,
  link:'https://thenkiri.com/show-s01/',
  title:{ rendered:'Example Show S01 (Complete) | Foreign TV Series' },
  content:{ rendered:'<a href="https://downloadwella.com/x/episode.mkv.html">Download Episode</a>' },
})
assert.equal(tv.type, 'tv')
assert.equal(tv.available, true)
assert.equal(_test.extractYear('Movie (1968)'), 1968)
assert.equal(_test.inferType('Show S02E03', ''), 'tv')

console.log('PASS TheNkiri catalog/source fixtures')
