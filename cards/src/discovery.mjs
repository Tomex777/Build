import {download} from './artwork.mjs';
import {discover as discoverLicensed} from '../renderer/src/discovery.mjs';

const decode=s=>s.replace(/&amp;/g,'&').replace(/&#0?39;|&apos;/g,"'").replace(/&quot;/g,'"').replace(/&nbsp;/g,' ').trim();
const attr=(tag,name)=>decode(tag.match(new RegExp('\\b'+name+'=["\']([^"\']+)["\']','i'))?.[1]??'');
export async function discover({provider='brave-souls',limit=150,query='',fetchImpl=fetch}={}) {
  if(['commons','krita'].includes(provider))return discoverLicensed({provider,query,limit,minWidth:2000,minHeight:2000,fetchImpl});
  const source=provider==='brave-souls'?'https://www.bleach-bravesouls.com/en/character/':provider==='bleach-anime'?'https://bleach-anime.com/en/character/':null;
  if(!source)throw Error('Use brave-souls or bleach-anime; open-license discovery is also retained in renderer/src/discovery.mjs');
  const html=(await download(source,{fetchImpl,limit:4*1024*1024})).toString('utf8'),candidates=[];
  if(provider==='brave-souls'){
    for(const m of html.matchAll(/<a\b([^>]*?)>([\s\S]*?)<\/a>/gi)){
      const href=attr(m[1],'href'),img=m[2].match(/<img\b[^>]*>/i)?.[0];
      if(!img||! /(?:^|\/)character\/[^/]+\.html$/.test(href))continue;
      const page=new URL(href,source);if(page.origin!==new URL(source).origin)continue;
      const slug=page.pathname.split('/').pop().replace('.html',''),name=attr(img,'alt').replace(/^Bleach\s+/i,'');
      if(name&&!candidates.some(c=>c.slug===slug))candidates.push({name,slug,page:page.href});
    }
  }else{
    for(const m of html.matchAll(/<li[^>]+data-chara="([^"]+)"[^>]*>([\s\S]*?)<\/li>/g)){
      const name=m[2].match(/p-chara_data__name-text">([^<]+)/)?.[1],image=m[2].match(/<img src="([^"]*chara_\d+\.png)/)?.[1];
      if(name&&image)candidates.push({name:decode(name),sourceId:m[1],page:source+'?chara='+m[1],image:new URL(image,source).href});
    }
  }
  if(!candidates.length)throw Error('Official catalogue structure changed; no guessed image fallback');
  return {provider,source,candidates:candidates.slice(0,limit).map(c=>({...c,status:'candidate',usage:'reference-only',requires:['identity and exact form','native resolution','recorded artwork rights','visual composition review']}))};
}
export async function resolveOfficialImage(candidate,{fetchImpl=fetch}={}) {
  if(candidate.image)return candidate.image;
  if(new URL(candidate.page).origin!=='https://www.bleach-bravesouls.com')throw Error('Unsupported artwork provider');
  const html=(await download(candidate.page,{fetchImpl,limit:4*1024*1024})).toString('utf8');
  const tag=[...html.matchAll(/<img\b[^>]*>/gi)].map(m=>m[0]).find(t=>attr(t,'class').split(/\s+/).includes('chara-mv_image'));
  const src=tag&&attr(tag,'src');if(!src)throw Error('Official main illustration missing');
  const url=new URL(src,candidate.page);if(url.origin!=='https://www.bleach-bravesouls.com')throw Error('Unexpected artwork host');return url.href;
}
