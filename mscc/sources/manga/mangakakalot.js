import { absolute, deliverCbz, deliverRange, fetchJson, loadDocument, normalizedChapter } from './_common.js'

const BASES=['https://www.mangakakalot.gg','https://www.mangakakalove.com']
let baseIndex=0
const base=()=>BASES[baseIndex]||BASES[0]

function normalizeQuery(value){
  return String(value||'').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'')
    .replace(/đ/g,'d').replace(/[^a-z0-9]+/g,'_').replace(/^_+|_+$/g,'')
}
function titleSlug(title){return String(title||'').toLowerCase().replace(/[^a-z0-9\s-]/g,'').trim().replace(/[\s-]+/g,'-').replace(/^-|-$/g,'')}
function slugFor(item){
  let slug=String(item?.slug||item?.id||item?.url||'').split('/').filter(Boolean).at(-1)||''
  if(/^[a-z]{2}\d+$/i.test(slug)&&item?.title) slug=titleSlug(item.title)
  return slug
}
async function withMirror(fn){
  let last
  for(let i=0;i<BASES.length;i++){
    const index=(baseIndex+i)%BASES.length
    try{const value=await fn(BASES[index]);baseIndex=index;return value}catch(error){last=error}
  }
  throw last||new Error('MangaKakalot mirrors failed.')
}

async function searchManga(query=''){
  return withMirror(async root=>{
    const path=query?'/search/story/'+normalizeQuery(query)+'?page=1':'/manga-list/hot-manga?page=1'
    const {$}=await loadDocument(root+path)
    const items=[]
    $('.panel_story_list .story_item, div.list-truyen-item-wrap, div.list-comic-item-wrap').each((_,el)=>{
      const anchor=$(el).find('h3 a[href]').first()
      const href=absolute(anchor.attr('href'),root)
      const title=anchor.text().trim()
      if(href&&title){
        const rawSlug=new URL(href).pathname.split('/').filter(Boolean).at(-1)||''
        items.push({id:rawSlug,url:href,title,slug:/^[a-z]{2}\d+$/i.test(rawSlug)?titleSlug(title):rawSlug,description:'MangaKakalot'})
      }
    })
    if(!items.length) throw new Error('MangaKakalot returned no search results.')
    return {items}
  })
}

async function chaptersFor(item){
  const slug=slugFor(item)
  if(!slug) throw new Error('MangaKakalot manga slug is missing.')
  return withMirror(async root=>{
    const {data}=await fetchJson(root+'/api/manga/'+encodeURIComponent(slug)+'/chapters?limit=-1')
    if(data?.success!==true) throw new Error('MangaKakalot chapter API did not succeed.')
    const rows=data?.data?.chapters||[]
    const chapters=rows.map((row,index)=>{
      const chapterSlug=row?.chapter_slug
      if(!chapterSlug) return null
      return normalizedChapter({
        id:slug+'|'+chapterSlug,
        url:root+'/manga/'+slug+'/'+chapterSlug,
        number:row?.chapter_num??index+1,
        title:row?.chapter_name||('Chapter '+(row?.chapter_num??index+1)),
      },index)
    }).filter(Boolean)
    return {title:item?.title||'MangaKakalot',chapters}
  })
}

function extractArray(script,name){
  const pattern=new RegExp(name+'\\s*=\\s*\\[([^\\]]+)\\]')
  const body=pattern.exec(script)?.[1]
  if(!body) return []
  return body.split(',').map(x=>x.trim().replace(/^["']|["']$/g,'').replace(/\\\//g,'/').replace(/\/$/,'')).filter(Boolean)
}

async function pagesFor(chapter){
  const url=String(chapter?.url||chapter?.id||'')
  const {text,$}=await loadDocument(url)
  const scripts=[...text.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/gi)].map(m=>m[1])
  const script=scripts.filter(x=>/cdns\s*=|chapterImages\s*=/.test(x)).join('\n')
  const cdns=[...extractArray(script,'cdns'),...extractArray(script,'backupImage')]
  const images=extractArray(script,'chapterImages')
  let pages=[]
  if(images.length&&cdns.length){
    pages=images.map(path=>({url:new URL('/'+path.replace(/^\/+/,''),cdns[0]+'/').href,headers:{Referer:url,Connection:'close'}}))
  }else{
    $('div.container-chapter-reader > img').each((_,el)=>{
      const src=absolute($(el).attr('src')||$(el).attr('data-src'),url)
      if(src) pages.push({url:src,headers:{Referer:url,Connection:'close'}})
    })
  }
  if(!pages.length) throw new Error('MangaKakalot returned no reader pages.')
  return pages
}

export default {
  id:'mangakakalot',name:'MangaKakalot',
  description:'Direct MangaKakalot mirror search, chapter API and CDN reader pages.',
  fallbackOrder:25,brandAliases:['Mangakakalot','Manga Kakalot'],
  async run({action,query,item,chapter,chapterId,range,context}){
    if(action==='search'||action==='browse') return searchManga(action==='search'?String(query||'').trim():'')
    if(action==='chapters') return chaptersFor(item)
    if(action==='options') return {qualities:['source'],deliveries:['document']}
    if(action==='download'){
      const target=chapter||{id:chapterId,url:chapterId,title:'Chapter'}
      return deliverCbz(context,{pages:await pagesFor(target),title:item?.title||'MangaKakalot',chapterTitle:target.title||('Chapter '+target.number)})
    }
    if(action==='downloadRange') return deliverRange({listChapters:chaptersFor,resolvePages:pagesFor,context,item,range})
    throw new Error('Unsupported MangaKakalot action: '+action)
  },
  _probe:{searchManga,chaptersFor,pagesFor},
}
