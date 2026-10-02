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
