import { absolute, chapterNumber, createNyoraBridgeSource, deliverCbz, deliverRange, loadDocument, normalizedChapter } from './_common.js'

const bridge=createNyoraBridgeSource({
  id:'comix',
  name:'Comix',
  aliases:['Comix.to','Comix.ws'],
  fallbackOrder:20,
  description:'Comix with Nyora parser plus direct rendered-page fallback.',
})
const MIRRORS=['https://comix.to','https://comix.ws']

function titleFrom($,el){
  return $(el).attr('title') || $(el).find('h1,h2,h3,h4,.title,.name').first().text().trim() || $(el).text().replace(/\s+/g,' ').trim()
}

async function directSearch(query=''){
  let last
  for(const root of MIRRORS){
    try{
      const url=root+'/browse'+(query?'?keyword='+encodeURIComponent(query):'')
      const {$}=await loadDocument(url)
      const items=[],seen=new Set()
      $('a[href*="/title/"]').each((_,el)=>{
        const href=absolute($(el).attr('href'),root)
        let parsed
        try{parsed=new URL(href)}catch{return}
        const parts=parsed.pathname.split('/').filter(Boolean)
        if(parts[0]!=='title'||parts.length!==2||seen.has(href)) return
        const title=titleFrom($,el)
        if(!title||title.length>220) return
        seen.add(href)
        items.push({id:href,url:href,title,description:'Comix'})
      })
      if(items.length) return {items:items.slice(0,50)}
    }catch(error){last=error}
  }
  if(last) throw last
  return {items:[]}
}

async function directChapters(item){
  const start=String(item?.url||item?.id||'')
  const roots=[start,...MIRRORS.map(root=>{
    try{const p=new URL(start).pathname;return root+p}catch{return ''}
  })].filter(Boolean)
  let last
  for(const url of [...new Set(roots)]){
    try{
      const {$}=await loadDocument(url)
      const chapters=[],seen=new Set()
      $('a[href*="/title/"]').each((index,el)=>{
        const href=absolute($(el).attr('href'),url)
        let parsed
        try{parsed=new URL(href)}catch{return}
        const parts=parsed.pathname.split('/').filter(Boolean)
        if(parts[0]!=='title'||parts.length<3||seen.has(href)) return
        const label=titleFrom($,el)
        const lastPart=parts.at(-1)
        if(!/chapter|^\d+[-_]/i.test(lastPart)&&!/\bch(?:apter)?\.?\s*\d/i.test(label)) return
        seen.add(href)
        chapters.push(normalizedChapter({id:href,url:href,title:label||('Chapter '+(index+1)),number:chapterNumber(label||lastPart,index+1)},index))
      })
      if(chapters.length) return {title:item?.title||$('h1').first().text().trim()||'Comix',chapters}
    }catch(error){last=error}
  }
  if(last) throw last
  return {title:item?.title||'Comix',chapters:[]}
}

function pagesFromPayload(payload,url){
  const pageData=payload?.result?.pages
  if(!pageData?.items?.length) return []
  const base=String(pageData.baseUrl||'').replace(/\/$/,'')
  return pageData.items.map((img,index)=>{
    let full=/^https?:\/\//i.test(String(img?.url||''))?String(img.url):base+'/'+String(img?.url||'').replace(/^\//,'')
    if(Number(img?.s)===1&&!/[?&]v3(?:[=&]|$)/.test(full)) full+=(full.includes('?')?'&':'?')+'v3'
    return {url:full,headers:{Referer:url,Accept:'image/avif,image/webp,*/*'}}
  }).filter(page=>page.url)
}
async function directPages(chapter){
  const url=String(chapter?.url||chapter?.id||'')
  const {$}=await loadDocument(url)
  try{
    const raw=$('script#initial-data').first().text()
    const initial=raw?JSON.parse(raw):null
    for(const value of Object.values(initial?.queries||{})){
      const resolved=pagesFromPayload(value,url)
      if(resolved.length) return resolved
    }
  }catch{}
  const pages=[],seen=new Set()
  const add=el=>{
    const raw=$(el).attr('data-src')||$(el).attr('data-original')||$(el).attr('src')||String($(el).attr('srcset')||'').split(',')[0]?.trim().split(/\s+/)[0]
    const src=absolute(raw,url)
    if(!src||seen.has(src)||/^data:/i.test(src)) return
    const alt=String($(el).attr('alt')||'')
    const cls=String($(el).attr('class')||'')
    if(!/page/i.test(alt+' '+cls)&&!$(el).closest('main,.reader,.chapter-reader,[class*=reader]').length) return
    seen.add(src)
    pages.push({url:src,headers:{Referer:url,Accept:'image/avif,image/webp,*/*'}})
  }
  $('img').each((_,el)=>add(el))
  if(!pages.length) throw new Error('Comix rendered reader returned no page images.')
  return pages
}

export default {
  ...bridge,
  description:'Comix direct rendered-page fallback with Nyora parser as an additional route.',
  async run(args){
    const {action,query,item,chapter,chapterId,range,context}=args
    try{
      const result=await bridge.run(args)
      if(action==='search'||action==='browse'){
        if(result?.items?.length) return result
      }else if(action==='chapters'){
        if(result?.chapters?.length) return result
      }else{
        return result
      }
    }catch{}

    if(action==='search'||action==='browse') return directSearch(action==='search'?String(query||'').trim():'')
    if(action==='chapters') return directChapters(item)
    if(action==='options') return {qualities:['source'],deliveries:['document']}
    if(action==='download'){
      const target=chapter||{id:chapterId,url:chapterId,title:'Chapter'}
      return deliverCbz(context,{pages:await directPages(target),title:item?.title||'Comix',chapterTitle:target.title||('Chapter '+target.number)})
    }
    if(action==='downloadRange') return deliverRange({listChapters:async()=> (await directChapters(item)).chapters,resolvePages:directPages,context,item,range})
    throw new Error('Unsupported Comix action: '+action)
  },
  _probe:{searchManga:directSearch,chaptersFor:directChapters,pagesFor:directPages},
}
