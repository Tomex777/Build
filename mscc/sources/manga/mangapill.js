import { absolute, chapterNumber, deliverCbz, deliverRange, loadDocument, normalizedChapter } from './_common.js'

const BASE='https://mangapill.com'

function cards($) {
  const items=[]
  $('.grid > div:not([class])').each((_,el)=>{
    const anchor=$(el).find("a[href^='/manga/']").first()
    const href=absolute(anchor.attr('href'),BASE)
    const title=$(el).find('div.line-clamp-2').first().text().trim() || anchor.attr('title') || anchor.text().trim()
    if(href&&title) items.push({id:href,url:href,title,description:'MangaPill'})
  })
  return items
}

async function searchManga(query='') {
  const url=new URL(query ? BASE+'/search' : BASE+'/chapters')
  if(query){url.searchParams.set('page','1');url.searchParams.set('q',query)}
  const {$}=await loadDocument(url.href)
  return {items:cards($)}
}

async function chaptersFor(item) {
  const url=String(item?.url||item?.id||'')
  const {$}=await loadDocument(url)
  const chapters=[]
  $('#chapters > div > a[href]').each((index,el)=>{
    const href=absolute($(el).attr('href'),url)
    const title=$(el).text().trim()
    if(href) chapters.push(normalizedChapter({id:href,url:href,title,number:chapterNumber(title,index+1)},index))
  })
  return {title:item?.title||$('h1').first().text().trim()||'MangaPill',chapters}
}

async function pagesFor(chapter) {
  const url=String(chapter?.url||chapter?.id||'')
  const {$}=await loadDocument(url)
  const pages=[]
  $('picture img').each((_,el)=>{
    const src=absolute($(el).attr('data-src')||$(el).attr('src'),url)
    if(src) pages.push({url:src,headers:{Referer:url}})
  })
  if(!pages.length) throw new Error('MangaPill returned no reader pages.')
  return pages
}

export default {
  id:'mangapill',name:'MangaPill',description:'Direct MangaPill search, chapters and reader pages.',fallbackOrder:27,brandAliases:['Manga Pill'],
  async run({action,query,item,chapter,chapterId,range,context}){
    if(action==='search'||action==='browse') return searchManga(action==='search'?String(query||'').trim():'')
    if(action==='chapters') return chaptersFor(item)
    if(action==='options') return {qualities:['source'],deliveries:['document']}
    if(action==='download'){
      const target=chapter||{id:chapterId,url:chapterId,title:'Chapter'}
      return deliverCbz(context,{pages:await pagesFor(target),title:item?.title||'MangaPill',chapterTitle:target.title||('Chapter '+target.number)})
    }
    if(action==='downloadRange') return deliverRange({listChapters:async()=> (await chaptersFor(item)).chapters,resolvePages:pagesFor,context,item,range})
    throw new Error('Unsupported MangaPill action: '+action)
  },
  _probe:{searchManga,chaptersFor,pagesFor},
}
