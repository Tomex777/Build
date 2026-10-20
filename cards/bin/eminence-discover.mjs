#!/usr/bin/env node
// Research-only official art indexer. Never treats visual matches as verified
// character cutouts, never rebinds artwork, never publishes cards.
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const pages=[
 'https://shadow-garden.jp/1st/character/',
 'https://shadow-garden.jp/2nd/character/',
 'https://www.shadow-garden-mog.jp/dmg/',
 'https://www.shadow-garden-mog.jp/apocrypha/',
];
const results=[];
const scan=(html,url)=>{
  const found=[],seen=new Set(),origin=new URL(url);
  const add=(candidate,context,type)=>{
    if(!candidate || /^(data:|blob:|javascript:)/i.test(candidate))return;
    const cleaned=candidate.replace(/&amp;/g,'&').trim();
    let target;try{target=new URL(cleaned,origin);}catch{return;}
    if(!/^https?:$/.test(target.protocol) || !/\.(png|jpe?g|webp|avif)(?:$|\?)/i.test(target.href))return;
    if(seen.has(target.href))return;seen.add(target.href);
    found.push({url:target.href,context:context.replace(/<[^>]+>/g,' ').replace(/\s+/g,' ').trim().slice(0,210),type});
  };
  for(const m of html.matchAll(/<img\b[^>]*>/ig)){
    const tag=m[0],context=html.slice(Math.max(0,m.index-240),Math.min(html.length,m.index+tag.length+240));
    for(const k of ['src','data-src','data-original','data-lazy-src','srcset']){
      const rx=new RegExp("(?:^|\\s)"+k+"\\s*=\\s*[\\x22\\x27]([^\\x22\\x27]+)[\\x22\\x27]","i");
      const v=tag.match(rx)?.[1];
      if(v)for(const src of (k==='srcset'?v.split(',').map(x=>x.trim().split(/\s+/)[0]):[v]))add(src,context,'img:'+k);
    }
  }
  for(const m of html.matchAll(/url\(\s*["']?([^)"']+\.(?:png|jpg|jpeg|webp|avif)(?:\?[^)"']*)?)["']?\s*\)/ig))
    add(m[1],html.slice(Math.max(0,m.index-130),m.index+180),'css-url');
  for(const m of html.matchAll(/(?:["'])(\/[^"'\s<>]{3,280}\.(?:png|jpe?g|webp)(?:\?[^"'<> ]*)?)(?:["'])/ig))
    add(m[1],html.slice(Math.max(0,m.index-110),m.index+180),'asset-path');
  return found;
};
for(const url of pages){
  try{
    const response=await fetch(url,{signal:AbortSignal.timeout(30000),headers:{'User-Agent':'Mozilla/5.0 (compatible; NightCardsResearch/1.0)'}});
    if(!response.ok)throw Error('HTTP '+response.status);
    const html=await response.text();
    const assets=scan(html,url);
    results.push({page:url,htmlBytes:html.length,assets,jsFiles:[...html.matchAll(/<script[^>]+src=["']([^"']+)/ig)].map(m=>new URL(m[1],url).href).slice(0,30)});
    console.log(url,'HTML',html.length,'assets',assets.length);
    console.log('ALL OFFICIAL ASSETS',JSON.stringify(assets.map(x=>x.url)));\n    console.log('CHARA HTML HINTS',JSON.stringify([...html.matchAll(/.{0,80}chara1.{0,200}/g)].slice(0,15).map(x=>x[0])));
  }catch(e){results.push({page:url,error:e.message,assets:[]});console.error(url,e.message);}
}
const output=path.join(root,'dist/eminence-discovery.json');
await fs.mkdir(path.dirname(output),{recursive:true});await fs.writeFile(output,JSON.stringify({date:new Date().toISOString(),pages:results},null,2));
if(results.every(r=>!r.assets?.length))process.exitCode=1;
