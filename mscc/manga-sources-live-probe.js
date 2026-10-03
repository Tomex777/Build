import AdmZip from 'adm-zip'
import { readFile, stat } from 'node:fs/promises'
import mangaDex from './sources/manga/mangadex.js'
import eighteenKami from './sources/manga/18kami.js'
import comix from './sources/manga/comix.js'
import kagane from './sources/manga/kagane.js'
import mangaFox from './sources/manga/mangafox.js'
import mangaK from './sources/manga/mangak.js'
import mangaFire from './sources/manga/mangafire.js'
import mangaHub from './sources/manga/mangahub.js'
import mangaKakalot from './sources/manga/mangakakalot.js'
import mangaKatana from './sources/manga/mangakatana.js'
import mangaPill from './sources/manga/mangapill.js'
import mangaRead from './sources/manga/mangaread.js'
import nyora from './sources/manga/nyora.js'
import toonily from './sources/manga/toonily.js'
import usagi from './sources/manga/usagi.js'
import weebCentral from './sources/manga/weebcentral.js'

const SOURCES = {
  mangadex:mangaDex, '18kami':eighteenKami, comix, kagane, mangafox:mangaFox, mangak:mangaK,
  mangafire:mangaFire, mangahub:mangaHub, mangakakalot:mangaKakalot, mangakatana:mangaKatana,
  mangapill:mangaPill, mangaread:mangaRead, nyora, toonily, usagi, weebcentral:weebCentral,
}
const id = String(process.argv[2] || '').trim().toLowerCase()
const source = SOURCES[id]
if (!source) throw new Error('Unknown manga source probe: ' + id)
const query = process.env.MSCC_LIVE_QUERY || (id === '18kami' ? 'One Piece' : 'One Piece')

function must(value, message) { if (!value) throw new Error(message) }
const began = performance.now()
const search = await source.run({ action:'search', query, context:{} })
must(Array.isArray(search?.items) && search.items.length, source.name + ' search returned no results')
const item = search.items.find(row => /one\s*piece/i.test(String(row?.title || ''))) || search.items[0]
const listing = await source.run({ action:'chapters', item, context:{} })
must(Array.isArray(listing?.chapters) && listing.chapters.length, source.name + ' returned no chapters')
const chapter = listing.chapters.find(row => Number(row?.number) === 1) || listing.chapters.at(-1) || listing.chapters[0]

let proof = null
const result = await source.run({
  action:'download',
  item,
  chapter,
  chapterId:chapter.id,
  quality:'source',
  delivery:'document',
  context:{
    send:async payload => {
      const target = payload?.document?.url || payload?.document
      must(target, source.name + ' sent no CBZ path')
      const info = await stat(target)
      must(info.size > 1000, source.name + ' CBZ is unexpectedly small')
      const bytes = await readFile(target)
      const zip = new AdmZip(bytes)
      const entries = zip.getEntries().filter(row => !row.isDirectory)
      must(entries.length > 0, source.name + ' CBZ contains no pages')
      proof = { bytes:info.size, pages:entries.length, first:entries[0].entryName }
    },
  },
})
must(result?.delivered === true, source.name + ' download route did not report delivery')
must(proof?.pages > 0, source.name + ' CBZ was not inspected')
console.log(JSON.stringify({
  source:source.name,
  query,
  resultCount:search.items.length,
  selectedTitle:item.title,
  chapterCount:listing.chapters.length,
  selectedChapter:chapter.title,
  cbz:proof,
  elapsedMs:Math.round(performance.now() - began),
  verdict:'PASS',
}, null, 2))
