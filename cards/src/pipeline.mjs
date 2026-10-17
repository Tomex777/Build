import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import {ROOT,inside,atomic,digest,loadCatalogue,rendererDigest,reviewFingerprint} from './catalogue.mjs';
import {acquireArtwork} from './artwork.mjs';
import {cardSvg,createSession} from './render.mjs';
import {POWER_SCALE,rankValue} from './power.mjs';

export async function readJson(file,fallback) {try{return JSON.parse(await fs.readFile(file,'utf8'));}catch(e){if(e.code==='ENOENT'&&fallback!==undefined)return fallback;throw e;}}
const xml=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
export async function presentation(report,out) {
  const entries=[...report.cards].sort((a,b)=>rankValue(b.power?.rank)-rankValue(a.power?.rank)||a.number-b.number||a.design.localeCompare(b.design));
  const tiles=entries.map(c=>`<a href="${c.png}" data-rank="${c.power?.rank??''}" data-search="${xml((c.name+' '+c.form+' '+c.design+' '+(c.power?.rank??'')).toLowerCase())}" title="${xml(c.power?`${c.power.scope}. ${c.power.basis}`:c.name)}"><img src="${c.phone}" width="320" height="512" loading="lazy" alt="${xml(c.name+' · '+c.design+(c.power?.rank?' · Power '+c.power.rank:''))}"><span>${xml(c.name)}</span><small>${xml((c.formId==='featured'?'':c.form+' · ')+c.design+(c.power?.rank?' · '+c.power.rank:''))}</small></a>`).join('');
  const html=`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>NIGHT · BLEACH</title><style>*{box-sizing:border-box}body{margin:0;background:#10151a;color:#f3e8d3;font:16px system-ui;padding:32px clamp(16px,4vw,56px)}header{max-width:1680px;margin:auto}h1{font:500 42px Georgia;margin:0 0 10px}header p{color:#a9b3b8;margin:0 0 28px}input,select{border:1px solid #52606a;background:#1b252e;color:#fff;padding:14px 18px;border-radius:9px;font:inherit;outline-offset:4px;margin:0}input{width:min(100%,440px)}select{cursor:pointer;min-width:125px}.filters{display:flex;flex-wrap:wrap;gap:12px;margin-bottom:28px}.rank-note{font-size:12px;line-height:1.6}main{display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:28px 22px;max-width:1680px;margin:auto}a{color:inherit;text-decoration:none}img{display:block;width:100%;height:auto;border-radius:13px}span,small{display:block;margin-top:10px}small{color:#a9b3b8;font-size:12px;margin-top:4px}a[hidden]{display:none}@media(max-width:600px){body{padding:24px 14px}main{grid-template-columns:repeat(2,minmax(0,1fr));gap:24px 12px}h1{font-size:34px}span{font-size:13px}small{font-size:10px}}</style><header><h1>NIGHT / BLEACH</h1><p>${entries.length} collectible editions</p><div class="filters"><input type="search" aria-label="Search cards" placeholder="Find a character or finish"><select aria-label="Power rank"><option value="">All ranks</option>${[...POWER_SCALE.ranks].reverse().filter(rank=>entries.some(c=>c.power?.rank===rank)).map(rank=>`<option value="${rank}">${rank}</option>`).join('')}</select></div><p class="rank-note">NIGHT power grades · strongest first · form-specific editorial proposals</p></header><main>${tiles}</main><script>const search=document.querySelector('input'),rank=document.querySelector('select');function filter(){const q=search.value.toLowerCase().trim();for(const a of document.querySelectorAll('main a'))a.hidden=!a.dataset.search.includes(q)||(rank.value&&a.dataset.rank!==rank.value)}search.addEventListener('input',filter);rank.addEventListener('change',filter)</script></html>`;
  await atomic(path.join(out,'index.html'),html);
  report.boards=[];
  for(let start=0;start<entries.length;start+=10){
    const selected=entries.slice(start,start+10), layers=[];
    const texts=[`<text x="32" y="47" font-size="25" fill="#f0e7d5">NIGHT / BLEACH</text><text x="1712" y="45" text-anchor="end" font-size="13" fill="#99a6af">${start+1}–${start+selected.length} / ${entries.length} · 320px phone proofs</text>`];
    for(let i=0;i<selected.length;i++){
      const c=selected[i], x=32+(i%5)*340,y=78+Math.floor(i/5)*582;
      layers.push({input:await fs.readFile(path.join(out,c.phone)),left:x,top:y});
      texts.push(`<text x="${x}" y="${y+535}" font-size="15" fill="#f3e8d3">${xml(c.name)}</text><text x="${x}" y="${y+555}" font-size="11" fill="#a4b1ba">${xml((c.formId==='featured'?'':c.form+' · ')+c.design+(c.power?.rank?' · '+c.power.rank:''))}</text>`);
    }
    layers.push({input:Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="1744" height="1250"><g font-family="sans-serif">${texts.join('')}</g></svg>`),left:0,top:0});
    const filename=`contact-${String(start/10+1).padStart(2,'0')}.png`;
    await sharp({create:{width:1744,height:1250,channels:4,background:'#10151a'}}).composite(layers).png().toFile(path.join(out,filename));
    report.boards.push(filename);
  }
}
export async function build({series='bleach',width=3000,out='dist/candidates',only,designs}={}) {
  const catalogue=await loadCatalogue(series), renderer=await rendererDigest(), folder=inside(out);
  const selected=only?.length?catalogue.cards.filter(c=>only.includes(c.id)):catalogue.cards;
  if(!selected.length||only?.some(id=>!selected.some(c=>c.id===id)))throw Error('Unknown or empty card selection');
  await fs.mkdir(folder,{recursive:true});
  const previous=await readJson(path.join(folder,'build.json'),{cards:[]});
  const report={version:1,series,width,height:width*1.6,renderer,builtAt:new Date().toISOString(),cards:[],failures:[]};
  const engine=await createSession();
  try{
    for(const card of selected){
      let assets;
      try{assets=await acquireArtwork(card);}catch(e){report.failures.push({id:card.id,error:e.message});continue;}
      for(const design of card.designs.filter(d=>!designs?.length||designs.includes(d))){
        const id=card.id+'--'+design, fingerprint=reviewFingerprint(card,design,renderer,width);
        try{
          const old=previous.cards.find(c=>c.id===id&&c.fingerprint===fingerprint);
          if(old&&digest(await fs.readFile(path.join(folder,old.png)))===old.pngSHA256&&digest(await fs.readFile(path.join(folder,old.svg)))===old.svgSHA256){await fs.access(path.join(folder,old.phone));report.cards.push(old);console.log('REUSED',id);continue;}
        }catch(e){if(e.code!=='ENOENT')throw e;}
        try{
          const svg=await cardSvg(card,assets,design), result=await engine.render(svg,width,assets.focal);
          const entry={id,character:card.character,name:card.name,title:card.title,number:card.number,formId:card.formId,form:card.form,design,power:card.power,aliases:card.aliases,width,height:width*1.6,png:id+'.png',svg:id+'.svg',phone:id+'--phone.png',pngSHA256:digest(result.png),svgSHA256:digest(svg),fingerprint,qa:result.qa,source:card.source.page,artworkSHA256:card.source.sha256,nativeArtwork:[card.source.width,card.source.height],rights:card.source.rights,usage:card.source.usage};
          await atomic(path.join(folder,entry.png),result.png);
          await atomic(path.join(folder,entry.svg),svg);
          await atomic(path.join(folder,entry.phone),await sharp(result.png).resize(320,512).png().toBuffer());
          report.cards.push(entry);console.log('RENDERED',id);
        }catch(e){report.failures.push({id,error:e.message});console.error('QUARANTINED',id,e.message);}
      }
      await atomic(path.join(folder,'build.json'),JSON.stringify(report,null,2)+'\n');
    }
  }finally{await engine.close();}
  await presentation(report,folder);
  await atomic(path.join(folder,'build.json'),JSON.stringify(report,null,2)+'\n');
  return report;
}
export async function approve({series='bleach',out='dist/candidates',ids,reviewer='Codex'}={}) {
  if(!ids?.length)throw Error('Explicit visually inspected card IDs are required');
  const folder=inside(out),report=await readJson(path.join(folder,'build.json'));
  if(report.width!==3000)throw Error('Approve only the 3000×4800 master renders');
  const catalogue=await loadCatalogue(series),renderer=await rendererDigest(),file=inside(`reviews/${series}.json`),reviews=await readJson(file,{});
  for(const id of ids){
    const entry=report.cards.find(c=>c.id===id),card=catalogue.cards.find(c=>id.startsWith(c.id+'--'));
    if(!entry||!card||entry.fingerprint!==reviewFingerprint(card,entry.design,renderer,3000))throw Error(`Stale render: ${id}`);
    if(digest(await fs.readFile(path.join(folder,entry.png)))!==entry.pngSHA256)throw Error(`Changed PNG: ${id}`);
    reviews[id]={status:'approved',fingerprint:entry.fingerprint,reviewer,reviewedAt:new Date().toISOString(),sizes:['3000×4800','native-pixel crops','320×512']};
  }
  await atomic(file,JSON.stringify(reviews,null,2)+'\n');return reviews;
}
export async function publish({series='bleach',out='dist/candidates',destination='dist/published'}={}) {
  const folder=inside(out),report=await readJson(path.join(folder,'build.json'));
  if(report.width!==3000||report.failures.length||!report.cards.length)throw Error('Quarantined, incomplete or low-resolution build cannot be published');
  const catalogue=await loadCatalogue(series),renderer=await rendererDigest(),reviews=await readJson(inside(`reviews/${series}.json`),{});
  const expected=catalogue.cards.flatMap(c=>c.designs.map(d=>c.id+'--'+d));
  if(expected.length!==report.cards.length||expected.some(id=>!report.cards.some(c=>c.id===id)))throw Error('Run the complete collection build; a subset must not replace the published library');
  const target=inside(destination),stage=target+'.pending';await fs.rm(stage,{recursive:true,force:true});await fs.mkdir(stage,{recursive:true});
  const cards=[];
  for(const entry of report.cards){
    const card=catalogue.cards.find(c=>entry.id===c.id+'--'+entry.design),review=reviews[entry.id];
    if(!card||entry.fingerprint!==reviewFingerprint(card,entry.design,renderer,3000)||review?.status!=='approved'||review.fingerprint!==entry.fingerprint)throw Error(`Visual review required: ${entry.id}`);
    for(const [filename,hash] of [[entry.png,entry.pngSHA256],[entry.svg,entry.svgSHA256],[entry.phone,null]]){
      const bytes=await fs.readFile(path.join(folder,filename));if(hash&&digest(bytes)!==hash)throw Error('Changed output: '+filename);await fs.writeFile(path.join(stage,filename),bytes);
    }
    cards.push({...entry,approved:true,review});
  }
  for(const filename of ['index.html',...report.boards??[]])await fs.copyFile(path.join(folder,filename),path.join(stage,filename));
  await fs.writeFile(path.join(stage,'manifest.json'),JSON.stringify({version:1,series,dimensions:[3000,4800],renderer,publishedAt:new Date().toISOString(),boards:report.boards,cards},null,2)+'\n');
  const backup=target+'.previous';await fs.rm(backup,{recursive:true,force:true});
  try{await fs.rename(target,backup);}catch(e){if(e.code!=='ENOENT')throw e;}
  try{await fs.rename(stage,target);}catch(e){await fs.rename(backup,target).catch(()=>{});throw e;}
  await fs.rm(backup,{recursive:true,force:true});return cards;
}
