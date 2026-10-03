import { readdir } from 'node:fs/promises'
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

const sources = [
  mangaDex, eighteenKami, comix, kagane, mangaFox, mangaK, mangaFire, mangaHub,
  mangaKakalot, mangaKatana, mangaPill, mangaRead, nyora, toonily, usagi, weebCentral,
]
const expected = [
  'mangadex','18kami','comix','kagane','mangafox','mangak','mangafire','mangahub',
  'mangakakalot','mangakatana','mangapill','mangaread','nyora','toonily','usagi','weebcentral',
]

if (sources.length !== expected.length) throw new Error('Manga source count mismatch')
for (const id of expected) {
  const source = sources.find(row => row.id === id)
  if (!source) throw new Error('Missing manga source: ' + id)
  if (typeof source.run !== 'function') throw new Error(id + ' does not expose run()')
  if (!source._probe) throw new Error(id + ' has no live probe surface')
}

const files = (await readdir(new URL('./sources/manga/', import.meta.url)))
  .filter(name => name.endsWith('.js') && !name.startsWith('_'))
  .sort()
for (const id of expected) {
  if (!files.includes(id + '.js')) throw new Error('Manga source file is not installed: ' + id + '.js')
}
if (files.includes('mangabuddy.js')) throw new Error('Legacy MangaBuddy adapter must not return; MangaK replaced it.')

const ref = { sourceId:'parser:MANGADEX_EN', sourceName:'MangaDex', url:'/title/example' }
const packed = nyora._test.pack(ref)
const unpacked = nyora._test.unpack(packed)
if (unpacked?.sourceId !== ref.sourceId || unpacked?.url !== ref.url) throw new Error('Nyora source-reference codec failed.')

console.log('PASS manga sources: ' + expected.join(', '))
