import assert from 'node:assert/strict'
import { _test } from './providers/tfpdl-catalog.js'

assert.equal(_test.yearOf('Movie Name 2026 720p WEB-DL x264-TFPDL'), 2026)
assert.equal(_test.qualityOf('Movie Name 2026 720p WEB-DL x264-TFPDL'), '720')
assert.deepEqual(_test.episodeOf('Example Show S02E03 720p WEB-DL'), { season:2, episode:3 })
assert.equal(_test.seasonOf('Example Show S02 720p'), 2)

const html = [
  '<p><a href="https://tfp.re/tfpdl?abc=1">Episode 1</a></p>',
  '<p><a href="https://example.com/other">Other</a></p>',
].join('')
assert.equal(_test.wrappersFromHtml(html).length, 1)

const exact = _test.titleScore(
  'Night of the Living Dead 1968 720p BluRay x264-TFPDL',
  'Night of the Living Dead',
  1968,
)
const wrong = _test.titleScore(
  'Erotic Nights of the Living Dead 1980 720p BluRay x264-TFPDL',
  'Night of the Living Dead',
  1968,
)
assert(exact > wrong)
assert(exact >= 1.4)
assert(wrong < 1.0)

const post = _test.normalizePost({
  id:123,
  slug:'example-show-s01e02',
  date:'2026-10-02T00:00:00',
  title:{ rendered:'Example Show S01E02 480p WEB-DL x264-TFPDL' },
  content:{ rendered:'<a href="https://tfp.re/tfpdl?x=1">Download</a>' },
}, 'Example Show', 2026)
assert.equal(post.type, 'tv')
assert.equal(post.season, 1)
assert.equal(post.episode, 2)
assert.equal(post.quality, '480')
assert.equal(post.wrapperCount, 1)

console.log('PASS TFPDL catalog/parser fixtures')
