import { deliverCbz, deliverRange, normalizedChapter, nyoraFetchJson, nyoraPublicPageUrl } from './_common.js'


function pack(value) {
  return 'nyora:' + Buffer.from(JSON.stringify(value)).toString('base64url')
}

function unpack(value) {
  const raw = String(value || '')
  if (!raw.startsWith('nyora:')) return null
  try { return JSON.parse(Buffer.from(raw.slice(6), 'base64url').toString('utf8')) } catch { return null }
}

function addNyoraEntries(items, seen, sourceId, sourceName, entries) {
  for (const entry of entries || []) {
    if (!entry?.url) continue
    const key = sourceId + '|' + entry.url
    if (seen.has(key)) continue
    seen.add(key)
    const ref = { sourceId, sourceName, url:String(entry.url), mangaId:String(entry.id || '') }
    items.push({ id:pack(ref), title:String(entry.title || 'Untitled'),
      description:sourceName + (entry.description ? ' — ' + String(entry.description) : ''),
      sourceId, url:String(entry.url) })
  }
}

async function searchManga(query = '') {
  if (!query) return { items:[] }
  const items = [], seen = new Set()
  try {
    const { data } = await nyoraFetchJson('/search/global?q=' + encodeURIComponent(query) + '&limitPerSource=5', 90000)
    for (const group of data?.groups || []) {
      addNyoraEntries(items, seen, String(group?.sourceId || ''), String(group?.sourceName || group?.sourceId || 'Nyora source'), group?.entries)
    }
  } catch {}
  if (!items.length) {
    const { data:catalogData } = await nyoraFetchJson('/sources/catalog', 60000)
    const catalog = Array.isArray(catalogData) ? catalogData : (catalogData?.entries || catalogData?.sources || [])
    const preferred = /mangadex|weebcentral|mangapill|mangakatana|mangaread|mangafox/i
    const candidates = catalog.filter(s=>s?.id).sort((x,y)=>(preferred.test(String(x.name||x.id))?0:1)-(preferred.test(String(y.name||y.id))?0:1)).slice(0,16)
    for (const source of candidates) {
      if (items.length >= 40) break
      try {
        const sid=String(source.id), name=String(source.name||source.title||sid)
        const { data }=await nyoraFetchJson('/sources/search?id='+encodeURIComponent(sid)+'&q='+encodeURIComponent(query)+'&page=1',45000)
        const entries=Array.isArray(data)?data:(data?.entries||data?.items||[])
        addNyoraEntries(items,seen,sid,name,entries.slice(0,5))
      } catch {}
    }
  }
  return { items:items.slice(0,80) }
}
function refFor(item) {
  const decoded = unpack(item?.id)
  if (decoded?.sourceId && decoded?.url) return decoded
  const sourceId = item?.sourceId || item?.source?.id
  const url = item?.url
  if (!sourceId || !url) throw new Error('Nyora result is missing its underlying source reference.')
  return { sourceId:String(sourceId), url:String(url), sourceName:String(item?.sourceName || sourceId) }
}

async function chaptersFor(item) {
  const ref = refFor(item)
  const { data } = await nyoraFetchJson(
    '/manga/details?id=' + encodeURIComponent(ref.sourceId) + '&url=' + encodeURIComponent(ref.url),
    90000
  )
  const manga = data?.manga || item || {}
  return {
    title:String(manga?.title || item?.title || 'Nyora'),
    chapters:(data?.chapters || manga?.chapters || []).map((row,index) => normalizedChapter({
      ...row,
      id:pack({
        sourceId:ref.sourceId,
        sourceName:ref.sourceName,
        url:String(row?.url || row?.id || ''),
        branch:row?.branch || null,
      }),
      url:String(row?.url || row?.id || ''),
    }, index)),
  }
}

async function pagesFor(chapter, item) {
  const packed = unpack(chapter?.id)
  const itemRef = item ? refFor(item) : null
  const sourceId = packed?.sourceId || itemRef?.sourceId
  const url = packed?.url || chapter?.url
  const branch = packed?.branch || chapter?.branch
  if (!sourceId || !url) throw new Error('Nyora chapter is missing its underlying source reference.')
  const endpoint = '/manga/pages?id=' + encodeURIComponent(sourceId)
    + '&url=' + encodeURIComponent(url)
    + (branch ? '&branch=' + encodeURIComponent(branch) : '')
  const { data } = await nyoraFetchJson(endpoint, 90000)
  return (data?.pages || []).map(page => typeof page === 'string'
    ? { url:nyoraPublicPageUrl(page) }
    : { url:nyoraPublicPageUrl(page?.url || ''), headers:page?.headers || {} }
  ).filter(page => page.url)
}

export default {
  id:'nyora',
  name:'Nyora',
  description:'Nyora global manga search across its live parser catalog, with source-preserving chapter/page resolution.',
  fallbackOrder:80,
  brandAliases:['Nyora Manga'],
  async run({ action, query, item, chapter, chapterId, range, context }) {
    if (action === 'search') return searchManga(String(query || '').trim())
    if (action === 'browse') return { items:[] }
    if (action === 'chapters') return chaptersFor(item)
    if (action === 'options') return { qualities:['source'], deliveries:['document'] }
    if (action === 'download') {
      const target = chapter || { id:chapterId, title:'Chapter' }
      const pages = await pagesFor(target, item)
      return deliverCbz(context, { pages, title:item?.title || 'Nyora', chapterTitle:target.title || ('Chapter ' + target.number) })
    }
    if (action === 'downloadRange') {
      return deliverRange({
        listChapters:async () => (await chaptersFor(item)).chapters,
        resolvePages:(ch) => pagesFor(ch, item),
        context, item, range,
      })
    }
    throw new Error('Unsupported Nyora action: ' + action)
  },
  _test:{ pack, unpack },
  _probe:{ searchManga, chaptersFor, pagesFor },
}
