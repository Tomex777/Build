import { absolute, deliverCbz, deliverRange, fetchJson, loadDocument, normalizedChapter } from './_common.js'

const BASE = 'https://web.usagi.one'
const API_HEADERS = {
  Accept:'application/json, text/plain, */*',
  'Sec-Fetch-Dest':'empty',
  'Sec-Fetch-Mode':'cors',
  'Sec-Fetch-Site':'cross-site',
  Referer:BASE+'/',
}

function pageUrl(raw, base) {
  try { return new URL(String(raw || ''), base).href } catch { return '' }
}

async function searchManga(query = '') {
  const url = new URL(BASE + '/api/catalog/search')
  url.searchParams.set('offset','0')
  url.searchParams.set('sortType','RATING')
  if (String(query || '').trim()) url.searchParams.set('q',String(query).trim())
  const { data } = await fetchJson(url.href, API_HEADERS, 60000)
  const rows = data?.list || []
  return {
    items:rows.map(row => {
      const slug = String(row?.elementId?.linkName || '').trim()
      return {
        id:slug,
        url:slug ? BASE + '/' + slug : '',
        title:String(row?.name || 'Untitled'),
        description:'Usagi',
      }
    }).filter(item => item.id),
  }
}

function userHashFromDocument($) {
  let found = ''
  $('script').each((_,el) => {
    const text = $(el).html() || ''
    const match = /user_hash.+'(.+?)'/.exec(text)
    if (match?.[1] && !found) found = match[1]
  })
  return found
}

async function chaptersFor(item) {
  const raw=String(item?.url||item?.id||'')
  const slug=String(item?.id||raw).replace(/^https?:\/\/[^/]+\//i,'').replace(/^\//,'')
  const cleanSlug=slug.replace(/__[^/]+$/,'')
  const candidates=[...new Set([
    /^https?:\/\//i.test(raw)?raw:'',
    BASE+'/'+slug, BASE+'/'+cleanSlug,
    'https://a.zazaza.me/'+slug, 'https://a.zazaza.me/'+cleanSlug,
  ].filter(Boolean))]
  let loaded=null, mangaUrl='', lastError=null
  for(const candidate of candidates){
    try{
      const page=await loadDocument(candidate)
      if(page.$('tr.item-row a.chapter-link, a.chapter-link').length||page.$('h1,.cr-hero-names__main').length){
        loaded=page;mangaUrl=String(page.response?.url||candidate);break
      }
    }catch(error){lastError=error}
  }
  if(!loaded) throw lastError||new Error('Usagi manga page is unavailable.')
  const { $ }=loaded
  const userHash = userHashFromDocument($)
  const rows = []

  $('tr.item-row:has(td > a):has(td.date:not(.text-info))').each((index,el) => {
    const anchor = $(el).find('a.chapter-link[href]').first()
    const href = absolute(anchor.attr('href'), mangaUrl)
    if (!href) return
    const info = $(el).find('td.item-title').first()
    const dataNum = Number(info.attr('data-num'))
    const number = Number.isFinite(dataNum) ? dataNum / 10 : index + 1
    const title = anchor.clone().children().remove().end().text().trim() || ('Chapter ' + number)
    const url = new URL(href)
    if (userHash && !url.searchParams.has('d')) url.searchParams.set('d',userHash)
    if (!url.searchParams.has('mtr')) url.searchParams.set('mtr','true')
    rows.push(normalizedChapter({ id:url.href, url:url.href, number, title }, index))
  })

  return {
    title:$('.cr-hero-names__main').first().text().trim() || $('meta[itemprop=name]').attr('content') || item?.title || 'Usagi',
    chapters:rows,
  }
}

function extractReaderPayload($) {
  let script = ''
  $('script').each((_,el) => {
    const text = $(el).html() || ''
    if (!script && text.includes('chapterInfo') && (text.includes('rm_h.readerInit(') || text.includes('rm_h.readerDoInit('))) script = text
  })
  if (!script) return ''
  const marker = script.includes('rm_h.readerInit(') ? 'rm_h.readerInit(' : 'rm_h.readerDoInit('
  const begin = script.indexOf(marker)
  const end = script.indexOf(');',begin)
  return begin >= 0 && end > begin ? script.slice(begin,end) : script
}

async function pagesFor(chapter) {
  const url = String(chapter?.url || chapter?.id || '')
  if (!url) throw new Error('Usagi chapter URL is missing.')
  const { $ } = await loadDocument(url)
  if ($('div.alert, form.purchase-form').length) throw new Error('Usagi chapter is unavailable or requires purchase.')
  if ($('h1').filter((_,el) => /требуется премиум/i.test($(el).text())).length) throw new Error('Usagi chapter requires premium access.')

  const payload = extractReaderPayload($)
  if (!payload) throw new Error('Usagi reader page data was not found.')

  const pages = []
  const regex = /\[['"](.*?)['"],['"](.*?)['"],['"](.*?)['"].*?\]/g
  for (const match of payload.matchAll(regex)) {
    const host = match[1]
    const middle = match[2]
    const end = match[3]
    let imageUrl
    if (!middle && end.startsWith('/static/')) imageUrl = BASE + end
    else if (middle.endsWith('/manga/')) imageUrl = host + end
    else imageUrl = middle + host + end
    if (!imageUrl.includes('://')) imageUrl = 'https:' + imageUrl
    if (imageUrl.includes('one-way.work')) imageUrl = imageUrl.split('?')[0]
    imageUrl = imageUrl.replace('//resh','//h')
    const resolved = pageUrl(imageUrl,url)
    if (resolved) pages.push({ url:resolved, headers:{ Referer:url, Accept:'image/avif,image/webp,*/*' } })
  }
  if (!pages.length) throw new Error('Usagi returned no reader pages.')
  return pages
}

export default {
  id:'usagi',
  name:'Usagi',
  description:'Direct Mihon-style GroupLe source on web.usagi.one with catalog search, public chapters and reader page extraction.',
  fallbackOrder:30,
  brandAliases:['Usagi.one','web.usagi.one'],
  async run({ action, query, item, chapter, chapterId, range, context }) {
    if (action === 'search' || action === 'browse') return searchManga(action === 'search' ? String(query || '').trim() : '')
    if (action === 'chapters') return chaptersFor(item)
    if (action === 'options') return { qualities:['source'], deliveries:['document'] }
    if (action === 'download') {
      const target = chapter || { id:chapterId, url:chapterId, title:'Chapter' }
      return deliverCbz(context,{ pages:await pagesFor(target), title:item?.title || 'Usagi', chapterTitle:target.title || ('Chapter '+target.number) })
    }
    if (action === 'downloadRange') return deliverRange({ listChapters:async()=> (await chaptersFor(item)).chapters, resolvePages:pagesFor, context, item, range })
    throw new Error('Unsupported Usagi action: ' + action)
  },
  _probe:{ searchManga, chaptersFor, pagesFor },
}
