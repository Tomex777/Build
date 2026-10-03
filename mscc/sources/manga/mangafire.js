import { deliverCbz, deliverRange, fetchJson, normalizedChapter } from './_common.js'
import { signedMangaFireUrl } from './_mangafire-vrf.js'

const BASE='https://mangafire.to'

async function api(path){
  return (await fetchJson(signedMangaFireUrl(BASE+path),{Referer:BASE+'/',Origin:BASE},45000)).data
}

async function searchManga(query=''){
  const url=new URL(BASE+'/api/titles')
  if(query) url.searchParams.set('keyword',query.trim())
  else url.searchParams.set('order[views_30d]','desc')
  url.searchParams.set('page','1')
  url.searchParams.set('limit','50')
  const data=(await fetchJson(signedMangaFireUrl(url.href),{Referer:BASE+'/',Origin:BASE},45000)).data
  return {items:(data?.items||[]).map(row=>{
    const hid=String(row?.hid||'')
    const slug=String(row?.slug||'')
    return {
      id:hid,
      hid,
      slug,
      url:BASE+'/title/'+hid+(slug?'-'+slug:''),
      title:String(row?.title||'Untitled'),
      description:'MangaFire',
    }
  }).filter(x=>x.id)}
}

function hidFor(item){
  const raw=String(item?.hid||item?.id||item?.url||'').replace(/\/$/,'').split('/').at(-1)||''
  if(item?.hid) return String(item.hid)
  if(raw.includes('.')) return raw.split('.').at(-1)
  if(raw.includes('-')) return raw.split('-')[0]
  return raw
}

async function chaptersFor(item){
  const hid=hidFor(item)
  if(!hid) throw new Error('MangaFire title id is missing.')
  const all=[]
  let page=1
  let last=1
  do{
    const url=new URL(BASE+'/api/titles/'+encodeURIComponent(hid)+'/chapters')
    url.searchParams.set('language','en')
    url.searchParams.set('sort','number')
    url.searchParams.set('order','desc')
    url.searchParams.set('page',String(page))
    url.searchParams.set('limit','200')
    const data=(await fetchJson(signedMangaFireUrl(url.href),{Referer:BASE+'/',Origin:BASE},45000)).data
    all.push(...(data?.items||[]))
    last=Math.max(1,Number(data?.meta?.lastPage||1))
    page+=1
  }while(page<=last&&page<=20)

  const chapters=all.map((row,index)=>{
    const number=Number(row?.number)
    const display=Number.isInteger(number)?String(number):String(row?.number??index+1)
    const name=String(row?.name||'').trim()
    return normalizedChapter({
      id:String(row?.id||''),
      url:BASE+'/title/'+hid+'/'+row?.id+'-chapter-'+display+'-en',
      number:Number.isFinite(number)?number:index+1,
      title:name?('Ch. '+display+' - '+name):('Ch. '+display),
    },index)
  }).filter(x=>x.id)
  return {title:item?.title||'MangaFire',chapters}
}

async function pagesFor(chapter){
  const raw=String(chapter?.id||chapter?.url||'')
  const id=raw.includes('/')?(raw.split('/').at(-1).split('-')[0]):raw
  if(!id) throw new Error('MangaFire chapter id is missing.')
  const data=await api('/api/chapters/'+encodeURIComponent(id))
  const pages=(data?.data?.pages||[]).map(page=>({
    url:String(page?.url||''),
    headers:{Referer:BASE+'/',Origin:BASE},
  })).filter(x=>x.url)
  if(!pages.length) throw new Error('MangaFire returned no reader pages.')
  return pages
}

export default {
  id:'mangafire',name:'MangaFire',
  description:'Direct MangaFire API with current VRF signing, English chapter pagination and page delivery.',
  fallbackOrder:23,brandAliases:['Manga Fire'],
  async run({action,query,item,chapter,chapterId,range,context}){
    if(action==='search'||action==='browse') return searchManga(action==='search'?String(query||'').trim():'')
    if(action==='chapters') return chaptersFor(item)
    if(action==='options') return {qualities:['source'],deliveries:['document']}
    if(action==='download'){
      const target=chapter||{id:chapterId,url:chapterId,title:'Chapter'}
      return deliverCbz(context,{pages:await pagesFor(target),title:item?.title||'MangaFire',chapterTitle:target.title||('Chapter '+target.number)})
    }
    if(action==='downloadRange') return deliverRange({listChapters:chaptersFor,resolvePages:pagesFor,context,item,range})
    throw new Error('Unsupported MangaFire action: '+action)
  },
  _probe:{searchManga,chaptersFor,pagesFor},
}
