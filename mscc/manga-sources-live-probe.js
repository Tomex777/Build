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

async function collectItems() {
  const attempts = [query, 'Bleach', 'Naruto'].filter((value, index, array) => value && array.indexOf(value) === index)
  const items = []
  const seen = new Set()

  for (const value of attempts) {
    try {
      const result = await source.run({ action:'search', query:value, context:{} })
      for (const item of result?.items || []) {
        const key = String(item?.id || item?.url || item?.title || '')
        if (!key || seen.has(key)) continue
        seen.add(key)
        items.push(item)
        if (items.length >= 10) return items
      }
    } catch {}
  }

  if (items.length < 10) {
    try {
      const result = await source.run({ action:'browse', query:'', context:{} })
      for (const item of result?.items || []) {
        const key = String(item?.id || item?.url || item?.title || '')
        if (!key || seen.has(key)) continue
        seen.add(key)
        items.push(item)
        if (items.length >= 10) break
      }
    } catch {}
  }
  return items
}

const items = await collectItems()
must(items.length, source.name + ' search/browse returned no results')
const normTitle = value => String(value || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim()
const wantedTitle = normTitle(query)
items.sort((a,b) => {
  const rank = item => {
    const title = normTitle(item?.title)
    if (title === wantedTitle) return 0
    if (title.startsWith(wantedTitle)) return 1
    if (title.includes(wantedTitle)) return 2
    return 3
  }
  return rank(a) - rank(b)
})

let proof = null
let result = null
let chapter = null
let item = null
let listing = null
const failures = []

for (const candidateItem of items.slice(0, 8)) {
  let candidateListing
  try {
    candidateListing = await source.run({ action:'chapters', item:candidateItem, context:{} })
  } catch (error) {
    failures.push((candidateItem.title || candidateItem.id) + ': chapters: ' + String(error?.message || error))
    continue
  }
  if (!Array.isArray(candidateListing?.chapters) || !candidateListing.chapters.length) {
    failures.push((candidateItem.title || candidateItem.id) + ': no chapters')
    continue
  }

  const preferred = [
    candidateListing.chapters.find(row => Number(row?.number) === 1),
    ...candidateListing.chapters.slice(0, 5),
    ...candidateListing.chapters.slice(-5),
  ].filter(Boolean)
  const candidates = [...new Map(preferred.map(row => [String(row.id || row.url || row.title), row])).values()].slice(0, 10)

  for (const candidate of candidates) {
    proof = null
    try {
      const delivered = await source.run({
        action:'download',
        item:candidateItem,
        chapter:candidate,
        chapterId:candidate.id,
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
      if (delivered?.delivered === true && proof?.pages > 0) {
        result = delivered
        chapter = candidate
        item = candidateItem
        listing = candidateListing
        break
      }
      failures.push((candidateItem.title || candidateItem.id) + ' / ' + (candidate.title || candidate.id) + ': delivery did not complete')
    } catch (error) {
      failures.push((candidateItem.title || candidateItem.id) + ' / ' + (candidate.title || candidate.id) + ': ' + String(error?.message || error))
    }
  }
  if (result?.delivered === true) break
}

must(result?.delivered === true && chapter && item && listing, source.name + ' had no downloadable chapter. ' + failures.slice(-12).join(' | '))
must(proof?.pages > 0, source.name + ' CBZ was not inspected')
console.log(JSON.stringify({
  source:source.name,
  query,
  resultCount:items.length,
  selectedTitle:item.title,
  chapterCount:listing.chapters.length,
  selectedChapter:chapter.title,
  cbz:proof,
  elapsedMs:Math.round(performance.now() - began),
  verdict:'PASS',
}, null, 2))
