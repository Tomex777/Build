import { absolute, chapterNumber, deliverCbz, deliverRange, loadDocument, normalizedChapter } from './_common.js'

const BASE='https://mangakatana.com'

function rows($) {
  const items=[]
  $('div#book_list > div.item').each((_,el)=>{
    const anchor=$(el).find('div.text > h3 > a').first()
    const href=absolute(anchor.attr('href'),BASE)
    const title=anchor.text().trim()
    if(href&&title) items.push({id:href,url:href,title,description:'MangaKatana'})
  })
  return items
}

async function searchManga(query='') {
  const url=new URL(query ? BASE+'/page/1' : BASE+'/manga/page/1')
  if(query){url.searchParams.set('search',query);url.searchParams.set('search_by','book_name')}
  const {$,response}=await loadDocument(url.href)
  const finalUrl=response.url||url.href
  if(new URL(finalUrl).pathname.match(/^\/manga\/[^/]+\/?$/)){
    const title=$('h1.heading').first().text().trim()
    return {items:title?[{id:finalUrl,url:finalUrl,title,description:'MangaKatana'}]:[]}
  }
  return {items:rows($)}
}

async function chaptersFor(item) {
  const url=String(item?.url||item?.id||'')
  const {$}=await loadDocument(url)
  const chapters=[]
  $('tr:has(.chapter)').each((index,el)=>{
    const anchor=$(el).find('a[href]').first()
    const href=absolute(anchor.attr('href'),url)
    const title=anchor.text().trim()
    if(href) chapters.push(normalizedChapter({id:href,url:href,title,number:chapterNumber(title,index+1)},index))
  })
  return {title:item?.title||$('h1.heading').first().text().trim()||'MangaKatana',chapters}
}

async function pagesFor(chapter) {
  const url=String(chapter?.url||chapter?.id||'')
  const {text}=await loadDocument(url)
  const script=[...text.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/gi)].map(m=>m[1]).find(x=>/data-src/.test(x))
  if(!script) throw new Error('MangaKatana reader script was not found.')
  const arrayName=/data-src['"]\s*,\s*(\w+)/.exec(script)?.[1]
  if(!arrayName) throw new Error('MangaKatana image array name was not found.')
  const body=new RegExp('var\\s+'+arrayName+'\\s*=\\s*\\[([^\\[]*)\\]').exec(script)?.[1]
  if(!body) throw new Error('MangaKatana image array was not found.')
  const pages=[...body.matchAll(/'([^']+)'/g)].map(m=>({url:absolute(m[1],url),headers:{Referer:url}})).filter(x=>x.url)
  if(!pages.length) throw new Error('MangaKatana returned no reader pages.')
  return pages
}

export default {
  id:'mangakatana',name:'MangaKatana',description:'Direct MangaKatana search, chapter table and reader image-array parser.',fallbackOrder:26,brandAliases:['Manga Katana'],
  async run({action,query,item,chapter,chapterId,range,context}){
    if(action==='search'||action==='browse') return searchManga(action==='search'?String(query||'').trim():'')
    if(action==='chapters') return chaptersFor(item)
    if(action==='options') return {qualities:['source'],deliveries:['document']}
    if(action==='download'){
      const target=chapter||{id:chapterId,url:chapterId,title:'Chapter'}
      return deliverCbz(context,{pages:await pagesFor(target),title:item?.title||'MangaKatana',chapterTitle:target.title||('Chapter '+target.number)})
    }
    if(action==='downloadRange') return deliverRange({listChapters:async()=> (await chaptersFor(item)).chapters,resolvePages:pagesFor,context,item,range})
    throw new Error('Unsupported MangaKatana action: '+action)
  },
  _probe:{searchManga,chaptersFor,pagesFor},
}
