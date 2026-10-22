'use strict';
// Network adapters only discover candidates; they do NOT authorize, schedule or publish.
const {normalizeMeme,normalizeNews,safeUrl}=require('./core.cjs');
const HARD_CAP=1024*1024;
class SourceError extends Error{constructor(message,{status=0,retryAfterSeconds=0}={}){super(message);this.name='SourceError';this.status=status;this.retryAfterSeconds=retryAfterSeconds;}}
function validateEndpoint(url,hosts){
 const clean=safeUrl(url);if(!clean)throw new SourceError('Invalid HTTPS source endpoint.');
 if(!hosts.includes(new URL(clean).hostname))throw new SourceError('Source hostname is not allowlisted.');
 return clean;
}
async function boundedGet(url,{fetchImpl=globalThis.fetch,headers={},allowedHosts,timeoutMs=8000,maxBytes=HARD_CAP}={}){
 const endpoint=validateEndpoint(url,allowedHosts||[]);
 if(typeof fetchImpl!=='function')throw new SourceError('Network fetch is unavailable.');
 const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),Math.min(15000,Math.max(1000,timeoutMs)));
 try{
  const response=await fetchImpl(endpoint,{method:'GET',headers,redirect:'manual',signal:controller.signal});
  const header=response.headers?.get('retry-after')||'';
  const numeric=Number(header),retryValue=header&&Number.isFinite(numeric)?numeric:Math.ceil((Date.parse(header)-Date.now())/1000);
  const retryAfter=Math.min(3600,Math.max(0,Number.isFinite(retryValue)?retryValue:0));
  if(!response.ok)throw new SourceError(response.status===429?'Source rate-limited.':'Source request failed.',{status:response.status,retryAfterSeconds:retryAfter});
  if(Number(response.headers?.get('content-length')||0)>maxBytes)throw new SourceError('Source response exceeds size limit.');
  if(!response.body||typeof response.body.getReader!=='function'){
   const body=await response.text();if(Buffer.byteLength(body)>maxBytes)throw new SourceError('Source response exceeds size limit.');return body;
  }
  const reader=response.body.getReader(),buffers=[];let size=0;
  try{for(;;){const {value,done}=await reader.read();if(done)break;size+=value.byteLength;
   if(size>maxBytes)throw new SourceError('Source response exceeds size limit.');buffers.push(Buffer.from(value));}}
  finally{await reader.cancel().catch(()=>{});}
  return Buffer.concat(buffers,size).toString('utf8');
 }catch(e){if(e instanceof SourceError)throw e;if(controller.signal.aborted)throw new SourceError('Source request timed out.');throw new SourceError('Source temporarily unavailable.');}
 finally{clearTimeout(timer)}
}
function redditAdapter({tokenProvider,fetchImpl,claimQuota,userAgent,subreddits=[]}={}){
 if(typeof tokenProvider!=='function')throw new TypeError('An approved Reddit OAuth token provider is required.');
 // Reddit requires an accurately registered app identifier and accountable operator.
 // Never invent a production username or use an anonymous/generic crawler identity.
 if(typeof userAgent!=='string'||!/^(?:web|android|ios):[A-Za-z0-9_.-]{3,100}:v?\d+(?:\.\d+){0,2} \(by \/u\/[A-Za-z0-9_-]{3,20}\)$/.test(userAgent))
  throw new TypeError('A registered Reddit app User-Agent in platform:app:version (by /u/operator) format is required.');
 if(!Array.isArray(subreddits))throw new TypeError('An array of approved subreddits is required.');
 if(typeof claimQuota!=='function')throw new TypeError('A shared app-wide Reddit API quota claim function is required.');
 const approved=new Set(subreddits.filter(s=>/^[A-Za-z0-9_]{3,21}$/.test(s)).map(s=>s.toLowerCase()));let nextAllowedAt=0;
 return Object.freeze({async discover({subreddit,category='other',limit=25,after=null,now=Date.now()}={}){
  if(!approved.has(String(subreddit).toLowerCase()))throw new SourceError('Subreddit not authorized for discovery.');
  if(now<nextAllowedAt)throw new SourceError('Source polling too frequent.',{status:429,retryAfterSeconds:Math.ceil((nextAllowedAt-now)/1000)});
  if(await claimQuota({provider:'reddit',subreddit,now})!==true)throw new SourceError('Reddit API quota exhausted.',{status:429,retryAfterSeconds:60});
  const token=await tokenProvider();if(typeof token!=='string'||token.length<12)throw new SourceError('Missing authorized Reddit OAuth token.');
  const params=new URLSearchParams({t:'all',limit:String(Math.max(1,Math.min(50,Math.trunc(limit))))});
  if(after&&/^t3_[A-Za-z0-9]{3,20}$/.test(after))params.set('after',after);
  const url='https://oauth.reddit.com/r/'+encodeURIComponent(subreddit)+'/top?'+params;
  nextAllowedAt=now+2000;let raw;
  try{raw=await boundedGet(url,{fetchImpl,allowedHosts:['oauth.reddit.com'],headers:{authorization:'Bearer '+token,'user-agent':userAgent,accept:'application/json'}});}
  catch(e){if(e.retryAfterSeconds)nextAllowedAt=Math.max(nextAllowedAt,now+e.retryAfterSeconds*1000);throw e;}
  let data;try{data=JSON.parse(raw)}catch{throw new SourceError('Source returned invalid JSON.');}
  const children=Array.isArray(data?.data?.children)?data.data.children:[];
  const source={id:'reddit:r/'+String(subreddit).toLowerCase(),provider:'reddit',rightsVerified:false};
  const items=children.map(child=>{const d=child?.data;
   if(!d||d.over_18||d.is_video||d.is_gallery||d.removed_by_category||d.spoiler)return null;
   const created=Number(d.created_utc);if(!Number.isFinite(created)||created<=0||created>8.64e12)return null;
   const mediaUrl=typeof d.url_overridden_by_dest==='string'?d.url_overridden_by_dest.replace(/&amp;/g,'&'):'';
   const mime=/\.(?:jpe?g)(?:\?|$)/i.test(mediaUrl)?'image/jpeg':/\.png(?:\?|$)/i.test(mediaUrl)?'image/png':/\.webp(?:\?|$)/i.test(mediaUrl)?'image/webp':'';
   // API user content is not a trusted download endpoint. Restrict discovery
   // to the requested subreddit permalink and Reddit-controlled still-image CDN.
   const permalink=typeof d.permalink==='string'?d.permalink:'';
   const matched=/^\/r\/([A-Za-z0-9_]{3,21})\/(?:[A-Za-z0-9_-]+\/?){1,8}$/.exec(permalink);
   if(!matched||matched[1].toLowerCase()!==String(subreddit).toLowerCase())return null;
   let imageHost='';try{imageHost=new URL(mediaUrl).hostname}catch{return null;}
   if(!['i.redd.it','preview.redd.it'].includes(imageHost))return null;
   const picture=d.preview?.images?.[0]?.source||{};
   return normalizeMeme({externalId:d.name,url:'https://www.reddit.com'+permalink,sourceUrl:'https://www.reddit.com'+permalink,
    mediaUrl,mime,width:picture.width,height:picture.height,category,createdAt:new Date(created*1000).toISOString(),
    upvotes:d.ups,upvoteRatio:d.upvote_ratio,author:d.author,caption:d.title,rights:'unknown',over18:d.over_18},source);
  }).filter(Boolean);
  return {items,after:/^t3_[A-Za-z0-9]{3,20}$/.test(data?.data?.after||'')?data.data.after:null,
   rightsNotice:'Rights unknown: explicit redistribution license/permission is required before publication.'};
 }});
}
function xmlDecode(value){return String(value||'').replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g,'$1').replace(/&(amp|lt|gt|quot|apos|#\d{1,7}|#x[\da-f]{1,6});/gi,(m,entity)=>{
 const basic={amp:'&',lt:'<',gt:'>',quot:'"',apos:"'"};if(basic[entity.toLowerCase()])return basic[entity.toLowerCase()];
 const cp=entity[1].toLowerCase()==='x'?parseInt(entity.slice(2),16):parseInt(entity.slice(1),10);
 return cp>0&&cp<=0x10FFFF&&!(cp>=0xD800&&cp<=0xDFFF)?String.fromCodePoint(cp):'';
 }).replace(/<[^>]*>/g,'').replace(/\s+/g,' ').trim();}
function extract(xml,field){const m=new RegExp('<'+field+'(?:\\s[^>]*)?>([\\s\\S]*?)<\\/'+field+'>','i').exec(xml);return m?xmlDecode(m[1]):'';}
function parseFeed(xml){
 if(typeof xml!=='string'||xml.length>HARD_CAP||/<!DOCTYPE|<!ENTITY/i.test(xml))throw new SourceError('Unsupported or unsafe XML feed.');
 if(!/<(?:rss|feed|rdf:RDF)\b/i.test(xml))throw new SourceError('Source is not an RSS/Atom feed.');
 const entries=[],pattern=/<(item|entry)(?:\s[^>]*)?>([\s\S]*?)<\/\1>/gi;let match;
 while((match=pattern.exec(xml))&&entries.length<60){
  const block=match[2];
  const link=/<link\b[^>]*\bhref=["']([^"']+)["'][^>]*\/?\s*>/i.exec(block)?.[1]||extract(block,'link');
  entries.push({title:extract(block,'title'),url:xmlDecode(link),publishedAt:extract(block,'pubDate')||extract(block,'published')||extract(block,'updated'),
  guid:extract(block,'guid')||extract(block,'id'),
  categories:[
   ...[...block.matchAll(/<category(?:\s[^>]*)?>([\s\S]*?)<\/category>/gi)].map(m=>xmlDecode(m[1])),
   ...[...block.matchAll(/<category\s+[^>]*\bterm=["']([^"']{1,120})["'][^>]*\/?>/gi)].map(m=>xmlDecode(m[1]))
  ].filter(Boolean).slice(0,30)});
 }return entries;
}
function rssNewsAdapter({id,name,url,articleHosts=[],official=false,fetchImpl,interpreter,now=()=>Date.now(),maxAgeDays=14}={}){
 const endpoint=safeUrl(url);if(!endpoint)throw new TypeError('HTTPS feed URL required.');
 const trustedHost=new URL(endpoint).hostname;
 const trustedHosts=[trustedHost,...(Array.isArray(articleHosts)?articleHosts:[]).filter(x=>typeof x==='string'&&/^[a-z0-9.-]{4,253}$/.test(x)&&!x.endsWith('.')&&!x.startsWith('.'))];
 if(typeof interpreter!=='function')throw new TypeError('A source-specific metadata interpreter is required.');
 let until=0,cache=null,inFlight=null;
 return Object.freeze({async discover(){
  const current=now();if(cache&&current<until)return cache;
  if(inFlight)return inFlight;
  // One bounded fetch per configured feed, even if many channel owners poll at once.
  const task=(async()=>{
  const xml=await boundedGet(endpoint,{fetchImpl,allowedHosts:[trustedHost],headers:{accept:'application/rss+xml, application/atom+xml, application/xml, text/xml'}});
  const source={id,name,trustedHost,trustedHosts,official,provider:'rss'};
  let interpreterErrors=0;
  const items=parseFeed(xml).map(row=>{
   try{
    const fields=interpreter(Object.freeze({...row}));if(!fields||typeof fields!=='object')return null;
    return normalizeNews({...row,...fields},source,{now:current,maxAgeDays});
   }catch{interpreterErrors++;return null;}
  }).filter(Boolean);
  if(interpreterErrors&&!items.length)throw new SourceError('News feed metadata interpreter failed.');
  cache=Object.freeze({items:Object.freeze(items),source:Object.freeze({id,name,url:endpoint,official}),fetchedAt:new Date(current).toISOString(),interpreterErrors});
  until=current+Math.min(15,Math.max(1,official?5:10))*60000;return cache;
  })();
  inFlight=task;
  try{return await task}finally{if(inFlight===task)inFlight=null;}
 }});
}

function licensedCatalogAdapter({id='licensed-library',loadRecords,verifyRights}={}){
 if(typeof loadRecords!=='function'||typeof verifyRights!=='function')throw new TypeError('A trusted approved-record loader and rights verifier are required.');
 return Object.freeze({async discover({category,limit=50}={}){
  const records=await loadRecords({category,limit:Math.max(1,Math.min(100,Math.trunc(limit)||50))});
  if(!Array.isArray(records))throw new SourceError('Approved media catalogue is unavailable.');
  const items=[];
  for(const row of records.slice(0,100)){
   if(!row||typeof row.assetId!=='string'||!row.assetId||typeof row.licenseEvidenceId!=='string'||!row.licenseEvidenceId)continue;
   const verified=await verifyRights(row.assetId,row.licenseEvidenceId);
   if(verified!==true)continue;
   const item=normalizeMeme({...row,rights:'cleared'},{id:'licensed:'+id,provider:'licensed-catalog',rightsVerified:true});
   if(item)items.push(item);
  }
  return {items,rightsNotice:'Verified by server-side rights records; publisher must revalidate before send.'};
 }});
}
module.exports={SourceError,boundedGet,parseFeed,redditAdapter,rssNewsAdapter,licensedCatalogAdapter};
