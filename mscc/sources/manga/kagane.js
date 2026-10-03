import { browserHeaders, deliverCbz, deliverRange, MANGA_UA, normalizedChapter, solveBrowserSession } from './_common.js'

const BASE='https://kagane.to'
const API=BASE+'/api/v2'
let integrityToken=''
let integrityExp=0

async function jsonRequest(url,{method='GET',body,headers={}}={}){
  const request=async()=>{
    const init={
      method,
      headers:browserHeaders(url,{accept:'application/json','content-type':'application/json',referer:BASE+'/',origin:BASE,...headers}),
      redirect:'follow',
      signal:AbortSignal.timeout(45000),
    }
    if(body!==undefined) init.body=JSON.stringify(body)
    const response=await fetch(url,init)
    return {response,text:await response.text()}
  }
  let result=await request()
  if([403,429,503].includes(result.response.status)){
    await solveBrowserSession(BASE+'/')
    result=await request()
  }
  if(!result.response.ok) throw new Error('Kagane HTTP '+result.response.status+' for '+new URL(url).pathname)
  try{return JSON.parse(result.text)}catch{throw new Error('Kagane returned invalid JSON.')}
}

async function searchManga(query=''){
  const url=new URL(API+'/search/series')
  url.searchParams.set('page','0')
  url.searchParams.set('size','35')
  const body={
    source_type:['Official','Unofficial','Mixed'],
    content_lang:['en'],
  }
  if(query) body.title=query
  const data=await jsonRequest(url.href,{method:'POST',body})
  return {items:(data?.content||[]).map(row=>({
    id:String(row.series_id||''),
    url:BASE+'/series/'+row.series_id,
    title:String(row.title||'Untitled').trim(),
    description:'Kagane',
  })).filter(x=>x.id)}
}

async function details(item){
  const id=String(item?.id||item?.url||'').split('/').filter(Boolean).at(-1)
  if(!id) throw new Error('Kagane series id is missing.')
  const data=await jsonRequest(API+'/series/'+encodeURIComponent(id))
  const rows=data?.series_books||[]
  const chapters=rows.slice().reverse().map((row,index)=>{
    const number=row?.sort_no ?? row?.chapter_no ?? index+1
    const title=String(row?.title||'').trim() || (row?.chapter_no ? 'Ch.'+row.chapter_no : 'Chapter '+number)
    return normalizedChapter({
      id:String(row.book_id||''),
      url:BASE+'/series/'+id+'/reader/'+row.book_id,
      number,
      title,
    },index)
  }).filter(x=>x.id)
  return {title:String(data?.title||item?.title||'Kagane'),chapters}
}

async function integrity(){
  if(integrityToken && integrityExp>Date.now()+5000) return integrityToken
  await fetch(BASE+'/',{headers:{'user-agent':MANGA_UA},redirect:'follow',signal:AbortSignal.timeout(30000)}).catch(()=>null)
  const data=await jsonRequest(BASE+'/api/integrity',{method:'POST',body:{}})
  if(!data?.token) throw new Error('Kagane integrity token was not returned.')
  integrityToken=String(data.token)
  integrityExp=Number(data.exp||0)*1000
  return integrityToken
}

async function challenge(chapterId){
  const token=await integrity()
  const url=new URL(API+'/books/'+encodeURIComponent(chapterId))
  url.searchParams.set('is_datasaver','false')
  try{
    return await jsonRequest(url.href,{method:'POST',body:{},headers:{'x-integrity-token':token}})
  }catch(error){
    integrityToken='';integrityExp=0
    const retry=await integrity()
    return jsonRequest(url.href,{method:'POST',body:{},headers:{'x-integrity-token':retry}})
  }
}

async function pagesFor(chapter){
  const chapterId=String(chapter?.id||chapter?.url||'').split('/').filter(Boolean).at(-1)
  if(!chapterId) throw new Error('Kagane chapter id is missing.')
  const data=await challenge(chapterId)
  const access=String(data?.access_token||'')
  const cache=String(data?.cache_url||'').replace(/\/$/,'')
  const rows=data?.manifest?.pages||[]
  if(!access||!cache||!rows.length) throw new Error('Kagane challenge returned no readable page manifest.')
  return rows.slice().sort((a,b)=>Number(a.page_no)-Number(b.page_no)).map(row=>({
    url:cache+'/api/v2/books/page/'+encodeURIComponent(chapterId)+'/'+encodeURIComponent(row.page_id)+'.'+(row.ext||'jxl')+'?token='+encodeURIComponent(access),
    headers:{Referer:BASE+'/'},
  }))
}

export default {
  id:'kagane',name:'Kagane',
  description:'Direct Kagane v2 API with integrity challenge, access token and cache-manifest page delivery.',
  fallbackOrder:21,brandAliases:['Kagane.to'],
  async run({action,query,item,chapter,chapterId,range,context}){
    if(action==='search'||action==='browse') return searchManga(action==='search'?String(query||'').trim():'')
    if(action==='chapters') return details(item)
    if(action==='options') return {qualities:['source'],deliveries:['document']}
    if(action==='download'){
      const target=chapter||{id:chapterId,url:chapterId,title:'Chapter'}
      return deliverCbz(context,{pages:await pagesFor(target),title:item?.title||'Kagane',chapterTitle:target.title||('Chapter '+target.number)})
    }
    if(action==='downloadRange') return deliverRange({listChapters:details,resolvePages:pagesFor,context,item,range})
    throw new Error('Unsupported Kagane action: '+action)
  },
  _probe:{searchManga,chaptersFor:details,pagesFor},
}
