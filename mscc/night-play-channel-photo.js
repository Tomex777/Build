// Download an owned WhatsApp Channel's picture through an authenticated NIGHT
// Baileys socket. Never expose a transient WhatsApp media URL to web clients.
// Background only: web requests do not wait on WhatsApp's image infrastructure.
const CHANNEL=/^\d{8,25}@newsletter$/
const MAX_BYTES=98304
const HOST=host=>host==='whatsapp.net'||host.endsWith('.whatsapp.net')||host==='whatsapp.com'||host.endsWith('.whatsapp.com')||host==='fbcdn.net'||host.endsWith('.fbcdn.net')
function safeUrl(value){
 try{
  const u=new URL(String(value||''))
  return u.protocol==='https:'&&!u.username&&!u.password&&HOST(u.hostname.toLowerCase())?u.href:''
 }catch{return ''}
}
function deadline(promise,ms){
 let timer
 return Promise.race([
  Promise.resolve(promise),
  new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('WhatsApp picture timeout')),ms)})
 ]).finally(()=>clearTimeout(timer))
}
async function imageFromUrl(raw,fetcher){
 let url=safeUrl(raw)
 if(!url)return null
 for(let redirect=0;redirect<3;redirect++){
  const result=await deadline(fetcher(url,{redirect:'manual',signal:AbortSignal.timeout(3500)}),3800)
  if([301,302,303,307,308].includes(result.status)){
   const location=result.headers?.get?.('location')
   url=safeUrl(new URL(location,url).href)
   if(!url)return null
   continue
  }
  if(!result.ok)return null
  const mime=String(result.headers?.get?.('content-type')||'').split(';')[0].toLowerCase()
  if(!['image/png','image/jpeg'].includes(mime))return null
  if(Number(result.headers?.get?.('content-length')||0)>MAX_BYTES)return null
  let size=0;const chunks=[]
  for await(const chunk of result.body){
   size+=chunk.length
   if(size>MAX_BYTES)return null
   chunks.push(Buffer.from(chunk))
  }
  const bytes=Buffer.concat(chunks)
  if(!bytes.length)return null
  return bytes
 }
 return null
}
function fromMeta(meta){
 const p=meta?.picture||meta?.thread_metadata?.picture||meta?.preview
 if(typeof p==='string')return p
 if(typeof p?.url==='string')return p.url
 if(typeof p?.direct_url==='string')return p.direct_url
 return ''
}
export function createNightPlayChannelPhotoSync(play,accounts,{fetcher=fetch,now=Date.now,cooldownMs=12*3600000}={}){
 const recent=new Map(),inflight=new Map()
 async function refresh(userId,channelId){
  if(!CHANNEL.test(channelId)||!play.channels.canUsePhoto(userId,channelId))return false
  const key=userId+'|'+channelId
  if(inflight.has(key))return inflight.get(key)
  if(recent.has(key)&&now()-recent.get(key)<cooldownMs)return false
  recent.set(key,now())
  const job=(async()=>{
   const accountId=play.channels.photoAccount(userId,channelId)
   if(!accountId)return false
   const account=accounts.get(accountId)
   const sock=account?.connected&&account.sock
   if(!sock)return false
   let url=''
   if(typeof sock.profilePictureUrl==='function'){
    try{url=await deadline(sock.profilePictureUrl(channelId,'preview'),2000)}catch{}
   }
   if(!safeUrl(url)&&typeof sock.newsletterMetadata==='function'){
    try{url=fromMeta(await deadline(sock.newsletterMetadata('jid',channelId),2200))}catch{}
   }
   if(!safeUrl(url))return false
   try{
    const bytes=await imageFromUrl(url,fetcher)
    return bytes?play.channels.savePhoto(userId,channelId,bytes):false
   }catch{return false}
  })()
  inflight.set(key,job)
  try{return await job}finally{inflight.delete(key)}
 }
 // Process no more than three owned channels per request, serially, with all
 // work asynchronous. Existing photos remain visible while refreshing.
 return async(userId,channels=[])=>{
  const selected=channels.filter(c=>c?.status==='connected'&&CHANNEL.test(c.id)).slice(0,3)
  for(const c of selected)await refresh(userId,c.id)
 }
}
