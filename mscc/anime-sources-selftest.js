import { readdir } from 'node:fs/promises'
import animeOnsen from './sources/anime/animeonsen.js'
import animePahe from './sources/anime/animepahe.js'
import animeSogo from './sources/anime/animesogo.js'
import kayoAnime from './sources/anime/kayoanime.js'
import nyaa from './sources/anime/nyaa.js'

const expectedOrder = [
  ['kayoanime', 10],
  ['animepahe', 20],
  ['animesogo', 30],
  ['animeonsen', 40],
  ['nyaa', 50],
]
const sources = [kayoAnime, animePahe, animeSogo, animeOnsen, nyaa]
for (const [id, fallbackOrder] of expectedOrder) {
  const source = sources.find(row => row.id === id)
  if (!source) throw new Error('Missing anime source: ' + id)
  if (source.fallbackOrder !== fallbackOrder) {
    throw new Error(id + ' fallback order changed: ' + source.fallbackOrder)
  }
  if (typeof source.run !== 'function') throw new Error(id + ' does not expose run()')
}

const files = (await readdir(new URL('./sources/anime/', import.meta.url)))
  .filter(name => name.endsWith('.js') && !name.startsWith('_'))
  .sort()
const installed = sources.map(source => source.id).sort()
for (const id of installed) {
  const expectedFile = id + '.js'
  if (!files.includes(expectedFile)) throw new Error('Anime source file is not installed: ' + expectedFile)
}

const paheSearch = animePahe._test.parseSearch({
  data:[
    { id:11, session:'bleach-session', title:'Bleach', type:'TV', year:2004 },
  ],
}, 'https://animepahe.com')
if (paheSearch[0]?.id !== 'https://animepahe.com/anime/bleach-session') {
  throw new Error('AnimePahe search normalization failed')
}
const paheSources = animePahe._test.parseSources(`
<button data-src="https://kwik.si/e/abc123" data-fansub="SubsPlease" data-resolution="1080" data-audio="jpn">1080p</button>
<button data-src="https://kwik.si/e/def456" data-fansub="SubsPlease" data-resolution="720" data-audio="jpn">720p</button>
`)
if (paheSources.length !== 2 || animePahe._test.pickSource(paheSources, '720')?.quality !== '720') {
  throw new Error('AnimePahe quality source parsing failed')
}
const paheEpisode = animePahe._test.episodeId('anime-session', 'episode-session', '8')
const decodedPahe = animePahe._test.decodeEpisode(paheEpisode)
if (decodedPahe?.anime !== 'anime-session' || decodedPahe?.episode !== 'episode-session' || decodedPahe?.number !== '8') {
  throw new Error('AnimePahe episode state codec failed')
}
if (animePahe._test.mediaFromText('file: "https://cdn.example/test/master.m3u8?token=ok"') !== 'https://cdn.example/test/master.m3u8?token=ok') {
  throw new Error('AnimePahe media URL extraction failed')
}
const paheSearchUrl = animePahe._test.searchUrl('https://animepahe.pw', 'Bleach')
if (paheSearchUrl !== 'https://animepahe.pw/api?m=search&q=Bleach') {
  throw new Error('AnimePahe search request drifted from the proven q=<query> form: ' + paheSearchUrl)
}
const realPahePlay = '<html><head><title>Bleach Ep. 1 :: animepahe</title><script src="/cdn-cgi/challenge-platform/x.js"></script></head><body><button data-src="https://kwik.cx/e/abc"></button></body></html>'
if (animePahe._test.looksBlocked(200, realPahePlay)) {
  throw new Error('AnimePahe real play page was falsely classified as Cloudflare')
}
if (!animePahe._test.looksBlocked(403, '<title>Attention Required! | Cloudflare</title>')) {
  throw new Error('AnimePahe Cloudflare block detector missed a real challenge')
}
const isolatedProxy = animePahe._test.proxyWithAuth('socks5://mscc-tor:9050', 'routeA', 'routeB')
if (!isolatedProxy.includes('routeA:routeB@mscc-tor:9050')) {
  throw new Error('AnimePahe Tor isolation credentials were not attached to the proxy')
}

const onsenSearch = animeOnsen._test.parseSearch({
  result:[{ content_id:'123', content_title_en:'Frieren', type:'TV' }],
})
if (onsenSearch[0]?.id !== '123' || onsenSearch[0]?.title !== 'Frieren') {
  throw new Error('AnimeOnsen search normalization failed')
}
const onsenEpisodes = animeOnsen._test.parseEpisodes({
  2:{ contentTitle_episode_en:'Second' },
  1:{ contentTitle_episode_en:'First' },
}, '123')
if (onsenEpisodes.length !== 2 || onsenEpisodes[0]?.number !== '1') {
  throw new Error('AnimeOnsen episode parsing/sorting failed')
}
if (animeOnsen._test.decodeEpisode(onsenEpisodes[0].id)?.contentId !== '123') {
  throw new Error('AnimeOnsen episode state codec failed')
}
const onsenJwt = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtc2NjIn0.signature'
const onsenCookie = encodeURIComponent(Buffer.from(
  [...onsenJwt].map(character => String.fromCharCode(character.charCodeAt(0) - 1)).join(''),
  'latin1',
).toString('base64'))
if (animeOnsen._test.decodeSessionToken(onsenCookie) !== onsenJwt) {
  throw new Error('AnimeOnsen ao.session token decoding failed')
}
const onsenSearchToken = '0123456789abcdef0123456789abcdef'
if (animeOnsen._test.searchTokenFromHtml(
  '<meta name="ao-search-token" content="' + onsenSearchToken + '">'
) !== onsenSearchToken) {
  throw new Error('AnimeOnsen browser search token parsing failed')
}

const sogoSearch = animeSogo._test.parseSearch(`
<div class="ani items">
  <div class="item"><a class="name d-title" href="/watch/one-piece-123">One Piece</a></div>
  <div class="item"><a class="name d-title" href="/watch/bleach-456">Bleach</a></div>
</div>
`)
if (sogoSearch.length !== 2 || sogoSearch[0]?.title !== 'One Piece') {
  throw new Error('AnimeSogo search parser failed')
}
if (animeSogo._test.parseAnimeId('<main id="watch-main" data-id="9911"></main>') !== '9911') {
  throw new Error('AnimeSogo anime ID parser failed')
}
const sogoEpisodes = animeSogo._test.parseEpisodes(`
<ul class="episodes">
  <li><a data-num="2" data-ids="b2" data-mal="21">Episode 2</a></li>
  <li><a data-num="1" data-ids="a1" data-mal="21">Episode 1</a></li>
</ul>
`, 'one-piece-123')
if (sogoEpisodes.length !== 2 || sogoEpisodes[0]?.number !== '1') {
  throw new Error('AnimeSogo episode parser failed')
}
const decodedSogo = animeSogo._test.decodeEpisode(sogoEpisodes[0].id)
if (decodedSogo?.serverIds !== 'a1' || decodedSogo?.slug !== 'one-piece-123') {
  throw new Error('AnimeSogo episode state codec failed')
}
const servers = animeSogo._test.parseServerList(`
<div class="type">
  <a class="server" data-link-id="x3"><span>Kiwi-Stream</span></a>
  <a class="server" data-link-id="x1"><span>HD-1</span></a>
</div>
`)
if (servers[0]?.id !== 'x1') throw new Error('AnimeSogo server priority parser failed')



const nyaaHtml = nyaa._test.parseNyaaHtml(`
<table><tbody><tr>
<td><a title="Anime - English-translated"></a></td>
<td><a href="/view/1234567">[Judas] Example Show - 08 [720p][HEVC][Multi-Subs]</a></td>
<td><a href="/download/1234567.torrent">torrent</a><a href="magnet:?xt=urn:btih:abc123">magnet</a></td>
<td>104.7 MiB</td><td data-timestamp="1790956800"></td><td>18</td><td>2</td><td>200</td>
</tr></tbody></table>
`)
if (nyaaHtml.length !== 1 || nyaaHtml[0]?.id !== '1234567' || nyaaHtml[0]?.seeders !== 18) {
  throw new Error('Nyaa HTML parser failed')
}
const nyaaRss = nyaa._test.parseNyaaRss(`<?xml version="1.0"?>
<rss xmlns:nyaa="https://nyaa.si/xmlns/nyaa"><channel><item>
<title>[ASW] Example Show - 08 [1080p HEVC]</title>
<guid>https://nyaa.si/view/7654321</guid>
<pubDate>Fri, 02 Oct 2026 12:00:00 +0000</pubDate>
<nyaa:infoHash>def456</nyaa:infoHash>
<nyaa:category>Anime - English-translated</nyaa:category>
<nyaa:size>188.0 MiB</nyaa:size>
<nyaa:seeders>25</nyaa:seeders><nyaa:leechers>1</nyaa:leechers><nyaa:downloads>350</nyaa:downloads>
</item></channel></rss>`)
if (nyaaRss.length !== 1 || nyaaRss[0]?.id !== '7654321' || !nyaaRss[0]?.magnet.includes('def456')) {
  throw new Error('Nyaa RSS parser failed')
}
if (!nyaa._test.nyaaSearchUrl('https://nyaa.si', 'Example Show', { page:2, sort:'seeders' }).includes('s=seeders')) {
  throw new Error('Nyaa search URL builder failed')
}

const compact720 = nyaa._test.normalizeRelease({
  id:1,
  name:'[Judas] Example Show - 08 [720p][HEVC x265][Multi-Subs]',
  size:'104.7 MiB',
  seeders:18,
  downloads:200,
  magnet:'magnet:?xt=urn:btih:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
  category:'Anime - English-translated',
})
const huge720 = nyaa._test.normalizeRelease({
  id:2,
  name:'[Yameii] Example Show S01E08 [English Dub] [CR WEB-DL 720p AVC]',
  size:'708.0 MiB',
  seeders:80,
  downloads:500,
  magnet:'magnet:?xt=urn:btih:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
  category:'Anime - English-translated',
})
if (compact720.quality !== '720' || compact720.codec !== 'hevc' || compact720.episodes[0] !== '8') {
  throw new Error('Nyaa release metadata parsing failed')
}
if (nyaa._test.rankReleases([huge720, compact720], {
  query:'Example Show',
  episode:'8',
  quality:'720',
})[0]?.id !== '1') {
  throw new Error('Nyaa compact-release ranking failed')
}
const nyaaEpisode = nyaa._test.encodeEpisode({ query:'Example Show', title:'Example Show', number:'8' })
if (nyaa._test.decodeEpisode(nyaaEpisode)?.number !== '8') {
  throw new Error('Nyaa episode state codec failed')
}
if (nyaa._test.episodeNumbers('[Group] Example Show S02E03 [1080p AV1]').join('|') !== '3') {
  throw new Error('Nyaa SxxExx episode parser failed')
}

console.log('PASS anime sources: ' + expectedOrder.map(([id]) => id).join(' -> '))
