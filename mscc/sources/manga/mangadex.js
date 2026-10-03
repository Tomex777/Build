import { deliverCbz, deliverRange, fetchJson, normalizedChapter } from './_common.js'

const API = 'https://api.mangadex.org'
const SITE = 'https://mangadex.org'

function titleOf(attrs = {}) {
  const title = attrs.title || {}
  return title.en || title['en-us'] || Object.values(title).find(Boolean) || 'Untitled'
}

function descriptionOf(attrs = {}) {
  const desc = attrs.description || {}
  return String(desc.en || Object.values(desc).find(Boolean) || attrs.status || '').trim()
}

function mangaItem(row) {
  return {
    id:String(row.id),
    title:titleOf(row.attributes),
    description:descriptionOf(row.attributes),
  }
}

async function searchManga(query = '') {
  const url = new URL(API + '/manga')
  url.searchParams.set('limit', '30')
  url.searchParams.set('includes[]', 'cover_art')
  url.searchParams.set('order[relevance]', 'desc')
  for (const rating of ['safe','suggestive','erotica','pornographic']) url.searchParams.append('contentRating[]', rating)
  if (query) url.searchParams.set('title', query)
  const { data } = await fetchJson(url.href)
  return { items:(data?.data || []).map(mangaItem) }
}

async function chaptersFor(item) {
  const id = String(item?.id || '')
  if (!id) throw new Error('MangaDex manga id is missing.')
  const rows = []
  let offset = 0
  for (;;) {
    const url = new URL(API + '/manga/' + encodeURIComponent(id) + '/feed')
    url.searchParams.set('limit', '100')
    url.searchParams.set('offset', String(offset))
    url.searchParams.append('translatedLanguage[]', 'en')
    url.searchParams.set('order[chapter]', 'asc')
    url.searchParams.set('includeExternalUrl', '0')
    const { data } = await fetchJson(url.href)
    const batch = data?.data || []
    rows.push(...batch)
    offset += batch.length
    if (!batch.length || offset >= Number(data?.total || 0)) break
    if (offset >= 1000) break
  }
  return {
    title:item?.title || 'MangaDex',
    chapters:rows.map((row, index) => normalizedChapter({
      id:row.id,
      number:row.attributes?.chapter || row.attributes?.volume || index + 1,
      title:row.attributes?.title
        ? ('Chapter ' + (row.attributes?.chapter || index + 1) + ' — ' + row.attributes.title)
        : ('Chapter ' + (row.attributes?.chapter || index + 1)),
      url:row.id,
    }, index)),
  }
}

async function pagesFor(chapter) {
  const id = String(chapter?.id || chapter?.url || '')
  if (!id) throw new Error('MangaDex chapter id is missing.')

  let payload = null
  let lastError = null
  for (const suffix of ['?forcePort443=true', '']) {
    try {
      payload = (await fetchJson(API + '/at-home/server/' + encodeURIComponent(id) + suffix)).data
      if (payload?.baseUrl && payload?.chapter?.hash && payload?.chapter?.data?.length) break
    } catch (error) {
      lastError = error
      if (Number(error?.status) !== 404) throw error
    }
  }

  const base = payload?.baseUrl
  const hash = payload?.chapter?.hash
  const files = payload?.chapter?.data || []
  if (!base || !hash || !files.length) {
    if (lastError) throw lastError
    throw new Error('MangaDex returned no chapter pages.')
  }

  return files.map(file => ({
    url:base + '/data/' + hash + '/' + file,
    headers:{ Referer:SITE + '/', Accept:'image/avif,image/webp,*/*' },
  }))
}

export default {
  id:'mangadex',
  name:'MangaDex',
  description:'Official MangaDex API: search, English chapters, page images and CBZ delivery.',
  fallbackOrder:1,
  brandAliases:['Manga Dex'],
  async run({ action, query, item, chapter, chapterId, range, context }) {
    if (action === 'search' || action === 'browse') return searchManga(action === 'search' ? String(query || '').trim() : '')
    if (action === 'chapters') return chaptersFor(item)
    if (action === 'options') return { qualities:['source'], deliveries:['document'] }
    if (action === 'download') {
      const target = chapter || { id:chapterId, title:'Chapter' }
      const pages = await pagesFor(target)
      return deliverCbz(context, { pages, title:item?.title || 'MangaDex', chapterTitle:target.title || ('Chapter ' + target.number) })
    }
    if (action === 'downloadRange') {
      return deliverRange({ listChapters:async () => (await chaptersFor(item)).chapters, resolvePages:pagesFor, context, item, range })
    }
    throw new Error('Unsupported MangaDex action: ' + action)
  },
  _probe:{ searchManga, chaptersFor, pagesFor },
}
