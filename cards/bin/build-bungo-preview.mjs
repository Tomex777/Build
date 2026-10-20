#!/usr/bin/env node
/**
 * Generate a real NIGHT Bungo Stray Dogs SVG card gallery.
 * Uses cardSvg() from the approved common NIGHT renderer, NOT CSS lookalike cards.
 * These outputs are unapproved artwork candidates; do not integrate with the bot.
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import {inside,loadCatalogue,digest} from '../src/catalogue.mjs';
import {acquireArtwork} from '../src/artwork.mjs';
import {cardSvg} from '../src/render.mjs';

const out=inside('dist/bungo-preview');
const catalogue=await loadCatalogue('bungo-stray-dogs');
if(!catalogue.cards.length)throw Error('No verified Bungo Stray Dogs art found. Cannot substitute mock cards.');
await fs.mkdir(path.join(out,'cards'),{recursive:true});
const completed=[],failed=[];
const escape=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;','\'':'&#39;'}[c]));
for(const card of catalogue.cards){
  const design=card.designs[0];
  try{
    const artwork=await acquireArtwork(card);
    const svg=await cardSvg(card,artwork,design);
    if(!svg.includes('data-character-breakout="required"') &&
       !svg.includes('data-characterBreakout="required"') &&
       !svg.includes('data-catalogue-id='))throw Error('The canonical NIGHT card renderer returned an unexpected SVG');
    if(!svg.includes('data-frame-boundary') || !svg.includes('data-layer="power-rank"'))
      throw Error('Missing NIGHT silhouette/depth frame or power-rank layer');
    const filename='cards/'+card.id+'.svg';
    await fs.writeFile(path.join(out,filename),svg);
    completed.push({id:card.id,name:card.name,title:card.title,form:card.form,rank:card.power.rank,
      design,number:card.number,filename,source:card.source.page,rights:card.source.rights});
    console.log('CARD',card.name,card.power.rank,design,filename);
  }catch(e){failed.push({id:card.id,error:e.message});console.error('SKIPPED',card.id,e.message);}
}
if(!completed.length)throw Error('Nothing renderable: '+JSON.stringify(failed));
const data=JSON.stringify(completed).replace(/</g,'\\u003c');
const html=`<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="theme-color" content="#0c1016"><title>NIGHT / Bungo Stray Dogs</title>
<meta name="description" content="NIGHT's Bungo Stray Dogs anime card collection — genuine NIGHT collectible SVG candidates, independent review." />
<style>
*{box-sizing:border-box}html{scrollbar-gutter:stable}body{margin:0;background:#0c1016;color:#f0ebe6;font:15px/1.5 system-ui,-apple-system,Segoe UI,sans-serif}
button,input,select{font:inherit}button{cursor:pointer}header{position:sticky;top:0;z-index:10;background:#0c1016ec;border-bottom:1px solid #29313b;backdrop-filter:blur(12px)}.top{max-width:1480px;margin:auto;padding:18px 27px;display:flex;align-items:center;justify-content:space-between;gap:20px}.logo{font-size:19px;font-weight:780;letter-spacing:.34em}.top span{font-size:11px;letter-spacing:.12em;color:#9ba8b6;text-align:right}
.hero{max-width:1480px;padding:78px 27px 32px;margin:auto;display:flex;align-items:flex-end;justify-content:space-between;gap:30px}.eyebrow{font-size:11px;letter-spacing:.25em;text-transform:uppercase;color:#e5b9a1}.hero h1{font-size:clamp(43px,8vw,99px);line-height:.96;letter-spacing:-.063em;font-weight:550;margin:18px 0 22px;font-family:Georgia,serif}.hero p{max-width:565px;font-size:14px;color:#9faeba;line-height:1.7;margin:0}.total{font-size:16px;color:#b5b8b5;text-align:right;white-space:nowrap}.total strong{font-size:45px;display:block;color:#faf2ea;font-weight:500}
.bar{max-width:1480px;margin:0 auto;padding:30px 27px;display:flex;gap:10px;flex-wrap:wrap;align-items:center}.search,.grade{height:44px;border-radius:9px;color:#f1f2f4;background:#151d26;border:1px solid #333e48}.search{padding:0 16px;flex:1 1 260px;min-width:0}.grade{padding:0 12px}.count{font-size:12px;color:#9eafb7;padding:0 14px}
.grid{display:grid;max-width:1480px;padding:0 27px 110px;margin:auto;grid-template-columns:repeat(auto-fill,minmax(215px,1fr));gap:37px 22px}.tile{min-width:0;text-align:left;border:0;color:inherit;background:transparent;padding:0}.tile img{display:block;width:100%;aspect-ratio:5/8;object-fit:contain;filter:drop-shadow(0 12px 18px #03050999);transition:transform .19s ease}.tile:hover img{transform:translateY(-7px)}.tile strong{display:block;margin-top:9px;font-size:14px;font-weight:650}.tile small{font-size:11px;color:#93a5b2;display:block}.tile[hidden]{display:none}.r{color:#e9b994}
footer{border-top:1px solid #25303b;text-align:center;padding:29px;color:#7b8d9e;font-size:11px;line-height:1.9}.empty{margin:auto;max-width:1480px;padding:20px 27px;color:#a2aab1}
dialog{max-width:100vw;max-height:100vh;inset:0;border:0;color:#eee;background:#0d121af5;padding:0;width:100vw;height:100vh}dialog::backdrop{background:#05070d}.viewer{height:100%;display:flex;align-items:center;justify-content:center;gap:24px;padding:60px 25px 30px}.viewer img{max-width:65vw;max-height:92%;object-fit:contain;filter:drop-shadow(0 14px 28px #000)}.info{max-width:270px}.info h2{font:36px/1.1 Georgia,serif;letter-spacing:-.03em}.info p{color:#9daeb8}.exit{position:absolute;right:26px;top:17px;border:1px solid #4b5865;background:#131b24;color:#fff;border-radius:8px;padding:8px 16px}
@media(max-width:640px){.top{padding:16px}.top span{font-size:9px}.hero{padding:54px 17px 22px;display:block}.hero h1{margin:15px 0}.hero p{font-size:12px}.total{text-align:left;margin-top:28px}.total strong{display:inline;font-size:23px}.bar{padding:20px 17px}.grid{padding:0 15px 70px;grid-template-columns:repeat(2,minmax(0,1fr));gap:25px 11px}.tile strong{font-size:12px}.tile small{font-size:10px}.viewer{flex-direction:column;padding:60px 14px 15px;gap:8px}.viewer img{max-width:91vw;max-height:70vh}.info{max-width:95vw;text-align:center}.info h2{font-size:22px;margin:4px}.info p{font-size:11px;margin:6px}}
</style></head><body><header><div class="top"><div class="logo">NIGHT</div><span>ANIME / BUNGO STRAY DOGS</span></div></header>
<main><section class="hero"><div><div class="eyebrow">NIGHT / Collection studies · Official art</div><h1>Bungo<br><i>Stray Dogs.</i></h1><p>The same NIGHT collectible designs introduced for BLEACH and Re:Zero, with form-specific grades and real artwork drawn across the inner card frame. Tap any card to inspect it.</p></div><div class="total"><strong>${completed.length}</strong> source-backed cards</div></section>
<div class="bar"><input id="search" class="search" type="search" placeholder="Search characters, forms, abilities…" aria-label="Search cards"><select id="rank" class="grade" aria-label="Rank"><option value="">All grades</option>${['F','E','D','C','B','A','S','SS','SSS','EX'].map(r=>'<option>'+r+'</option>').join('')}</select><span id="shown" class="count"></span></div>
<section id="grid" class="grid"></section><p class="empty" id="empty" hidden>No matching cards.</p></main>
<dialog id="modal" aria-label="Card details"><button id="exit" class="exit" aria-label="Close">Close ×</button><div class="viewer"><img id="large" alt=""><div class="info"><h2 id="name"></h2><p id="details"></p><p><small>Source-backed NIGHT SVG / 1000 × 1600. Reference-only character art, not licensed for sale.</small></p></div></div></dialog>
<footer>Independent NIGHT card art review · Copyright of the original characters and anime illustrations remains with their respective rights holders.<br>The designs are candidate studies, not commercially cleared releases. No combat stats or finished bot cards are implied.</footer>
<script>
const cards=${data};
const grid=document.querySelector('#grid'),input=document.querySelector('#search'),rank=document.querySelector('#rank'),shown=document.querySelector('#shown'),empty=document.querySelector('#empty');
const dialog=document.querySelector('#modal');
for(const c of cards){const b=document.createElement('button');b.className='tile';b.dataset.search=(c.name+' '+c.title+' '+c.form+' '+c.design).toLowerCase();b.dataset.rank=c.rank;
b.innerHTML='<img loading="lazy" alt=""><strong></strong><small></small>';
b.querySelector('img').src=c.filename;b.querySelector('img').alt=c.name+' NIGHT card';
b.querySelector('strong').textContent=c.name;b.querySelector('small').textContent=c.rank+' · '+c.form;
b.addEventListener('click',()=>{document.querySelector('#large').src=c.filename;document.querySelector('#large').alt=c.name+' full card';document.querySelector('#name').textContent=c.name;document.querySelector('#details').textContent='Rank '+c.rank+' · '+c.form+' · '+c.design;dialog.showModal();});grid.appendChild(b)}
function filter(){let n=0;for(const b of grid.children){b.hidden=!(b.dataset.search.includes(input.value.toLowerCase().trim())&&(!rank.value||rank.value===b.dataset.rank));if(!b.hidden)n++}shown.textContent=n+' cards';empty.hidden=n>0}
input.addEventListener('input',filter);rank.addEventListener('change',filter);filter();
document.querySelector('#exit').addEventListener('click',()=>dialog.close());dialog.addEventListener('click',e=>{if(e.target===dialog)dialog.close()});document.addEventListener('keydown',e=>{if(e.key==='Escape'&&dialog.open)dialog.close()});
</script></body></html>`;
await fs.writeFile(path.join(out,'index.html'),html);
await fs.writeFile(path.join(out,'manifest.json'),JSON.stringify({status:'candidate-visual-review',count:completed.length,cards:completed,failed,rights:'reference-only'},null,2));
console.log('REAL SVG CARDS',completed.length,'SOURCE ERRORS',failed.length);
