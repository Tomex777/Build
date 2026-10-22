'use strict';
const { createHash }=require('node:crypto');
const CATEGORIES=Object.freeze(['relatable','dark','wholesome','anime','nigerian','animals','relationships','other']);
const SIMPLE_TAGS=new Set(['casting','releases','trailers','renewals','romance','platforms','other']);
const IGNORED_QUERY=/^(utm_.+|fbclid|gclid|ref|ref_src|source)$/i;
const HTTPS=/^https:\/\//i;
const WEIGHTS=Object.freeze({interest:22,quality:22,engagement:14,feedback:20,originality:14,freshness:8});
const clamp=(v,min=0,max=1)=>Math.min(max,Math.max(min,Number.isFinite(Number(v))?Number(v):min));
const tokens=value=>String(value||'').normalize('NFKC').toLowerCase().replace(/[\p{P}\p{S}]+/gu,' ').split(/\s+/).filter(Boolean);
function safeUrl(value){
 if(typeof value!=='string'||value.length>2048||!HTTPS.test(value))return null;
 try{const u=new URL(value);if(u.username||u.password||u.port||u.hostname==='localhost'||u.hostname.endsWith('.local')||/^\d+(?:\.\d+){3}$/.test(u.hostname)||u.hostname.includes('['))return null;
 u.hash='';for(const k of [...u.searchParams.keys()])if(IGNORED_QUERY.test(k))u.searchParams.delete(k);u.searchParams.sort();return u.toString();
 }catch{return null}
}
const identifier=(prefix,value)=>prefix+'_'+createHash('sha256').update(String(value)).digest('hex').slice(0,24);
function normalizeMeme(record,source){
 if(!record||typeof record!=='object')return null;
 const url=safeUrl(record.url),mediaUrl=safeUrl(record.mediaUrl),sourceUrl=safeUrl(record.sourceUrl||record.url);
 if(!url||!mediaUrl||!sourceUrl||!/^image\/(?:jpeg|png|webp)$/.test(record.mime||''))return null;
 if(record.over18||record.removed||record.isVideo||record.isGallery||record.unsafe===true)return null;
 const category=CATEGORIES.includes(record.category)?record.category:'other';
 const age=Date.parse(record.createdAt||'');if(!Number.isFinite(age))return null;
 const rights=record.rights==='cleared'&&source?.rightsVerified===true?'cleared':'unknown';
 return Object.freeze({id:identifier('meme',String(source?.id||'source')+'|'+String(record.externalId||url)),kind:'meme',
 category,caption:String(record.caption||'').slice(0,800),createdAt:new Date(age).toISOString(),
 media:{url:mediaUrl,mime:record.mime,width:Math.max(0,Math.floor(Number(record.width)||0)),height:Math.max(0,Math.floor(Number(record.height)||0)),byteLength:Math.max(0,Math.floor(Number(record.byteLength)||0)),perceptualHash:/^[\da-f]{16}$/i.test(record.perceptualHash||'')?record.perceptualHash.toLowerCase():null},
 source:{id:String(source?.id||''),provider:String(source?.provider||'unknown'),url:sourceUrl,externalId:String(record.externalId||''),author:String(record.author||'').slice(0,120),attributionRequired:true,rights,license:record.license||null},
 metrics:{upvotes:Math.max(0,Number(record.upvotes)||0),ratio:clamp(record.upvoteRatio||0),legibility:clamp(record.legibility??.5),originality:clamp(record.originality??.65)},
 publishable:rights==='cleared',reviewOnly:rights!=='cleared',publishBlockedReason:rights==='cleared'?null:'rights_unverified'});
}
function hashNear(a,b,distance=5){
 if(!/^[a-f\d]{16}$/i.test(a||'')||!/^[a-f\d]{16}$/i.test(b||''))return false;
 let bits=BigInt('0x'+a)^BigInt('0x'+b),count=0;
 while(bits){bits&=bits-1n;if(++count>distance)return false}return true;
}
function isDuplicate(a,b){
 if(a.id===b.id||a.media?.url===b.media?.url||a.source?.url===b.source?.url)return true;
 return hashNear(a.media?.perceptualHash,b.media?.perceptualHash);
}
function memeScore(item,options={}){
 const selected=new Set(options.categories||[]);
 const categoryMatch=selected.has(item.category)?1:selected.size?0:.55;
 const px=item.media.width*item.media.height,resolution=clamp((px-120000)/650000);
 const ratio=item.media.width/Math.max(1,item.media.height),shape=ratio>=.55&&ratio<=2.6?1:.15;
 const quality=resolution*.45+item.metrics.legibility*.45+shape*.1;
 const votes=clamp(Math.log10(1+item.metrics.upvotes)/4.2);
 const engagement=votes*.65+item.metrics.ratio*.35;
 const feedback=clamp((options.feedback?.[item.category]??.5));
 const hours=Math.max(0,((options.now||Date.now())-Date.parse(item.createdAt))/3600000);
 const freshness=clamp(1-Math.log1p(hours/24)/Math.log1p(365*6));
 return Math.round(100*(WEIGHTS.interest*categoryMatch+WEIGHTS.quality*quality+WEIGHTS.engagement*engagement+WEIGHTS.feedback*feedback+WEIGHTS.originality*item.metrics.originality+WEIGHTS.freshness*freshness)/Object.values(WEIGHTS).reduce((a,b)=>a+b,0))/100;
}
function chooseFourMemes(items,{enabled=false,categories=[],allowDark=false,used=[],feedback={},now=Date.now()}={}){
 if(enabled!==true)return [];
 const selected=(Array.isArray(categories)?categories:[]).filter(c=>CATEGORIES.includes(c));
 used=Array.isArray(used)?used:[];
 const blocked=new Set(used.map(x=>typeof x==='string'?x:x.id));
 const previouslyUsed=used.filter(x=>x&&typeof x==='object'&&x.kind==='meme');
 const candidates=(Array.isArray(items)?items:[]).filter(i=>i&&i.kind==='meme'&&(!selected.length||selected.includes(i.category))&&
 (i.category!=='dark'||allowDark===true)&&!blocked.has(i.id)&&!previouslyUsed.some(prev=>isDuplicate(i,prev)));
 const ranked=candidates.map(i=>({...i,score:memeScore(i,{categories:selected,feedback,now})})).sort((a,b)=>b.score-a.score||a.id.localeCompare(b.id));
 const out=[],categoryCount=new Map(),sourceCount=new Map();
 const select=strict=>{
  for(const item of ranked){
   if(out.length===4)break;
   if(out.some(other=>isDuplicate(item,other)))continue;
   if(strict&&((categoryCount.get(item.category)||0)>=2||(sourceCount.get(item.source.id)||0)>=2))continue;
   out.push(item);categoryCount.set(item.category,(categoryCount.get(item.category)||0)+1);
   sourceCount.set(item.source.id,(sourceCount.get(item.source.id)||0)+1);
  }
 };
 // Prefer diversity, but always fill four from a single selected category when possible.
 select(true);select(false);
 return out;
}
function normalizeNews(record,source,{now=Date.now(),maxAgeDays=14}={}){
 if(!record||typeof record!=='object'||!source?.trustedHost)return null;
 const url=safeUrl(record.url);if(!url||new URL(url).hostname!==source.trustedHost)return null;
 const published=Date.parse(record.publishedAt||'');
 if(!Number.isFinite(published)||published>now+3600000||published<now-maxAgeDays*86400000)return null;
 const title=String(record.title||'').replace(/\s+/g,' ').trim();if(title.length<8||title.length>240)return null;
 const tags=[...new Set((Array.isArray(record.tags)?record.tags:[]).filter(t=>SIMPLE_TAGS.has(t)))];
 const verification=record.rumor===true?'rumor':source.official===true&&record.confirmed===true?'official':'unverified';
 const show=String(record.show||'').trim().slice(0,100),event=String(record.event||'update').trim().slice(0,40);
 const summary=verification==='official'?(show?show+': '+event+'. Official announcement linked below.':'Official '+event+' announcement. See the source for details.'):'Details require review. See the attributed source before publishing.';
 const normalized=tokens(title).filter(t=>!['the','a','an','of','for','on','in','new','from','and','to'].includes(t));
 const explicitKey=typeof record.storyKey==='string'&&/^[A-Za-z0-9:._-]{4,120}$/.test(record.storyKey)?record.storyKey:null;
 const fingerprint=identifier('story',explicitKey||((show||'general').toLowerCase()+'|'+event+'|'+normalized.slice(0,10).sort().join(' ')));
 return Object.freeze({id:identifier('news',url),kind:'news',title,summary,publishedAt:new Date(published).toISOString(),show,tags,genre:String(record.genre||'').slice(0,48),platform:String(record.platform||'').slice(0,80),availabilityRegion:String(record.availabilityRegion||'').slice(0,80),spoiler:record.spoiler===true,eventDate:Number.isFinite(Date.parse(record.eventDate||''))?new Date(record.eventDate).toISOString():null,verification,requiresReview:verification!=='official'||record.spoiler===true,fingerprint,
 source:{id:String(source.id||''),name:String(source.name||''),url,provider:source.provider||'rss',attributionRequired:true},autoEligible:verification==='official'&&record.spoiler!==true});
}
function chooseNews(items,{enabled=false,interests=[],shows=[],includeSpoilers=false,now=Date.now(),maxAgeDays=14,limit=4}={}){
 if(!enabled)return [];
 const wanted=new Set((Array.isArray(interests)?interests:[]).filter(x=>SIMPLE_TAGS.has(x))),followed=new Set((Array.isArray(shows)?shows:[]).map(x=>tokens(x).join(' ')).filter(Boolean));
 const unique=new Map();
 for(const item of (Array.isArray(items)?items:[])){
 if(!item||item.kind!=='news'||(item.spoiler&&includeSpoilers!==true))continue;
    const published=Date.parse(item.publishedAt);
    if(!Number.isFinite(published)||published>now+3600000||published<now-Math.max(1,maxAgeDays)*86400000)continue;
 const match=(!wanted.size&&!followed.size)||item.tags.some(t=>wanted.has(t))||followed.has(tokens(item.show).join(' '));
 if(!match)continue;
 const key=item.fingerprint,prior=unique.get(key);
 if(!prior||(item.verification==='official'&&prior.verification!=='official')||(item.verification===prior.verification&&item.publishedAt>prior.publishedAt))unique.set(key,item);
 }
 return [...unique.values()].sort((a,b)=>b.publishedAt.localeCompare(a.publishedAt)||a.id.localeCompare(b.id)).slice(0,Math.min(20,Math.max(1,limit)));
}
module.exports={CATEGORIES,SIMPLE_TAGS,safeUrl,normalizeMeme,normalizeNews,memeScore,chooseFourMemes,chooseNews,isDuplicate,hashNear};
