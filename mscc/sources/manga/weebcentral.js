import { absolute, chapterNumber, deliverCbz, deliverRange, loadDocument, normalizedChapter } from './_common.js'

const BASE = 'https://weebcentral.com'
let lastRequestAt = 0
async function gate() {
  const wait = Math.max(0, 2000 - (Date.now() - lastRequestAt))
  if (wait) await new Promise(resolve => setTimeout(resolve, wait))
  lastRequestAt = Date.now()
}

async function searchManga(query = '') {
  const url = new URL(BASE + '/search/data')
  url.searchParams.set('text', String(query || '').replace(/[!#:(),-]/g, ' ').trim())
  url.searchParams.set('limit', '32')
  url.searchParams.set('offset', '0')
  url.searchParams.set('display_mode', 'Full Display')
  await gate()
  const { $ } = await loadDocument(url.href)
  const items = []
  $('article > section > a').each((_,el) => {
    const link = absolute($(el).attr('href'), BASE)
    const title = $(el).find('div:not([class])').last().text().trim()
    if (link && title) items.push({ id:link, url:link, title, description:'WeebCentral' })
  })
  return { items }
}

async function chaptersFor(item) {
  const mangaUrl = String(item?.url || item?.id || '')
  const parsed = new URL(mangaUrl, BASE)
  const segments = parsed.pathname.split('/').filter(Boolean)
  if (segments[0] !== 'series' || !segments[1]) throw new Error('WeebCentral series URL is invalid.')
  await gate()
  const { $ } = await loadDocument(BASE + '/series/' + encodeURIComponent(segments[1]) + '/full-chapter-list')
  const rows = []
  $('div[x-data] > a[href]').each((index,el) => {
    const href = absolute($(el).attr('href'), BASE)
    const title = $(el).find('span.flex > span').first().text().trim() || $(el).text().trim()
    if (href) rows.push(normalizedChapter({ id:href, url:href, title, number:chapterNumber(title, index + 1) }, index))
  })
  return { title:item?.title || 'WeebCentral', chapters:rows }
}

async function pagesFor(chapter) {
  const base = new URL(String(chapter?.url || chapter?.id || ''), BASE)
  if (!base.pathname.endsWith('/images')) base.pathname = base.pathname.replace(/\/$/,'') + '/images'
  base.searchParams.set('is_prev','False')
  base.searchParams.set('reading_style','long_strip')
  await gate()
  const { $ } = await loadDocument(base.href)
  const pages = []
  const addImage=el=>{
    const src=absolute($(el).attr('src')||$(el).attr('data-src')||$(el).attr('data-lazy-src')||$(el).attr('data-original'),base.href)
    if(src&&!pages.some(p=>p.url===src)) pages.push({url:src,headers:{Referer:base.href,Accept:'image/avif,image/webp,*/*',Host:new URL(src).host}})
  }
  $('section[x-data~=scroll] > img').each((_,el)=>addImage(el))
  if(!pages.length) $('section img, main img[alt*=Page], img[alt^=Page], img[alt^=page]').each((_,el)=>addImage(el))
  if (!pages.length) throw new Error('WeebCentral returned no reader pages.')
  return pages
}

export default {
  id:'weebcentral', name:'WeebCentral',
  description:'Direct WeebCentral search, chapter-list and long-strip page reader.',
  fallbackOrder:31, brandAliases:['Weeb Central'],
  async run({ action, query, item, chapter, chapterId, range, context }) {
    if (action === 'search' || action === 'browse') return searchManga(action === 'search' ? String(query || '').trim() : '')
    if (action === 'chapters') return chaptersFor(item)
    if (action === 'options') return { qualities:['source'], deliveries:['document'] }
    if (action === 'download') {
      const target = chapter || { id:chapterId, url:chapterId, title:'Chapter' }
      return deliverCbz(context,{ pages:await pagesFor(target), title:item?.title || 'WeebCentral', chapterTitle:target.title || ('Chapter ' + target.number) })
    }
    if (action === 'downloadRange') return deliverRange({ listChapters:async()=> (await chaptersFor(item)).chapters, resolvePages:pagesFor, context, item, range })
    throw new Error('Unsupported WeebCentral action: ' + action)
  },
  _probe:{ searchManga, chaptersFor, pagesFor },
}
