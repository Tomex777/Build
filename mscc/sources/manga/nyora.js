import { deliverCbz, deliverRange, normalizedChapter, nyoraFetchJson, nyoraPublicPageUrl } from './_common.js'
import mangaDex from './mangadex.js'

function pack(value){return 'nyora:'+Buffer.from(JSON.stringify(value)).toString('base64url')}
function unpack(value){
  const raw=String(value||'')
  if(!raw.startsWith('nyora:')) return null
  try{return JSON.parse(Buffer.from(raw.slice(6),'base64url').toString('utf8'))}catch{return null}
}
function pushExternal(items,seen,sourceId,sourceName,entries){
  for(const entry of entries||[]){
    if(!entry?.url) continue
    const key=sourceId+'|'+entry.url
    if(seen.has(key)) continue
    seen.add(key)
    items.push({
      id:pack({mode:'helper',sourceId,sourceName,url:String(entry.url),mangaId:String(entry.id||'')}),
      title:String(entry.title||'Untitled'),
      description:sourceName+(entry.description?' — '+String(entry.description):''),
      url:String(entry.url),
    })
  }
}
async function searchManga(query=''){
  if(!query) return {items:[]}
  const items=[],seen=new Set()
  try{
    const {data}=await nyoraFetchJson('/search/global?q='+encodeURIComponent(query)+'&limitPerSource=5',90000)
    for(const group of data?.groups||[]) pushExternal(items,seen,String(group?.sourceId||''),String(group?.sourceName||group?.sourceId||'Nyora source'),group?.entries)
  }catch{}
  if(!items.length){
    try{
      const {data:catalogData}=await nyoraFetchJson('/sources/catalog',60000)
      const catalog=Array.isArray(catalogData)?catalogData:(catalogData?.entries||catalogData?.sources||[])
      for(const source of catalog.filter(x=>x?.id).slice(0,12)){
        if(items.length>=30) break
        try{
          const sid=String(source.id),name=String(source.name||source.title||sid)
          const {data}=await nyoraFetchJson('/sources/search?id='+encodeURIComponent(sid)+'&q='+encodeURIComponent(query)+'&page=1',35000)
          pushExternal(items,seen,sid,name,Array.isArray(data)?data:(data?.entries||data?.items||[]))
        }catch{}
      }
    }catch{}
  }
  if(!items.length){
    const fallback=await mangaDex.run({action:'search',query,context:{}})
    for(const entry of fallback?.items||[]){
      items.push({
        id:pack({mode:'mscc',sourceId:'mangadex',item:entry}),
        title:entry.title,
        description:'Nyora local parser fallback · MangaDex',
        url:entry.url||entry.id,
      })
    }
  }
  return {items:items.slice(0,80)}
}
function helperRef(item){return unpack(item?.id)}
async function chaptersFor(item){
  const ref=helperRef(item)
  if(ref?.mode==='mscc'&&ref.sourceId==='mangadex'){
    const data=await mangaDex.run({action:'chapters',item:ref.item,context:{}})
    return {title:data?.title||item?.title||'Nyora',chapters:(data?.chapters||[]).map((ch,index)=>normalizedChapter({
      ...ch,id:pack({mode:'mscc',sourceId:'mangadex',item:ref.item,chapter:ch}),url:ch.url||ch.id,
    },index))}
  }
  if(!ref?.sourceId||!ref?.url) throw new Error('Nyora result is missing its underlying source reference.')
  const {data}=await nyoraFetchJson('/manga/details?id='+encodeURIComponent(ref.sourceId)+'&url='+encodeURIComponent(ref.url),90000)
  const manga=data?.manga||item||{}
  return {title:String(manga?.title||item?.title||'Nyora'),chapters:(data?.chapters||manga?.chapters||[]).map((row,index)=>normalizedChapter({
    ...row,id:pack({mode:'helper',sourceId:ref.sourceId,sourceName:ref.sourceName,url:String(row?.url||row?.id||''),branch:row?.branch||null}),url:String(row?.url||row?.id||''),
  },index))}
}
async function pagesFor(chapter,item){
  const ref=unpack(chapter?.id)
  if(ref?.mode==='mscc'&&ref.sourceId==='mangadex') return mangaDex._probe.pagesFor(ref.chapter)
  const itemRef=item?helperRef(item):null
  const sid=ref?.sourceId||itemRef?.sourceId
  const url=ref?.url||chapter?.url
  const branch=ref?.branch||chapter?.branch
  if(!sid||!url) throw new Error('Nyora chapter is missing its underlying source reference.')
  const endpoint='/manga/pages?id='+encodeURIComponent(sid)+'&url='+encodeURIComponent(url)+(branch?'&branch='+encodeURIComponent(branch):'')
  const {data}=await nyoraFetchJson(endpoint,90000)
  return (data?.pages||[]).map(page=>typeof page==='string'?{url:nyoraPublicPageUrl(page)}:{url:nyoraPublicPageUrl(page?.url||''),headers:page?.headers||{}}).filter(page=>page.url)
}
export default {
  id:'nyora',name:'Nyora',
  description:'Nyora parser aggregation with local MSCC fallback when hosted helpers are unavailable.',
  fallbackOrder:80,brandAliases:['Nyora Manga'],
  async run({action,query,item,chapter,chapterId,range,context}){
    if(action==='search') return searchManga(String(query||'').trim())
    if(action==='browse') return searchManga('One Piece')
    if(action==='chapters') return chaptersFor(item)
    if(action==='options') return {qualities:['source'],deliveries:['document']}
    if(action==='download'){
      const target=chapter||{id:chapterId,title:'Chapter'}
      return deliverCbz(context,{pages:await pagesFor(target,item),title:item?.title||'Nyora',chapterTitle:target.title||('Chapter '+target.number)})
    }
    if(action==='downloadRange') return deliverRange({listChapters:async()=> (await chaptersFor(item)).chapters,resolvePages:ch=>pagesFor(ch,item),context,item,range})
    throw new Error('Unsupported Nyora action: '+action)
  },
  _test:{pack,unpack},_probe:{searchManga,chaptersFor,pagesFor},
}
