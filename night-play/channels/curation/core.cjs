'use strict';
const { createHash }=require('node:crypto');
const { classifyHeadline }=require('./kdrama.cjs');
const CATEGORIES=Object.freeze(['relatable','dark','wholesome','anime','nigerian','animals','relationships','other']);
const SIMPLE_TAGS=new Set(['casting','releases','trailers','renewals','romance','platforms','other']);
const IGNORED_QUERY=/^(utm_.+|fbclid|gclid|ref|ref_src|source)$/i;
const HTTPS=/^https:\/\//i;
const WEIGHTS=Object.freeze({interest:22,quality:22,engagement:14,feedback:20,originality:14,freshness:8});
const clamp=(v,min=0,max=1)=>Math.min(max,Math.max(min,Number.isFinite(Number(v))?Number(v):min));
const boundedPositiveInt=(v,cap)=>Number.isFinite(Number(v))?Math.max(0,Math.min(cap,Math.floor(Number(v)))):0;
const tokens=value=>String(value||'').normalize('NFKC').toLowerCase().replace(/[\p{P}\p{S}]+/gu,' ').split(/\s+/).filter(Boolean);
function safeUrl(value){
 if(typeof value!=='string'||value.length>2048||!HTTPS.test(value))return null;
 try{const u=new URL(value);if(u.username||u.password||u.port||u.hostname==='localhost'||(u.hostname.endsWith('.local')||u.hostname.endsWith('.localhost'))||/^\d+(?:\.\d+){3}$/.test(u.hostname)||u.hostname.includes('['))return null;
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
 media:Object.freeze({url:mediaUrl,mime:record.mime,width:boundedPositiveInt(record.width,16000),height:boundedPositiveInt(record.height,16000),byteLength:boundedPositiveInt(record.byteLength,64*1024*1024),perceptualHash:/^[\da-f]{16}$/i.test(record.perceptualHash||'')?record.perceptualHash.toLowerCase():null}),
 source:Object.freeze({id:String(source?.id||'').slice(0,120),provider:String(source?.provider||'unknown').slice(0,60),url:sourceUrl,externalId:String(record.externalId||'').slice(0,180),author:String(record.author||'').slice(0,120),attributionRequired:true,rights,license:typeof record.license==='string'?record.license.slice(0,160):null}),
 metrics:Object.freeze({upvotes:boundedPositiveInt(record.upvotes,1000000000),ratio:clamp(record.upvoteRatio||0),legibility:clamp(record.legibility??.5),originality:clamp(record.originality??.65)}),
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
 const selected=new Set(Array.isArray(options.categories)?options.categories:[]);
 const categoryMatch=selected.has(item.category)?1:selected.size?0:.55;
 const px=item.media.width*item.media.height,resolution=clamp((px-120000)/650000);
 const ratio=item.media.width/Math.max(1,item.media.height),shape=ratio>=.55&&ratio<=2.6?1:.15;
 const quality=resolution*.45+item.metrics.legibility*.45+shape*.1;
 const votes=clamp(Math.log10(1+item.metrics.upvotes)/4.2);
 const engagement=votes*.65+item.metrics.ratio*.35;
 const feedbackByCategory=clamp((options.feedback&&typeof options.feedback==='object'?options.feedback[item.category]:undefined)??.5);
 const sourcePreference=clamp(options.feedback?.sources?.[item.source.id]??feedbackByCategory);
 const feedback=feedbackByCategory*.7+sourcePreference*.3;
 const hours=Math.max(0,((options.now||Date.now())-Date.parse(item.createdAt))/3600000);
 const freshness=clamp(1-Math.log1p(hours/24)/Math.log1p(365*6));
 return Math.round(100*(WEIGHTS.interest*categoryMatch+WEIGHTS.quality*quality+WEIGHTS.engagement*engagement+WEIGHTS.feedback*feedback+WEIGHTS.originality*item.metrics.originality+WEIGHTS.freshness*freshness)/Object.values(WEIGHTS).reduce((a,b)=>a+b,0))/100;
}
function chooseFourMemes(items,{enabled=false,categories=[],allowDark=false,used=[],feedback={},now=Date.now()}={}){
 if(enabled!==true)return [];
 const selected=(Array.isArray(categories)?categories:[]).filter(c=>CATEGORIES.includes(c));
 used=Array.isArray(used)?used:[];
 const blocked=new Set(used.map(x=>typeof x==='string'?x:x&&typeof x.id==='string'?x.id:null).filter(Boolean));
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
 const url=safeUrl(record.url);
 const trustedHosts=Array.isArray(source.trustedHosts)?source.trustedHosts:[source.trustedHost];
 if(!url||!trustedHosts.includes(new URL(url).hostname))return null;
 const published=Date.parse(record.publishedAt||'');
 if(!Number.isFinite(published)||published>now+3600000||published<now-maxAgeDays*86400000)return null;
 const title=String(record.title||'').replace(/\s+/g,' ').trim();if(title.length<8||title.length>240)return null;
 const tags=Object.freeze([...new Set((Array.isArray(record.tags)?record.tags:[]).filter(t=>SIMPLE_TAGS.has(t)))]);
 const headline=classifyHeadline(title);
 const spoiler=record.spoiler===true||headline.spoiler;
 const rumor=record.rumor===true||headline.rumor;
 const verification=rumor?'rumor':source.official===true&&record.confirmed===true?'official':'unverified';
 const show=String(record.show||'').trim().slice(0,100),event=String(record.event||'update').trim().slice(0,40);
 const summary=verification==='official'?(show?show+': '+event+'. Official announcement linked below.':'Official '+event+' announcement. See the source for details.'):'Details require review. See the attributed source before publishing.';
 const normalized=tokens(title).filter(t=>!['the','a','an','of','for','on','in','new','from','and','to'].includes(t));
 const explicitKey=typeof record.storyKey==='string'&&/^[A-Za-z0-9:._-]{4,120}$/.test(record.storyKey)?record.storyKey:null;
 const fingerprint=identifier('story',explicitKey||((show||'general').toLowerCase()+'|'+event+'|'+normalized.slice(0,10).sort().join(' ')));
 return Object.freeze({id:identifier('news',url),kind:'news',title,summary,publishedAt:new Date(published).toISOString(),show,tags,genre:verification==='official'?String(record.genre||'').slice(0,48):'',platform:verification==='official'?String(record.platform||'').slice(0,80):'',availabilityRegion:verification==='official'?String(record.availabilityRegion||'').slice(0,80):'',spoiler,event,eventDate:Number.isFinite(Date.parse(record.eventDate||''))?new Date(record.eventDate).toISOString():null,verification,requiresReview:verification!=='official'||spoiler,fingerprint,
 source:Object.freeze({id:String(source.id||'').slice(0,120),name:String(source.name||'').slice(0,120),url,provider:String(source.provider||'rss').slice(0,60),attributionRequired:true}),autoEligible:verification==='official'&&!spoiler});
}
// Only collapse similarly dated, same-show/same-event headlines when the editorial
// tokens strongly overlap. A matching server-confirmed storyKey takes precedence.
const NEWS_STOP_WORDS=new Set(['the','a','an','of','for','on','in','new','from','and','to','by','as','at','with','is','its','will','has','official','officially','report','says','said','announced','announces','announcement']);
function storyWords(item){
 const out=new Set();
 for(let word of tokens(item.title)){
  if(NEWS_STOP_WORDS.has(word))continue;
  word=word.replace(/^(renewal|renewed|renews)$/,'renew')
    .replace(/^(confirmed|confirms|confirmation)$/,'confirm')
    .replace(/^(premieres|premiered|premiering)$/,'premiere')
    .replace(/^(releases|released)$/,'release');
  if(word.length>1||/^\d+$/.test(word))out.add(word);
 }
 return out;
}
function nearSameNews(a,b){
 if(a.fingerprint&&a.fingerprint===b.fingerprint)return true;
 if(!a.show||!b.show||tokens(a.show).join(' ')!==tokens(b.show).join(' ')||a.event!==b.event)return false;
 const timeA=Date.parse(a.publishedAt),timeB=Date.parse(b.publishedAt);
 if(!Number.isFinite(timeA)||!Number.isFinite(timeB)||Math.abs(timeA-timeB)>72*3600000)return false;
 // Different episode/season/year numbers are distinct news even if outlets use nearly identical wording.
 const numericA=tokens(a.title).filter(x=>/^\d+$/.test(x)).sort().join('|');
 const numericB=tokens(b.title).filter(x=>/^\d+$/.test(x)).sort().join('|');
 if(numericA!==numericB)return false;
 const wordsA=storyWords(a),wordsB=storyWords(b),max=Math.max(wordsA.size,wordsB.size);
 if(max<4)return false;
 let same=0;for(const word of wordsA)if(wordsB.has(word))same++;
 return same>=4&&same/max>=.72;
}
function chooseNews(items,{enabled=false,interests=[],shows=[],genres=[],platforms=[],regions=[],includeSpoilers=false,now=Date.now(),maxAgeDays=14,limit=4,mode='review'}={}){
 if(enabled!==true||!['review','automatic'].includes(mode))return [];
 const wanted=new Set((Array.isArray(interests)?interests:[]).slice(0,30).filter(x=>SIMPLE_TAGS.has(x))),followed=new Set((Array.isArray(shows)?shows:[]).slice(0,30).map(x=>tokens(x).join(' ')).filter(Boolean));
 const normalizePreferences=values=>new Set((Array.isArray(values)?values:[]).slice(0,30).map(x=>tokens(x).join(' ')).filter(Boolean));
 const preferredGenres=normalizePreferences(genres),preferredPlatforms=normalizePreferences(platforms),preferredRegions=normalizePreferences(regions);
 const matchesMetadata=(value,options)=>String(value||'').split(/[,/|]/).some(part=>options.has(tokens(part).join(' ')));
 const unique=new Map();
 for(const item of (Array.isArray(items)?items:[])){
  if(!item||item.kind!=='news'||(item.spoiler&&includeSpoilers!==true))continue;
  // Automatic is a deliberately separate fail-closed lane; owner review does not
  // imply it is safe to auto-publish a story later.
  if(mode==='automatic'&&!(item.autoEligible===true&&item.verification==='official'&&item.requiresReview===false&&item.spoiler!==true))continue;
  const published=Date.parse(item.publishedAt);
  if(!Number.isFinite(published)||published>now+3600000||published<now-Math.max(1,maxAgeDays)*86400000)continue;
  const targetSelected=!!(wanted.size||followed.size||preferredGenres.size||preferredPlatforms.size);
  const match=!targetSelected||(Array.isArray(item.tags)&&item.tags.some(t=>wanted.has(t)))||followed.has(tokens(item.show).join(' '))||matchesMetadata(item.genre,preferredGenres)||matchesMetadata(item.platform,preferredPlatforms);
  if(!match)continue;
  // Region is a strict verified-availability filter, not an OR with headline topics.
  if(preferredRegions.size&&(item.verification!=='official'||!matchesMetadata(item.availabilityRegion,preferredRegions)))continue;
  const key=item.fingerprint||item.id,prior=unique.get(key);
  if(!prior||(item.verification==='official'&&prior.verification!=='official')||(item.verification===prior.verification&&item.publishedAt>prior.publishedAt))unique.set(key,item);
 }
 const grouped=[];
 // Give official confirmations priority when merging near-duplicate independent reporting.
 const candidates=[...unique.values()].sort((a,b)=>(b.verification==='official')-(a.verification==='official')||b.publishedAt.localeCompare(a.publishedAt)||a.id.localeCompare(b.id));
 for(const item of candidates)if(!grouped.some(previous=>nearSameNews(previous,item)))grouped.push(item);
 return grouped.sort((a,b)=>b.publishedAt.localeCompare(a.publishedAt)||a.id.localeCompare(b.id)).slice(0,Math.min(20,Math.max(1,limit)));
}

function deriveHumanFeedback(reviewHistory){
 const counters=Object.create(null),sourceCounters=Object.create(null),feedback=Object.create(null),sources=Object.create(null);
 let sourceCount=0;
 // Caller MUST pass authenticated, per-owner/per-channel review events, never
 // crowd or global browsing data. Bound memory, record volume and source variety.
 for(const record of (Array.isArray(reviewHistory)?reviewHistory:[]).slice(-5000)){
  const category=record?.category,decision=record?.decision;
  if(!CATEGORIES.includes(category)||!['approved','declined'].includes(decision))continue;
  if(!counters[category])counters[category]={approved:0,declined:0};
  counters[category][decision]++;
  const sourceId=record?.sourceId;
  if(typeof sourceId==='string'&&/^[A-Za-z0-9:._/\\-]{2,120}$/.test(sourceId)&&
    (Object.hasOwn(sourceCounters,sourceId)||sourceCount<256)){
   if(!Object.hasOwn(sourceCounters,sourceId)){sourceCounters[sourceId]={approved:0,declined:0};sourceCount++;}
   sourceCounters[sourceId][decision]++;
  }
 }
 for(const category of Object.keys(counters)){
  const {approved,declined}=counters[category];
  // Neutral Bayesian prior: a few clicks should not erase unexplored categories.
  feedback[category]=(approved+3)/(approved+declined+6);
 }
 for(const source of Object.keys(sourceCounters)){
  const {approved,declined}=sourceCounters[source];
  sources[source]=(approved+3)/(approved+declined+6);
 }
 Object.defineProperty(feedback,'sources',{value:Object.freeze(sources),enumerable:true});
 return Object.freeze(feedback);
}

module.exports={deriveHumanFeedback,CATEGORIES,SIMPLE_TAGS,safeUrl,normalizeMeme,normalizeNews,memeScore,chooseFourMemes,chooseNews,isDuplicate,hashNear};
