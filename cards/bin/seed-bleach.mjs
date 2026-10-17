// Explicit first collection seed. Discovery never approves or overwrites reviews.
import fs from 'node:fs/promises';
import sharp from 'sharp';
import {discover,resolveOfficialImage} from '../src/discovery.mjs';
import {download} from '../src/artwork.mjs';
import {inside,atomic,digest,normalise} from '../src/catalogue.mjs';

const target=inside('catalog/bleach.json');
try{await fs.access(target);throw Error('Catalogue exists; add reviewed recipes explicitly instead of reseeding');}catch(e){if(e.code!=='ENOENT')throw e;}
const bbs=await discover({limit:150}), anime=await discover({provider:'bleach-anime',limit:150});
await atomic(inside('catalog/official-roster.json'),JSON.stringify(bbs,null,2)+'\n');
await atomic(inside('catalog/official-anime-roster.json'),JSON.stringify(anime,null,2)+'\n');
const chosen='orihime uryu sado kon isshin urahara yoruichi rukia byakuya renji kenpachi hitsugaya rangiku yamamoto soifon kira unohana isane hinamori komamura kyoraku nanao hisagi yachiru ikkaku yumichika mayuri nemu ukitake kaien kukaku ganju aizen gin tosen nel stark barragan halibel nelliel ulquiorra nnoitora grimmjow szayelaporro yammy hirako hiyori rojuro lisa hachigen kensei mashiro ginjo riruka tsukishima'.split(' ');
const titles={aizen:'Aizen',yamamoto:'Yamamoto',unohana:'Unohana',kyoraku:'Shunsui',urahara:'Urahara',tosen:'Tosen',halibel:'Harribel',stark:'Starrk',hirako:'Shinji',hitsugaya:'Toshiro',soifon:'Soi Fon',sado:'Chad',rojuro:'Rose',ginjo:'Ginjo',tsukishima:'Tsukishima'};
const finishes={aizen:['obsidian','nocturne'],rukia:['tidelight','nocturne'],yoruichi:['emberglass','nocturne'],kenpachi:['crimson','nocturne'],byakuya:['ivory','nocturne'],ulquiorra:['nocturne','tidelight']};
const rights='All rights reserved — character artwork is not licensed by the renderer MIT license.';
const credit='© Tite Kubo / SHUEISHA';
const cards=[],failures=[];
async function sourceFor(row,provider,slug){
  const image=await resolveOfficialImage(row),localPath=`artwork/bleach/${slug}.png`;
  let bytes;try{bytes=await fs.readFile(inside(localPath));}catch(e){if(e.code!=='ENOENT')throw e;bytes=await download(image);await atomic(inside(localPath),bytes);}
  const m=await sharp(bytes).metadata();
  const {data,info}=await sharp(bytes).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  let left=info.width,top=info.height,right=0,bottom=0;
  for(let y=0;y<info.height;y++)for(let x=0;x<info.width;x++)if(data[(y*info.width+x)*4+3]>16){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}
  return {source:{provider,page:row.page,image,localPath,sha256:digest(bytes),width:m.width,height:m.height,rights,usage:'reference-only',artist:provider==='brave-souls'?'BLEACH: Brave Souls':provider==='soul-resonance'?'BLEACH: Soul Resonance':'BLEACH Official Anime',credit},bbox:[left,top,right-left+1,bottom-top+1]};
}
async function workers(rows,fn){let index=0;await Promise.all(Array.from({length:5},async()=>{for(;;){const i=index++;if(i>=rows.length)return;try{await fn(rows[i],i);}catch(e){failures.push({name:rows[i].name??rows[i].slug,error:e.message});}}}));}
await workers(chosen.map(slug=>{const c=bbs.candidates.find(c=>c.slug===slug);if(!c)throw Error('Missing official character: '+slug);return c;}),async row=>{
  const {source,bbox}=await sourceFor(row,'brave-souls',row.slug),[x,y,w,h]=bbox;
  const recipe={crop:[x,y,w,Math.min(h,w)],bottomPadding:.28,edgeFade:.12,bottomFade:.22,focal:[.5,.3]};
  if(row.slug==='byakuya'){recipe.horizontalShift={ivory:240};recipe.focal=[.21,.3];}
  cards.push({id:row.slug+'-featured',character:row.slug,name:row.name,title:titles[row.slug]??row.name.split(' ')[0],form:'Featured portrait',formId:'featured',aliases:[row.name,row.slug,titles[row.slug]??row.name.split(' ')[0]],designs:finishes[row.slug]??['nocturne'],status:'candidate',source,recipe});
  console.log('ACQUIRED',row.slug);
});
const forms=[['substitute-soul-reaper','Substitute Soul Reaper','https://p1.dailygn.com/obj/g-marketing-act-assets/2025_06_10_16_06_25/man_chushiyihu_s941048.png','vanguard'],['original-shikai','Original Shikai','https://p1.dailygn.com/obj/g-marketing-act-assets/2025_06_10_13_48_40/PCman_yihu_yihu_shijie_s851246.png','ivory'],['original-bankai','Original Bankai','https://p1.dailygn.com/obj/g-marketing-act-assets/2025_05_28_18_55_17/PCman_yihu_wanjie_s172285.png','voltage']];
for(const [formId,form,image,design] of forms){
  const id='ichigo-'+formId,{source,bbox}=await sourceFor({page:'https://www.bleach-soulresonance.com/',image},'soul-resonance',id);
  cards.push({id,character:'ichigo',name:'Ichigo Kurosaki',title:'Ichigo',form,formId,aliases:['Ichigo','Ichigo Kurosaki','Kurosaki Ichigo'],designs:design==='ivory'?['ivory','nocturne','tidelight']:[design],status:'candidate',source,recipe:{crop:bbox,bottomPadding:.28,edgeFade:0,bottomFade:.12,focal:[.57,.45]}});
}
cards.sort((a,b)=>a.id.localeCompare(b.id));cards.forEach((c,i)=>c.number=i+1);
const selectedIds='53 54 55 56 57 23 24 25 26 28 83 64 65 84 82 27 61 29 63 67'.split(' ');
const extra=[];
await workers(selectedIds.map(id=>{const c=anime.candidates.find(c=>c.sourceId===id);if(!c)throw Error('Missing anime character: '+id);return c;}),async row=>{
  const slug=normalise(row.name).replaceAll(' ','-'),{source,bbox}=await sourceFor(row,'bleach-anime',slug+'-tybw'),[x,y,w,h]=bbox;
  extra.push({id:slug+'-tybw',character:slug,name:row.name,title:row.name==='Äs Nödt'?row.name:row.name.split(' ')[0],form:'Thousand-Year Blood War',formId:'tybw',aliases:[row.name,normalise(row.name),slug],designs:['nocturne'],status:'candidate',source,recipe:{crop:[x,y,w,Math.min(h,Math.round(w*1.1))],bottomPadding:.28,edgeFade:0,bottomFade:.12,focal:[.5,.3]}});console.log('ACQUIRED',slug);
});
extra.sort((a,b)=>a.id.localeCompare(b.id));for(const c of extra){c.number=cards.length+1;cards.push(c);}
const proposal=JSON.parse(await fs.readFile(inside('renderer/examples/ichigo-collection-proposal.json'),'utf8'));
await atomic(target,JSON.stringify({version:1,series:'bleach',name:'BLEACH / NIGHT Collection',dimensions:[3000,4800],cards,pendingForms:proposal,failures},null,2)+'\n');
console.log(cards.length+' character/form entries; '+cards.reduce((n,c)=>n+c.designs.length,0)+' editions; '+failures.length+' acquisition failures');
if(failures.length)process.exitCode=1;
