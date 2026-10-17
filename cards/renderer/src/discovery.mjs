/** Network artwork discovery. No search-engine thumbnails or unlicensed fan art. */
import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
export const KRITA_SOURCE='https://krita.org/en/about/mascot/';
const HOSTS=new Set(['krita.org','cdn.kde.org','commons.wikimedia.org','upload.wikimedia.org']);
const MAX_BYTES=48*1024*1024;
export const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
export function allowedURL(raw){
 const u=new URL(raw);
 if(u.protocol!=='https:'||u.username||u.password||u.port||!HOSTS.has(u.hostname))throw new Error('Artwork URL outside approved HTTPS providers');
 return u.href;
}
export function licensePolicy(url){
 let u;try{u=new URL(url)}catch{return null}
 if(u.protocol!=='https:'||u.hostname!=='creativecommons.org')return null;
 u.pathname=u.pathname.replace(/\/(?:deed|legalcode)(?:\.[a-zA-Z-]+)?\/?$/,'/');
 if(!u.pathname.endsWith('/'))u.pathname+='/';
 if(/^\/licenses\/by\/(3\.0|4\.0)\/$/.test(u.pathname))return {id:`CC BY ${u.pathname.split('/')[3]}`,shareAlike:false,url:u.href};
 if(/^\/licenses\/by-sa\/(3\.0|4\.0)\/$/.test(u.pathname))return {id:`CC BY-SA ${u.pathname.split('/')[3]}`,shareAlike:true,url:u.href};
 if(/^\/publicdomain\/(zero|mark)\/1\.0\/$/.test(u.pathname))return {id:u.pathname.includes('zero')?'CC0 1.0':'Public Domain Mark 1.0',shareAlike:false,url:u.href};
 return null;
}
export async function request(url,{fetchImpl=globalThis.fetch,maxBytes=MAX_BYTES,retries=2}={}){
 let target=allowedURL(url);
 for(let attempt=0;attempt<=retries;attempt++){
  const r=await fetchImpl(target,{headers:{'User-Agent':'NIGHT-Collectibles/1.0 (licensed-art discovery; source links retained)','Accept':'*/*'},signal:AbortSignal.timeout(30000),redirect:'manual'});
  if([301,302,303,307,308].includes(r.status)){
   if(attempt>=retries)throw new Error('Too many provider redirects');target=allowedURL(new URL(r.headers.get('location'),target).href);continue;
  }
  if([429,502,503,504].includes(r.status)&&attempt<retries){await new Promise(resolve=>setTimeout(resolve,400*(attempt+1)));continue;}
  if(!r.ok)throw new Error(`Artwork provider returned HTTP ${r.status}`);
  const length=Number(r.headers.get('content-length'));
  if(length>maxBytes)throw new Error('Artwork exceeds download limit');
  const chunks=[];let size=0;
  for await(const chunk of r.body){size+=chunk.length;if(size>maxBytes)throw new Error('Artwork exceeds download limit');chunks.push(chunk);}
  return {bytes:Buffer.concat(chunks),url:target};
 }
 throw new Error('Artwork provider unavailable');
}
const attr=(tag,key)=>tag.match(new RegExp(`\\b${key}=(?:"([^"]*)"|'([^']*)'|([^\\s>]+))`,'i'))?.slice(1).find(x=>x!==undefined)??'';
const clean=s=>String(s??'').replace(/<[^>]*>/g,'').replace(/&amp;/g,'&').replace(/&#0*39;|&apos;/g,"'").replace(/&quot;/g,'"').replace(/\s+/g,' ').trim();
/** Extract only Tyson Tan's gallery; the separately licensed Fan Art section is excluded. */
export function parseKrita(html,query=''){
 const section=html.split(/Kiki Splash Art Gallery<\/h3>/i)[1]?.split(/<h3[^>]*>Kiki.s Alternative Designs/i)[0];
 if(!section||!html.includes('https://creativecommons.org/licenses/by-sa/4.0/'))throw new Error('Official gallery/license markup changed; artwork rejected');
 const records=[],seen=new Set();
 for(const m of section.matchAll(/(<a\b[^>]*>)[\s\S]*?(<img\b[^>]*>)[\s\S]*?<\/a>/gi)){
  const url=attr(m[1],'href'),title=clean(attr(m[2],'alt'));
  if(!/\.(png|jpg|jpeg)$/i.test(url)||!title||seen.has(url))continue;
  allowedURL(url);seen.add(url);
  if(query&&!`${title} Tyson Tan anime illustration`.toLowerCase().includes(query.toLowerCase()))continue;
  const id='krita-'+hash(url).slice(0,12);
  records.push({id,provider:'krita',title,artist:'Tyson Tan',sourceUrl:KRITA_SOURCE,originalUrl:url,license:'CC BY-SA 4.0',licenseUrl:'https://creativecommons.org/licenses/by-sa/4.0/',shareAlike:true,status:'metadata-eligible',evidenceSha256:hash(html),verifiedAt:new Date().toISOString()});
 }
 return records;
}
export function parseCommons(json,{minWidth=2000,minHeight=2000}={}){
 const candidates=[];
 for(const p of Object.values(json.query?.pages??{})){
  const i=p.imageinfo?.[0],e=i?.extmetadata??{};
  let policy=licensePolicy(e.LicenseUrl?.value?.replace(/^http:/,'https:'));
  // A public-domain classification needs all three independent metadata fields.
  if(!policy&&e.License?.value==='pd'&&e.Copyrighted?.value==='False'&&e.UsageTerms?.value==='Public domain')policy=licensePolicy('https://creativecommons.org/publicdomain/mark/1.0/');
  if(!i||!policy||i.width<minWidth||i.height<minHeight||!['image/jpeg','image/png','image/webp'].includes(i.mime))continue;
  if(e.Restrictions?.value||/noncommercial|no derivatives|fair use/i.test(JSON.stringify(e)))continue;
  const artist=clean(e.Artist?.value);if(!artist||/^(missing name|unknown|unknown author)$/i.test(artist))continue;
  candidates.push({id:`commons-${p.pageid}`,provider:'commons',title:clean(e.ObjectName?.value||p.title.replace(/^File:/,'')),artist,sourceUrl:i.descriptionurl,originalUrl:allowedURL(i.url),width:i.width,height:i.height,license:policy.id,licenseUrl:policy.url,shareAlike:policy.shareAlike,status:'metadata-eligible',verifiedAt:new Date().toISOString(),evidence:e});
 }
 return candidates;
}
/** Searches are live; minimum dimensions are verified against decoded bytes at acquire time. */
export async function discover({provider='krita',query='',minWidth=2000,minHeight=2000,limit=12,fetchImpl}={}){
 if(provider==='krita'){
  const {bytes}=await request(KRITA_SOURCE,{fetchImpl,maxBytes:3000000});
  return parseKrita(bytes.toString('utf8'),query).slice(0,Math.min(limit,30));
 }
 if(provider==='commons'){
  if(!query.trim())throw new Error('Commons search requires a query');
  const url=new URL('https://commons.wikimedia.org/w/api.php');
  for(const [key,value] of Object.entries({action:'query',format:'json',generator:'search',gsrsearch:`${query} -filetype:svg`,gsrnamespace:6,gsrlimit:Math.min(limit,20),prop:'imageinfo',iiprop:'url|size|mime|extmetadata',iiextmetadatafilter:'Artist|License|LicenseUrl|LicenseShortName|ObjectName|Restrictions|UsageTerms|Copyrighted',origin:'*'}))url.searchParams.set(key,value);
  const {bytes}=await request(url.href,{fetchImpl,maxBytes:5000000});const json=JSON.parse(bytes);if(json.error)throw new Error(`Commons API: ${json.error.info??json.error.code}`);return parseCommons(json,{minWidth,minHeight});
 }
 throw new Error('Unknown artwork provider');
}
/** Re-query the provider before acquiring. A saved search result is not proof of current rights. */
export async function acquire(record,{destination,minWidth=2000,minHeight=2000,fetchImpl}={}){
 if(!destination)throw new Error('Acquisition needs a destination directory');
 let fresh;
 if(record.provider==='krita')fresh=(await discover({provider:'krita',limit:30,fetchImpl})).find(r=>r.originalUrl===record.originalUrl);
 else if(record.provider==='commons'){
  const url=new URL('https://commons.wikimedia.org/w/api.php');
  for(const[k,v]of Object.entries({action:'query',format:'json',pageids:record.id.replace(/^commons-/,''),prop:'imageinfo',iiprop:'url|size|mime|extmetadata'}))url.searchParams.set(k,v);
  const {bytes}=await request(url.href,{fetchImpl,maxBytes:3000000});fresh=parseCommons(JSON.parse(bytes),{minWidth,minHeight})[0];
 }
 if(!fresh||!licensePolicy(fresh.licenseUrl))throw new Error('Fresh license verification failed');
 const {bytes}=await request(fresh.originalUrl,{fetchImpl});
 const metadata=await sharp(bytes,{limitInputPixels:120000000}).metadata();
 if(!['png','jpeg','webp'].includes(metadata.format)||metadata.pages>1||metadata.width<minWidth||metadata.height<minHeight)throw new Error('Artwork does not meet pixel/format requirements');
 const sha256=hash(bytes),ext={jpeg:'jpg',png:'png',webp:'webp'}[metadata.format];
 await fs.mkdir(destination,{recursive:true});
 const filename=`${fresh.id}-${sha256.slice(0,12)}.${ext}`;
 await fs.writeFile(path.join(destination,filename),bytes);
 const manifest={...fresh,filename,width:metadata.width,height:metadata.height,sha256,status:'acquired-needs-composition-review',changes:'Original file; no changes.',visualApproval:false};
 await fs.writeFile(path.join(destination,`${fresh.id}.json`),JSON.stringify(manifest,null,2));
 const credit=`${fresh.title} — ${fresh.artist}\nSource: ${fresh.sourceUrl}\n${fresh.license}: ${fresh.licenseUrl}\n${fresh.shareAlike?'Adapted card images must retain this license.\n':''}Downloaded: ${fresh.verifiedAt}\nSHA-256: ${sha256}\n`;
 await fs.writeFile(path.join(destination,`${fresh.id}-ATTRIBUTION.txt`),credit);
 return manifest;
}
