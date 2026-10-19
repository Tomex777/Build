import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { _test } from './providers/thenkiri.js'
import movieSource, { _test as movieDeliveryTest } from './sources/movies/thenkiri.js'
import tvSource, { _test as tvDeliveryTest } from './sources/tv/thenkiri.js'

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
assert.equal(tv.title, 'Example Show')
assert.equal(tv.seasonNumber, 1)
assert.equal(tv.description, 'S01 • TV')
assert.equal(tv.available, true)
assert.equal(tv.releaseLinks.length, 2)
assert.equal(tv.releaseLinks[0].season, 1)
assert.equal(tv.releaseLinks[0].episode, 1)
assert.equal(tv.releaseLinks[1].host, 'wetafiles')
assert.equal(tv.releaseLinks[1].episode, 2)

const sourceSeasons = _test.seasonsFromRelease(tv)
assert.deepEqual(sourceSeasons, [{
  id:'1',
  number:1,
  title:'Season 1',
  episodeCount:2,
}])
const sourceEpisodes = _test.episodesFromRelease(tv, 1)
assert.deepEqual(sourceEpisodes, [
  { id:'1', number:'1', seasonNumber:1, title:'Episode 1' },
  { id:'2', number:'2', seasonNumber:1, title:'Episode 2' },
])
assert.deepEqual(_test.episodesFromRelease(tv, 2), [])
assert.deepEqual(_test.episodesFromRelease({
  seasonNumber:0,
  releaseLinks:[{ season:0, episode:1 }],
}, 1), [], 'unknown-season releases must not be shown inside every season')

const exactEpisode = _test.matchingLinks(tv.releaseLinks, { type:'tv', season:1, episode:2 })
assert.equal(exactEpisode.length, 1)
assert.equal(exactEpisode[0].host, 'wetafiles')
assert.equal(_test.matchingLinks(tv.releaseLinks, { type:'tv', season:1, episode:3 }).length, 0)
assert.equal(_test.extractYear('Movie (1968)'), 1968)
assert.equal(_test.inferType('Show S02E03', ''), 'tv')
assert.equal(_test.cleanSeriesTitle('Example Show S02 (Episode 3 Added) | TV Series'), 'Example Show')
assert.equal(_test.cleanTitle('Example Movie (2025) | Download Hollywood Movie'), 'Example Movie (2025)')
assert.equal(_test.hostKind('https://www.downloadwella.com/abc/file.mkv.html'), 'downloadwella')
assert.equal(_test.hostKind('https://wetafiles.com/abc/file.mkv.html'), 'wetafiles')
assert.equal(_test.hostKind('https://example.com/file.mkv'), '')

// HTML fallback fixtures mirror the live site's h2 > a search cards when the
// WordPress REST endpoint is unavailable or returns no posts.
const listingHtml = [
  '<article id="post-101" class="post"><h2><a href="https://thenkiri.com/seriously-single-too-2026-download-movie/" title="Download Seriously Single Too (2026) | Download Movie">Seriously Single Too (2026) | Download Movie</a></h2></article>',
  '<article id="post-202" class="post"><h2><a href="https://thenkiri.com/hollywood-arts-s01-complete-tv-series/" title="Hollywood Arts S01 (Episode 1 – 12 Added) | TV Series">Hollywood Arts S01 (Episode 1 – 12 Added) | TV Series</a></h2></article>',
  '<h2><a href="https://thenkiri.com/tv-series/">TV Series</a></h2>',
].join('')
const scraped = _test.parseHtmlListing(listingHtml, 'https://thenkiri.com/?s=sample', 20)
assert.equal(scraped.length, 2, 'listing fallback ignores navigation/category links')
assert.equal(scraped[0].id, 'thenkiri:seriously-single-too-2026-download-movie')
assert.equal(scraped[0].sourcePostId, 101)
assert.equal(scraped[0].type, 'movie')
assert.equal(scraped[0].title, 'Seriously Single Too (2026)')
assert.equal(scraped[1].id, 'thenkiri:hollywood-arts-s01-complete-tv-series')
assert.equal(scraped[1].sourcePostId, 202)
assert.equal(scraped[1].type, 'tv')
assert.equal(scraped[1].title, 'Hollywood Arts')

const detailHtml = [
  '<html><head><title>DOWNLOAD Example Show S01 (Complete) | TV Series - TheNkiri.com</title></head><body>',
  '<article id="post-303"><h1>DOWNLOAD Example Show S01 (Complete) | TV Series - TheNkiri.com</h1>',
  '<a href="https://downloadwella.com/a/Example.Show.S01E01.%28THENKIRI.COM%29.mkv.html">Download Episode</a>',
  '</article></body></html>',
].join('')
const htmlPost = _test.normalizeHtmlPost(detailHtml, 'https://thenkiri.com/example-show-s01-complete-tv-series/')
assert.equal(htmlPost.type, 'tv')
assert.equal(htmlPost.title, 'Example Show')
assert.equal(htmlPost.sourcePostId, 303)
assert.equal(htmlPost.releaseLinks.length, 1)
assert.equal(htmlPost.releaseLinks[0].episode, 1)

// Downloadwella's current free-download form returns an anchor containing
// a button with class=downloadbtn, not the old "Start Download" anchor.
const downloadwellaHtml = [
  '<form action="/ekwhijx6ax8p/example.mkv.html" method="POST">',
  '<input type="hidden" name="op" value="download2">',
  '<input type="hidden" name="id" value="abc123">',
  '<input type="hidden" name="rand" value="xyz987">',
  '<input type="hidden" name="referer" value="">',
  '<input type="hidden" name="method_free" value="Free Download">',
  '<button type="submit">Create download link</button></form>',
].join('')
const downloadForm = _test.downloadwellaForm(downloadwellaHtml, 'https://downloadwella.com/file.html')
assert.ok(downloadForm)
assert.equal(downloadForm.action, 'https://downloadwella.com/ekwhijx6ax8p/example.mkv.html')
assert.equal(downloadForm.fields.op, 'download2')
assert.equal(downloadForm.fields.id, 'abc123')
assert.equal(downloadForm.fields.rand, 'xyz987')
assert.equal(downloadForm.fields.method_free, 'Free Download')
assert.equal(
  _test.startDownloadUrl(
    '<a href="/d/generated-token"><button class="btn downloadbtn">Download</button></a>',
    'https://downloadwella.com/ekwhijx6ax8p/example.mkv.html',
  ),
  'https://downloadwella.com/d/generated-token',
)


// Runtime tests: prove both adapters pass the right media identity, send the
// resolved file through the WhatsApp context, and always clean temporary files.
assert.equal(typeof movieSource.run, 'function')
assert.equal(typeof tvSource.run, 'function')

let movieSend
let movieResolveArgs
let movieCleanupCount = 0
const movieResult = await movieDeliveryTest.deliverMovie({
  item:{ id:'thenkiri:123', title:'Example: Movie' },
  context:{ send:async payload => { movieSend = payload } },
  materialize:async input => {
    movieResolveArgs = input
    return { path:'/tmp/mscc-test-movie.mkv', cleanup:async () => { movieCleanupCount += 1 } }
  },
})
assert.deepEqual(movieResolveArgs, {
  item:{ id:'thenkiri:123', title:'Example: Movie' },
  title:'Example: Movie',
  type:'movie',
})
assert.equal(movieSend.document.url, '/tmp/mscc-test-movie.mkv')
assert.equal(movieSend.mimetype, 'video/x-matroska')
assert.equal(movieSend.fileName, 'Example Movie.mkv')
assert.equal(movieResult.delivered, true)
assert.equal(movieCleanupCount, 1)

let tvSend
let tvResolveArgs
let tvCleanupCount = 0
const tvResult = await tvDeliveryTest.deliverEpisode({
  item:{ id:'thenkiri:456', title:'Example Show' },
  context:{ send:async payload => { tvSend = payload } },
  seasonNumber:1,
  episodeNumber:2,
  materialize:async input => {
    tvResolveArgs = input
    return { path:'/tmp/mscc-test-tv.mkv', cleanup:async () => { tvCleanupCount += 1 } }
  },
})
assert.deepEqual(tvResolveArgs, {
  item:{ id:'thenkiri:456', title:'Example Show' },
  title:'Example Show',
  type:'tv',
  season:1,
  episode:2,
})
assert.equal(tvSend.document.url, '/tmp/mscc-test-tv.mkv')
assert.equal(tvSend.mimetype, 'video/x-matroska')
assert.equal(tvSend.fileName, 'Example Show.S01E02.mkv')
assert.match(tvSend.caption, /S01E02/)
assert.equal(tvResult.delivered, true)
assert.equal(tvCleanupCount, 1)

let failedMovieCleanup = 0
await assert.rejects(movieDeliveryTest.deliverMovie({
  item:{ title:'Broken send test' },
  context:{ send:async () => { throw new Error('expected mock send failure') } },
  materialize:async () => ({
    path:'/tmp/mscc-test-failed-movie.mkv',
    cleanup:async () => { failedMovieCleanup += 1 },
  }),
}), /expected mock send failure/)
assert.equal(failedMovieCleanup, 1, 'movie temp media must be cleaned even when WhatsApp delivery fails')

let failedTvCleanup = 0
await assert.rejects(tvDeliveryTest.deliverEpisode({
  item:{ title:'Broken TV send test' },
  context:{ send:async () => { throw new Error('expected mock send failure') } },
  seasonNumber:1,
  episodeNumber:1,
  materialize:async () => ({
    path:'/tmp/mscc-test-failed-tv.mkv',
    cleanup:async () => { failedTvCleanup += 1 },
  }),
}), /expected mock send failure/)
assert.equal(failedTvCleanup, 1, 'TV temp media must be cleaned even when WhatsApp delivery fails')

await assert.rejects(movieDeliveryTest.deliverMovie({
  item:{ title:'No context' },
  context:null,
  materialize:async () => { throw new Error('materialize must not run without a send context') },
}), /requires a message context/)
await assert.rejects(tvDeliveryTest.deliverEpisode({
  item:{ title:'No context' },
  context:null,
  seasonNumber:1,
  episodeNumber:1,
  materialize:async () => { throw new Error('materialize must not run without a send context') },
}), /requires a message context/)
await assert.rejects(tvDeliveryTest.deliverEpisode({
  item:{ title:'Invalid episode' },
  context:{ send:async () => {} },
  seasonNumber:0,
  episodeNumber:1,
  materialize:async () => { throw new Error('materialize must not run for invalid episode') },
}), /episode identity is incomplete/)

// Keep a lightweight source-contract guard so these regression helpers remain
// reachable from the adapter modules and range delivery does not regress back
// to the previous "catalog-only" response.
const movieAdapter = await readFile(new URL('./sources/movies/thenkiri.js', import.meta.url), 'utf8')
const tvAdapter = await readFile(new URL('./sources/tv/thenkiri.js', import.meta.url), 'utf8')
assert.match(movieAdapter, /async run\(\{[^}]*context[^}]*\}\)/s)
assert.match(movieAdapter, /return deliverMovie\(\{ item, context \}\)/)
assert.match(tvAdapter, /materializeTheNkiriRelease/)
assert.match(tvAdapter, /async function deliverEpisode/)
assert.match(tvAdapter, /action === 'downloadRange'/)
assert.match(tvAdapter, /listTheNkiriSeasons/)
assert.match(tvAdapter, /listTheNkiriEpisodes/)
assert.doesNotMatch(tvAdapter, /Automated third-party file-host delivery is not enabled/)

console.log('PASS TheNkiri catalog, movie/TV delivery, cleanup and regression fixtures')
