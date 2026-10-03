import { createDecipheriv, randomBytes } from 'node:crypto'
import { browserHeaders, deliverCbz, deliverRange, MANGA_UA, normalizedChapter, solveBrowserSession } from './_common.js'

const BASE='https://mangahub.io'
const API='https://api.mghcdn.com/graphql'
const CDN='https://imgx.mghcdn.com'
const SOURCE='m01'
let accessKey=randomBytes(16).toString('hex')

function esc(value){return JSON.stringify(String(value||'')).slice(1,-1)}
function apiHeaders(){
  return browserHeaders(API,{
    'user-agent':MANGA_UA,
    accept:'application/json',
    'content-type':'application/json',
    origin:BASE,
    referer:BASE+'/',
    cookie:'mhub_access='+accessKey,
    'x-mhub-access':accessKey,
  })
}

async function refreshKey(refreshUrl=''){
  const target=refreshUrl||BASE+'/chapter/martial-peak/chapter-1000'
  const parsed=new URL(target,BASE)
  const slug=parsed.pathname.split('/').filter(Boolean)[1] || 'martial-peak'
  const referer=BASE+'/manga/'+slug

  for(const suffix of ['?reloadKey=1','']){
    const requestUrl=target+suffix
    let response=await fetch(requestUrl,{
      headers:browserHeaders(requestUrl,{'user-agent':MANGA_UA,referer}),
      redirect:'follow',
      signal:AbortSignal.timeout(30000),
    })
    if([403,429,503].includes(response.status)){
      response.body?.cancel?.().catch?.(()=>{})
      await solveBrowserSession(BASE+'/')
      response=await fetch(requestUrl,{
        headers:browserHeaders(requestUrl,{'user-agent':MANGA_UA,referer}),
        redirect:'follow',
        signal:AbortSignal.timeout(30000),
      })
    }
    const values=typeof response.headers.getSetCookie==='function'?response.headers.getSetCookie():[response.headers.get('set-cookie')||'']
    for(const value of values){
      const match=/mhub_access=([^;]+)/.exec(String(value||''))
      if(match?.[1]){accessKey=match[1];return true}
    }
  }
  accessKey=randomBytes(16).toString('hex')
  return false
}

async function graph(query,refreshUrl=''){
  let last
  for(let attempt=0;attempt<2;attempt++){
    let response=await fetch(API,{method:'POST',headers:apiHeaders(),body:JSON.stringify({query}),signal:AbortSignal.timeout(45000)})
    let text=await response.text()
    if([403,429,503].includes(response.status)&&/cloudflare|just a moment|challenge|captcha/i.test(text)){
      await solveBrowserSession(BASE+'/')
      response=await fetch(API,{method:'POST',headers:apiHeaders(),body:JSON.stringify({query}),signal:AbortSignal.timeout(45000)})
      text=await response.text()
    }
    if(!response.ok) {
      last=new Error('MangaHub GraphQL HTTP '+response.status)
    } else {
      let json
      try{json=JSON.parse(text)}catch{throw new Error('MangaHub GraphQL returned invalid JSON.')}
      if(!json?.errors?.length) return json?.data||{}
      last=new Error('MangaHub GraphQL: '+json.errors.map(x=>x.message).join('; '))
    }
    if(attempt===0) await refreshKey(refreshUrl).catch(()=>false)
  }
  throw last||new Error('MangaHub GraphQL failed.')
}

async function searchManga(query=''){
  const q='{ search(x: '+SOURCE+', q: "'+esc(query)+'", genre: "all", mod: POPULAR, offset: 0) { rows { title slug image } } }'
  const data=await graph(q)
  return {items:(data?.search?.rows||[]).map(row=>({
    id:String(row.slug||''),
    url:BASE+'/manga/'+row.slug,
    title:String(row.title||row.slug||'Untitled'),
    description:'MangaHub',
  })).filter(x=>x.id)}
}

async function details(item){
  const slug=String(item?.id||item?.url||'').split('/').filter(Boolean).at(-1)
  if(!slug) throw new Error('MangaHub slug is missing.')
  const q='{ manga(x: '+SOURCE+', slug: "'+esc(slug)+'") { title slug status image author artist genres description alternativeTitle chapters { number title date } } }'
  const data=await graph(q,BASE+'/manga/'+slug)
  const manga=data?.manga
  if(!manga) throw new Error('MangaHub returned no manga details.')
  const chapters=(manga.chapters||[]).slice().sort((a,b)=>Number(a.number)-Number(b.number)).map((row,index)=>{
    const number=String(row.number).replace(/\.0$/,'')
    return normalizedChapter({
      id:slug+'|'+number,
      url:BASE+'/chapter/'+slug+'/chapter-'+number,
      number,
      title:String(row.title||'').trim()
        ? ('Chapter '+number+' - '+String(row.title).trim())
        : ('Chapter '+number),
    },index)
  })
  return {title:String(manga.title||item?.title||'MangaHub'),chapters}
}

function parseChapter(chapter){
  const id=String(chapter?.id||'')
  if(id.includes('|')){
    const [slug,number]=id.split('|')
    return {slug,number:Number(number)}
  }
  const url=new URL(String(chapter?.url||id),BASE)
  const slug=url.pathname.split('/').filter(Boolean)[1]
  const number=Number(url.pathname.split('chapter-').at(-1))
  return {slug,number}
}

function decodePagesString(value){
  if(!String(value||'').startsWith('enc:v1')) return String(value||'')
  throw new Error('encrypted')
}

async function decryptPages(value){
  const parts=String(value).split(':')
  if(parts.length<6) throw new Error('MangaHub encrypted page manifest is malformed.')
  const keyId=parts[2]
  const iv=Buffer.from(parts[3],'base64url')
  const tag=Buffer.from(parts[4],'base64url')
  const ciphertext=Buffer.from(parts.slice(5).join(':'),'base64url')
  const response=await fetch(BASE+'/api/chapter-crypto',{headers:{...apiHeaders(),accept:'application/json'},signal:AbortSignal.timeout(30000)})
  if(!response.ok) throw new Error('MangaHub chapter crypto HTTP '+response.status)
  const crypto=await response.json()
  const encoded=crypto?.keys?.[keyId] || ((crypto?.keyId==null||crypto?.keyId===keyId)?crypto?.key:null)
  if(!encoded) throw new Error('MangaHub chapter crypto key was not found.')
  const key=Buffer.from(encoded,'base64url')
  const decipher=createDecipheriv('aes-'+(key.length*8)+'-gcm',key,iv)
  decipher.setAuthTag(tag)
  return Buffer.concat([decipher.update(ciphertext),decipher.final()]).toString('utf8')
}

async function pagesFor(chapter){
  const {slug,number}=parseChapter(chapter)
  if(!slug||!Number.isFinite(number)) throw new Error('MangaHub chapter reference is invalid.')
  const q='{ chapter(x: '+SOURCE+', slug: "'+esc(slug)+'", number: '+number+') { pages mangaID number } }'
  const data=await graph(q,BASE+'/chapter/'+slug+'/chapter-'+number)
  const chapterData=data?.chapter
  if(!chapterData?.pages) throw new Error('MangaHub returned no chapter pages.')
  let raw=String(chapterData.pages)
  if(raw.startsWith('enc:v1')) raw=await decryptPages(raw)
  let parsed
  try{parsed=JSON.parse(raw)}catch{throw new Error('MangaHub page manifest is invalid JSON.')}
  let paths=[]
  if(Array.isArray(parsed)) paths=parsed
  else if(parsed&&Array.isArray(parsed.i)) paths=parsed.i.map(x=>String(parsed.p||'')+x)
  else if(parsed&&typeof parsed==='object') paths=Object.values(parsed)
  const pages=paths.map(path=>{
    const value=String(path||'')
    const url=/^https?:\/\//i.test(value)?value:CDN+'/'+value.replace(/^\//,'')
    return {url,headers:{Referer:BASE+'/',origin:BASE}}
  }).filter(x=>x.url)
  if(!pages.length) throw new Error('MangaHub resolved no page images.')
  return pages
}

export default {
  id:'mangahub',name:'MangaHub',
  description:'Direct MangaHub GraphQL source with current API-key refresh and AES-GCM page-manifest support.',
  fallbackOrder:24,brandAliases:['MangaHub.io','Manga Hub'],
  async run({action,query,item,chapter,chapterId,range,context}){
    if(action==='search'||action==='browse') return searchManga(action==='search'?String(query||'').trim():'')
    if(action==='chapters') return details(item)
    if(action==='options') return {qualities:['source'],deliveries:['document']}
    if(action==='download'){
      const target=chapter||{id:chapterId,url:chapterId,title:'Chapter'}
      return deliverCbz(context,{pages:await pagesFor(target),title:item?.title||'MangaHub',chapterTitle:target.title||('Chapter '+target.number)})
    }
    if(action==='downloadRange') return deliverRange({listChapters:async()=> (await details(item)).chapters,resolvePages:pagesFor,context,item,range})
    throw new Error('Unsupported MangaHub action: '+action)
  },
  _probe:{searchManga,chaptersFor:details,pagesFor},
}
