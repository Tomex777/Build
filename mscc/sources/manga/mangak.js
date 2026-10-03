import { deliverCbz, deliverRange, fetchJson, loadDocument, normalizedChapter } from './_common.js'

const BASE = 'https://mangak.io'
const API = 'https://api.mangak.io'

function unwrap(data) { return data?.data || data || {} }

async function searchManga(query = '') {
  const url = new URL(API + '/titles/search')
  url.searchParams.set('page', '1')
  url.searchParams.set('limit', '24')
  if (query) url.searchParams.set('q', query.replace(/[^\p{L}\p{N} ]/gu, '').trim().slice(0, 50))
  else {
    url.searchParams.set('sort', 'popular')
    url.searchParams.set('window', 'week')
  }
  const { data } = await fetchJson(url.href)
  const body = unwrap(data)
  return {
    items:(body.items || []).map(row => ({
      id:String(row.id || row.url || ''),
      title:String(row.name || row.title || 'Untitled'),
      description:String(row.status || '').trim(),
      url:String(row.url || ''),
    })),
  }
}

async function resolveItem(item) {
  if (item?.id && !String(item.id).startsWith('/')) return item
  const url = item?.url || item?.id
  if (!url) return item
  const { text } = await loadDocument(new URL(url, BASE).href)
  const match = /<script[^>]+id=["']__NEXT_DATA__["'][^>]*>([\s\S]*?)<\/script>/i.exec(text)
  if (!match) throw new Error('MangaK page did not expose Next.js manga data.')
  const json = JSON.parse(match[1])
  const manga = json?.props?.pageProps?.initialManga || json?.pageProps?.initialManga
  if (!manga?.id) throw new Error('MangaK manga id was not found.')
  return { ...item, id:String(manga.id), title:String(manga.name || item?.title || 'MangaK'), url:String(manga.url || url) }
}

async function chaptersFor(input) {
  const item = await resolveItem(input)
  const { data } = await fetchJson(API + '/titles/' + encodeURIComponent(item.id) + '/chapters?cv=' + Date.now())
  const body = unwrap(data)
  const rows = body.chapters || []
  return {
    title:item?.title || 'MangaK',
    chapters:rows
      .slice()
      .sort((a,b) => Number(a.chapter_number || 0) - Number(b.chapter_number || 0))
      .map((row,index) => normalizedChapter({
        id:row.url,
        url:row.url,
        number:row.chapter_number ?? index + 1,
        title:row.name || ('Chapter ' + (row.chapter_number ?? index + 1)),
      }, index)),
  }
}

async function pagesFor(chapter) {
  const target = new URL(String(chapter?.url || chapter?.id || ''), BASE).href
  const { text } = await loadDocument(target)
  const match = /<script[^>]+id=["']__NEXT_DATA__["'][^>]*>([\s\S]*?)<\/script>/i.exec(text)
  if (!match) throw new Error('MangaK chapter did not expose Next.js page data.')
  const json = JSON.parse(match[1])
  const pages = json?.props?.pageProps?.initialChapter?.images || json?.pageProps?.initialChapter?.images || []
  return pages.map(url => ({ url:String(url), headers:{ Referer:target } }))
}

export default {
  id:'mangak',
  name:'MangaK',
  description:'Current MangaBuddy successor at mangak.io, using its title API and reader page data.',
  fallbackOrder:2,
  brandAliases:['MangaBuddy','Manga K'],
  async run({ action, query, item, chapter, chapterId, range, context }) {
    if (action === 'search' || action === 'browse') return searchManga(action === 'search' ? String(query || '').trim() : '')
    if (action === 'chapters') return chaptersFor(item)
    if (action === 'options') return { qualities:['source'], deliveries:['document'] }
    if (action === 'download') {
      const target = chapter || { id:chapterId, url:chapterId, title:'Chapter' }
      return deliverCbz(context, { pages:await pagesFor(target), title:item?.title || 'MangaK', chapterTitle:target.title || ('Chapter ' + target.number) })
    }
    if (action === 'downloadRange') {
      return deliverRange({ listChapters:async () => (await chaptersFor(item)).chapters, resolvePages:pagesFor, context, item, range })
    }
    throw new Error('Unsupported MangaK action: ' + action)
  },
  _probe:{ searchManga, chaptersFor, pagesFor },
}
