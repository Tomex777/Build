import { absolute, chapterNumber, deliverCbz, deliverRange, loadDocument, normalizedChapter } from './_common.js'

const BASE = 'https://18kami.com'
const CURRENT = 'https://18comic.vip'

function parseCards($, query = '', pageUrl = BASE) {
  const tokens = String(query || '').toLowerCase().split(/\s+/).filter(Boolean)
  const seen = new Set()
  const items = []
  $('a[href]').each((_, el) => {
    const href = absolute($(el).attr('href'), pageUrl)
    const text = $(el).find('h1,h2,h3,h4,.title,.card-title').first().text().trim() || $(el).attr('title') || $(el).text().trim()
    if (!href || text.length < 2) return
    let host = ''
    try { host = new URL(href).hostname.replace(/^www\./,'') } catch {}
    if (!['18kami.com','18comic.vip'].includes(host)) return
    if (tokens.length && !tokens.some(token => text.toLowerCase().includes(token))) return
    if (/\/(login|register|tag|tags|author|artists?|category|categories)(?:\/|$)/i.test(href)) return
    if (seen.has(href)) return
    seen.add(href)
    items.push({ id:href, url:href, title:text.slice(0,180), description:'18+ source' })
  })
  return items.slice(0,50)
}

async function searchManga(query = '') {
  const candidates = query
    ? [
        CURRENT + '/albums/meiman?search=' + encodeURIComponent(query),
        CURRENT + '/search/photos?search_query=' + encodeURIComponent(query),
        CURRENT + '/albums/meiman',
        BASE + '/?s=' + encodeURIComponent(query),
      ]
    : [CURRENT + '/albums/meiman', CURRENT + '/albums', BASE + '/']
  let last
  let unfiltered = []
  for (const url of candidates) {
    try {
      const { $, response } = await loadDocument(url)
      const pageUrl = String(response?.url || url)
      const items = parseCards($, query, pageUrl)
      if (items.length) return { items }
      if (!unfiltered.length) unfiltered = parseCards($, '', pageUrl)
    } catch (error) { last = error }
  }
  if (unfiltered.length) return { items:unfiltered }
  if (last) throw last
  return { items:[] }
}

async function chaptersFor(item) {
  const url = String(item?.id || item?.url || '')
  const { $ } = await loadDocument(url)
  const rows = []
  $('.episode').first().find('a[href]').each((_,el) => {
    const href = absolute($(el).attr('href'), url)
    const title = $(el).find('li').first().clone().children().remove().end().text().trim() || $(el).text().trim()
    if (href) rows.push({ id:href, url:href, title:title || 'Chapter', number:chapterNumber(title, rows.length + 1) })
  })
  if (!rows.length) {
    const reader = $('a').filter((_,el) => /start reading/i.test($(el).text())).first()
    const href = reader.length ? absolute(reader.attr('href'),url) : url
    rows.push({ id:href||url, url:href||url, title:'Chapter 1', number:'1' })
  }
  return { title:$('h1').first().text().trim() || item?.title || '18Kami', chapters:rows.map(normalizedChapter) }
}

async function pagesFor(chapter) {
  let url = String(chapter?.url || chapter?.id || '')
  let loaded = await loadDocument(url)
  const start = loaded.$('a').filter((_,el) => /start reading/i.test(loaded.$(el).text())).first().attr('href')
  if (start) {
    url = absolute(start, url)
    loaded = await loadDocument(url)
  }
  const pages = []
  const add=(src,referer=url)=>{const resolved=absolute(src,referer);if(resolved&&!pages.some(p=>p.url===resolved))pages.push({url:resolved,headers:{Referer:referer}})}
  loaded.$('img[data-page]').each((_,el)=>add(loaded.$(el).attr('data-original')||loaded.$(el).attr('data-src')||loaded.$(el).attr('src')))
  if(!pages.length && /18comic\.vip/i.test(url)){
    const photoUrl=url.replace('/album/','/photo/')
    if(photoUrl!==url) loaded=await loadDocument(photoUrl)
    loaded.$('.row.thumb-overlay-albums img, .thumb-overlay-albums img, img[data-original]').each((_,el)=>add(loaded.$(el).attr('data-original')||loaded.$(el).attr('data-src')||loaded.$(el).attr('src'),photoUrl))
  }
  if (!pages.length) throw new Error('18Kami returned no reader pages.')
  return pages
}

export default {
  id:'18kami',
  name:'18Kami',
  description:'Adult manga source. Search, chapter discovery, reader pages and CBZ delivery.',
  fallbackOrder:90,
  brandAliases:['18 Kami'],
  async run({ action, query, item, chapter, chapterId, range, context }) {
    if (action === 'search' || action === 'browse') return searchManga(action === 'search' ? String(query || '').trim() : '')
    if (action === 'chapters') return chaptersFor(item)
    if (action === 'options') return { qualities:['source'], deliveries:['document'] }
    if (action === 'download') {
      const target = chapter || { id:chapterId, url:chapterId, title:'Chapter' }
      return deliverCbz(context, { pages:await pagesFor(target), title:item?.title || '18Kami', chapterTitle:target.title || ('Chapter ' + target.number) })
    }
    if (action === 'downloadRange') return deliverRange({ listChapters:async () => (await chaptersFor(item)).chapters, resolvePages:pagesFor, context, item, range })
    throw new Error('Unsupported 18Kami action: ' + action)
  },
  _probe:{ searchManga, chaptersFor, pagesFor },
}
