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

const sent=[]
await source.run({
  action:'download',
  episodeId:encoded,
  delivery:'document',
  context:{ send:async payload => sent.push(payload) },
})
if (sent[0]?.document?.url !== 'https://drive.google.com/uc?export=download&id=1AbCdEfGhIjKlMn') {
  throw new Error('KayoAnime document delivery URL regression')
}
if (sent[0]?.fileName !== 'Bleach Episode 8 1080p.mkv') {
  throw new Error('KayoAnime document filename regression')
}

console.log('PASS KayoAnime source adapter')
