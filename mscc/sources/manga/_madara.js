import * as cheerio from 'cheerio'
import { absolute, browserHeaders, chapterNumber, deliverCbz, deliverRange, fetchText, normalizedChapter, solveBrowserSession } from './_common.js'

async function postForm(url, body, headers = {}) {
  const request = async () => {
    const response = await fetch(url, {
      method:'POST',
      headers:browserHeaders(url, {
        accept:'text/html, */*; q=0.01',
        'content-type':'application/x-www-form-urlencoded; charset=UTF-8',
        'x-requested-with':'XMLHttpRequest',
        ...headers,
      }),
      body:new URLSearchParams(body),
      redirect:'follow',
      signal:AbortSignal.timeout(45000),
    })
    return { response, text:await response.text() }
  }
  let result=await request()
  if([403,429,503].includes(result.response.status)){
    await solveBrowserSession(new URL(url).origin + '/')
    result=await request()
  }
  if(!result.response.ok) throw new Error('HTTP '+result.response.status+' for '+new URL(url).hostname)
  return result
}

function imageFrom($, el) {
  const img=$(el).is('img') ? $(el) : $(el).find('img').first()
  return img.attr('data-src') || img.attr('data-lazy-src') || img.attr('data-original') || img.attr('src') || ''
}

export function createMadaraSource({
  id,
  name,
  baseUrl,
  mangaSubString='manga',
  fallbackOrder=100,
  aliases=[],
  cookies='',
  searchCardSelector='div.page-item-detail, .manga__item, .c-tabs-item__content',
}) {
  const pageHeaders=()=>({
    Referer:baseUrl+'/',
    ...(cookies?{Cookie:cookies}:{}),
  })

  const searchManga=async(query='')=>{
    const body={
      action:'madara_load_more',
      page:'0',
      template:'madara-core/content/content-archive',
      'vars[paged]':'1',
      'vars[template]':'archive',
      'vars[posts_per_page]':'25',
      'vars[post_type]':'wp-manga',
      'vars[post_status]':'publish',
      'vars[manga_archives_item_layout]':'big_thumbnail',
    }
    if(query) body['vars[s]']=query
    else {
      body['vars[orderby]']='meta_value_num'
      body['vars[meta_key]']='_wp_manga_views'
      body['vars[order]']='DESC'
    }
    const {text}=await postForm(baseUrl+'/wp-admin/admin-ajax.php',body,pageHeaders())
    const $=cheerio.load(text)
    const items=[]
    const parseCards=root=>{
      root(searchCardSelector).each((_,el)=>{
        const anchor=root(el).find('.post-title a[href], h3 a[href], h4 a[href]').first()
        const href=absolute(anchor.attr('href'),baseUrl)
        const title=anchor.text().trim() || anchor.attr('title') || ''
        if(href&&title&&!items.some(x=>x.url===href)) items.push({id:href,url:href,title,description:name})
      })
    }
    parseCards($)
    if(!items.length){
      const fallbackUrl=query ? baseUrl+'/?s='+encodeURIComponent(query)+'&post_type=wp-manga' : baseUrl+'/'+mangaSubString+'/?m_orderby=views'
      try { parseCards(cheerio.load((await fetchText(fallbackUrl,pageHeaders(),45000)).text)) } catch {}
    }
    return {items}
  }

  const chaptersFor=async(item)=>{
    const mangaUrl=String(item?.url||item?.id||'')
    const parsed=new URL(mangaUrl,baseUrl)
    let text=''
    try {
      const ajaxUrl=baseUrl+parsed.pathname.replace(/\/$/,'')+'/ajax/chapters/'
      text=(await postForm(ajaxUrl,{},pageHeaders())).text
    } catch {
      text=(await fetchText(parsed.href,pageHeaders(),45000)).text
    }
    const $=cheerio.load(text)
    const chapters=[]
    $('li.wp-manga-chapter').each((index,el)=>{
      const anchor=$(el).find('a[href]').first()
      const href=absolute(anchor.attr('href'),baseUrl)
      const title=anchor.text().trim()
      if(href) chapters.push(normalizedChapter({id:href,url:href,title,number:chapterNumber(title,index+1)},index))
    })
    return {title:item?.title||name,chapters}
  }

  const pagesFor=async(chapter)=>{
    const url=String(chapter?.url||chapter?.id||'')
    const {text}=await fetchText(url,pageHeaders(),45000)
    const $=cheerio.load(text)
    const pages=[]
    $('div.page-break, li.blocks-gallery-item, .reading-content .text-left:not(:has(.blocks-gallery-item))').each((_,el)=>{
      const src=absolute(imageFrom($,el),url)
      if(src) pages.push({url:src,headers:{Referer:url,...(cookies?{Cookie:cookies}:{})}})
    })
    if(!pages.length){
      $('.reading-content img, .page-break img').each((_,el)=>{
        const src=absolute(imageFrom($,el),url)
        if(src&&!pages.some(p=>p.url===src)) pages.push({url:src,headers:{Referer:url,...(cookies?{Cookie:cookies}:{})}})
      })
    }
    if(!pages.length) throw new Error(name+' returned no reader pages.')
    return pages
  }

  return {
    id,name,description:'Direct '+name+' Madara search, chapter AJAX and reader pages.',fallbackOrder,brandAliases:[name,...aliases],
    async run({action,query,item,chapter,chapterId,range,context}){
      if(action==='search'||action==='browse') return searchManga(action==='search'?String(query||'').trim():'')
      if(action==='chapters') return chaptersFor(item)
      if(action==='options') return {qualities:['source'],deliveries:['document']}
      if(action==='download'){
        const target=chapter||{id:chapterId,url:chapterId,title:'Chapter'}
        return deliverCbz(context,{pages:await pagesFor(target),title:item?.title||name,chapterTitle:target.title||('Chapter '+target.number)})
      }
      if(action==='downloadRange') return deliverRange({listChapters:async()=> (await chaptersFor(item)).chapters,resolvePages:pagesFor,context,item,range})
      throw new Error('Unsupported '+name+' action: '+action)
    },
    _probe:{searchManga,chaptersFor,pagesFor},
  }
}
