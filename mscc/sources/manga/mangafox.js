import { absolute, chapterNumber, deliverCbz, deliverRange, loadDocument, normalizedChapter } from './_common.js'

const BASE='https://fanfox.net'
const MOBILE='https://m.fanfox.net'
const COOKIES='readway=2; isAdult=1'

async function searchManga(query='') {
  const url=query
    ? BASE+'/search?title='+encodeURIComponent(query)+'&genres=&nogenres=&sort=&stype=1'
    : BASE+'/directory/'
  const {$}=await loadDocument(url,{Cookie:COOKIES})
  const selector=query?'ul.manga-list-4-list li':'ul.manga-list-1-list li'
  const items=[]
  $(selector).each((_,el)=>{
    const anchor=$(el).find('a[href]').first()
    const href=absolute(anchor.attr('href'),BASE)
    const title=anchor.attr('title')||$(el).find('a[title]').first().attr('title')||anchor.text().trim()
    if(href&&title) items.push({id:href,url:href,title,description:'Manga Fox'})
  })
  return {items}
}

async function chaptersFor(item) {
  const url=String(item?.url||item?.id||'')
  const {$}=await loadDocument(url,{Cookie:COOKIES})
  const chapters=[]
  $('ul.detail-main-list li a[href]').each((index,el)=>{
    const href=absolute($(el).attr('href'),url)
    const title=$(el).find('.detail-main-list-main p').first().text().trim()||$(el).text().trim()
    if(href) chapters.push(normalizedChapter({id:href,url:href,title,number:chapterNumber(title,index+1)},index))
  })
  return {title:item?.title||$('.detail-info-right-title-font').first().text().trim()||'Manga Fox',chapters}
}

async function pagesFor(chapter) {
  const original=new URL(String(chapter?.url||chapter?.id||''),BASE)
  const mobilePath=original.pathname.replace('/manga/','/roll_manga/')
  const target=MOBILE+mobilePath+original.search
  const {$}=await loadDocument(target,{Cookie:COOKIES,Referer:MOBILE+'/'})
  const pages=[]
  $('#viewer img').each((_,el)=>{
    const src=absolute($(el).attr('data-original')||$(el).attr('src'),target)
    if(src) pages.push({url:src,headers:{Referer:MOBILE+'/',Cookie:COOKIES}})
  })
  if(!pages.length) throw new Error('Manga Fox returned no reader pages.')
  return pages
}

export default {
  id:'mangafox',name:'Manga Fox',description:'Direct FanFox/Manga Fox search, chapters and mobile roll reader.',fallbackOrder:22,brandAliases:['MangaFox','FanFox'],
  async run({action,query,item,chapter,chapterId,range,context}){
    if(action==='search'||action==='browse') return searchManga(action==='search'?String(query||'').trim():'')
    if(action==='chapters') return chaptersFor(item)
    if(action==='options') return {qualities:['source'],deliveries:['document']}
    if(action==='download'){
      const target=chapter||{id:chapterId,url:chapterId,title:'Chapter'}
      return deliverCbz(context,{pages:await pagesFor(target),title:item?.title||'Manga Fox',chapterTitle:target.title||('Chapter '+target.number)})
    }
    if(action==='downloadRange') return deliverRange({listChapters:async()=> (await chaptersFor(item)).chapters,resolvePages:pagesFor,context,item,range})
    throw new Error('Unsupported Manga Fox action: '+action)
  },
  _probe:{searchManga,chaptersFor,pagesFor},
}
