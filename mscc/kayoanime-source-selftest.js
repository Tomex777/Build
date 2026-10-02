import source from './sources/anime/kayoanime.js'
import animePahe from './sources/anime/animepahe.js'
import animeSogo from './sources/anime/animesogo.js'
import animeOnsen from './sources/anime/animeonsen.js'

const listing = `
<html><body>
<article><h2><a href="https://kayoanime.com/bleach/">Bleach</a></h2></article>
<article><h2><a href="https://kayoanime.com/naruto/">Naruto</a></h2></article>
</body></html>`

const parsed = source._test.parseListing(listing, 'Bleach')
if (parsed.length !== 2 || parsed[0].title !== 'Bleach') {
  throw new Error('KayoAnime listing parser regression')
}

if (source._test.extractDriveFolderId('https://drive.google.com/drive/folders/1AbCdEfGhIjKlMn') !== '1AbCdEfGhIjKlMn') {
  throw new Error('KayoAnime Drive folder ID parser regression')
}
if (source._test.extractDriveFileId('https://drive.google.com/file/d/1AbCdEfGhIjKlMn/view') !== '1AbCdEfGhIjKlMn') {
  throw new Error('KayoAnime Drive file ID parser regression')
}
if (source._test.episodeNumber('Bleach Episode 8 1080p.mkv') !== 8) {
  throw new Error('KayoAnime episode number parser regression')
}

const encoded = source._test.encodeEpisode({ id:'1AbCdEfGhIjKlMn', name:'Bleach Episode 8 1080p.mkv' })
const decoded = source._test.decodeEpisode(encoded)
if (decoded?.id !== '1AbCdEfGhIjKlMn' || decoded?.extension !== 'mkv') {
  throw new Error('KayoAnime episode state codec regression')
}

const descriptor = source._probe.mediaDescriptor(encoded)
if (descriptor?.url !== 'https://drive.google.com/uc?export=download&id=1AbCdEfGhIjKlMn') {
  throw new Error('KayoAnime media descriptor URL regression')
}

const confirm = source._test.parseDriveConfirmation(`
<form action="https://drive.usercontent.google.com/download">
  <input type="hidden" name="id" value="1AbCdEfGhIjKlMn">
  <input type="hidden" name="export" value="download">
  <input type="hidden" name="confirm" value="t">
  <input type="hidden" name="uuid" value="abc123">
</form>
`, 'https://drive.google.com/uc?export=download&id=1AbCdEfGhIjKlMn')
const confirmUrl = new URL(confirm)
if (confirmUrl.hostname !== 'drive.usercontent.google.com' || confirmUrl.searchParams.get('confirm') !== 't') {
  throw new Error('KayoAnime Google Drive confirmation handoff regression')
}


if ([source, animePahe, animeSogo, animeOnsen].map(row => row.fallbackOrder).join('|') !== '10|20|30|40') {
  throw new Error('Anime fallback order must remain KayoAnime, AnimePahe, AnimeSogo, AnimeOnsen')
}

const paheSearch = animePahe._test.parseSearch({
  data:[{ session:'bleach-session', title:'Bleach', type:'TV', year:2026 }],
}, 'https://animepahe.pw')
if (paheSearch.length !== 1 || paheSearch[0].title !== 'Bleach' || !paheSearch[0].id.includes('animepahe.pw')) {
  throw new Error('AnimePahe search parser regression')
}
const paheSources = animePahe._test.parseSources(`
<div id="resolutionMenu">
  <button data-src="https://kwik.cx/e/abc123" data-resolution="1080" data-audio="jpn"></button>
  <button data-src="https://kwik.si/e/def456" data-resolution="720" data-audio="eng"></button>
</div>`)
if (paheSources.length !== 2 || paheSources[0].quality !== '1080' || paheSources[1].audio !== 'eng') {
  throw new Error('AnimePahe source parser regression')
}
if (animePahe._test.mediaFromText("src='https://cdn.example.test/master.m3u8'") !== 'https://cdn.example.test/master.m3u8') {
  throw new Error('AnimePahe media URL parser regression')
}

if (animeSogo._test.vrfEncrypt('Bleach') !== 'cE9mUTd5bkRnWVZNellkTQ%3D%3D') {
  throw new Error('AnimeSogo VRF regression')
}
const sogoSearch = animeSogo._test.parseSearch(`
<a class="name" href="/watch/bleach-123">Bleach</a>
`)
if (sogoSearch.length !== 1 || !sogoSearch[0].id.includes('/watch/bleach-123')) {
  throw new Error('AnimeSogo search parser regression')
}
const sogoEpisodes = animeSogo._test.parseEpisodes(`
<a data-num="1" data-ids="sub1,dub1">Episode 1</a>
<a data-num="2" data-ids="sub2,dub2">Episode 2</a>
`, 'bleach-123')
if (sogoEpisodes.length !== 2 || sogoEpisodes[1].number !== '2') {
  throw new Error('AnimeSogo episode parser regression')
}
const sogoServers = animeSogo._test.parseServerList(`
<a class="server" data-link-id="a">HD-2</a>
<a class="server" data-link-id="b">Server</a>
`)
if (sogoServers.length !== 2 || sogoServers[0].name !== 'HD-2') {
  throw new Error('AnimeSogo server parser regression')
}

const onsenSearch = animeOnsen._test.parseSearch({
  hits:[{ content_id:'frieren', content_title_en:'Frieren', content_title:'Sousou no Frieren' }],
})
if (onsenSearch.length !== 1 || onsenSearch[0].title !== 'Frieren') {
  throw new Error('AnimeOnsen search parser regression')
}
if (animeOnsen._test.titleOf({ content_title_en:'Frieren', content_title:'Sousou no Frieren' }) !== 'Frieren') {
  throw new Error('AnimeOnsen English-title regression')
}

console.log('PASS anime source adapters: KayoAnime | AnimePahe | AnimeSogo | AnimeOnsen')
