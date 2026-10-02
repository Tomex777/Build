import source from './sources/anime/kayoanime.js'

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

console.log('PASS KayoAnime source adapter')
