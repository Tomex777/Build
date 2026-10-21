import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import {ROOT,inside,atomic,digest,loadCatalogue,rendererDigest,reviewFingerprint} from './catalogue.mjs';
import {acquireArtwork} from './artwork.mjs';
import {cardSvg,createSession} from './render.mjs';
import {rankValue} from './power.mjs';
import {writeCollectionGallery} from './gallery.mjs';

export async function readJson(file,fallback) {try{return JSON.parse(await fs.readFile(file,'utf8'));}catch(e){if(e.code==='ENOENT'&&fallback!==undefined)return fallback;throw e;}}
const xml=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
export async function presentation(report,out) {
  const seriesLabel=report.series==='eminence'?'THE EMINENCE IN SHADOW':String(report.series||'bleach').toUpperCase();
  const entries=[...report.cards].sort((a,b)=>rankValue(b.power?.rank)-rankValue(a.power?.rank)||a.number-b.number||a.design.localeCompare(b.design));
  await writeCollectionGallery(report,out);
  report.boards=[];
  for(let start=0;start<entries.length;start+=10){
    const selected=entries.slice(start,start+10), layers=[];
    const texts=[`<text x="32" y="47" font-size="25" fill="#f0e7d5">NIGHT / ${seriesLabel}</text><text x="1712" y="45" text-anchor="end" font-size="13" fill="#99a6af">${start+1}–${start+selected.length} / ${entries.length} · 320px phone proofs</text>`];
    for(let i=0;i<selected.length;i++){
      const c=selected[i], x=32+(i%5)*340,y=78+Math.floor(i/5)*582;
      layers.push({input:await fs.readFile(path.join(out,c.phone)),left:x,top:y});
      texts.push(`<text x="${x}" y="${y+535}" font-size="15" fill="#f3e8d3">${xml(c.name)}</text><text x="${x}" y="${y+555}" font-size="11" fill="#a4b1ba">${xml((c.formId==='featured'?'':c.form+' · ')+c.design+(c.power?.rank?' · '+c.power.rank:''))}</text>`);
    }
    layers.push({input:Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="1744" height="1250"><g font-family="sans-serif">${texts.join('')}</g></svg>`),left:0,top:0});
    const filename=`contact-${String(start/10+1).padStart(2,'0')}.png`;
    await atomic(path.join(out,filename),await sharp({create:{width:1744,height:1250,channels:4,background:'#10151a'}}).composite(layers).png().toBuffer());
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
    for(const [filename,hash] of [[entry.png,entry.pngSHA256],[entry.svg,entry.svgSHA256],[entry.phone,null],[entry.preview,entry.previewSHA256]]){
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
