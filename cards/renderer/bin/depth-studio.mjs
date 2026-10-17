import fs from 'node:fs/promises';
import path from 'node:path';
import {ROOT} from '../src/node-assets.mjs';
import {loadCharacterAssets} from '../src/character-assets.mjs';
import {characterCards} from '../src/characters.mjs';
const profile=JSON.parse(await fs.readFile(path.resolve(process.argv[2]??ROOT+'/examples/characters/naruto.json'),'utf8'));
const templates=JSON.parse(await fs.readFile(path.join(ROOT,'examples/cards.json'),'utf8'));
const cards=characterCards(templates,profile).filter(c=>c.theme!=='verdant');
const a=await loadCharacterAssets(profile);
const assets=Object.fromEntries(cards.map(c=>[c.id,a]));
// The four pure renderer modules have only named imports/exports. This tiny
// packer intentionally supports this exact dependency graph; no remote scripts.
let renderer='';
for(const file of ['geometry.mjs','materials.mjs','fronts.mjs','index.mjs','depth.mjs']){
 let s=await fs.readFile(path.join(ROOT,'src',file),'utf8');
 s=s.replace(/^import .*?;\s*$/gm,'').replace(/^export \{[^}]*\};\s*$/gm,'').replace(/\bexport (?=(?:const|function|class)\b)/g,'');
 if(/^import |^export /m.test(s))throw new Error('Unexpected module syntax; update the studio packer');
 renderer+='\n'+s;
}
const safe=s=>s.replace(/</g,'\\u003c');
const css=await fs.readFile(path.join(ROOT,'src/gallery.css'),'utf8');
const js=(await fs.readFile(path.join(ROOT,'src/gallery.js'),'utf8')).replace('-${side}-2000.png`','-${side}-3000.png`').replace('canvas.width=2000;canvas.height=2800;','canvas.width=3000;canvas.height=4200;').replace('ctx.drawImage(img,0,0,2000,2800);','ctx.drawImage(img,0,0,3000,4200);').replace('2000 × 2800 PNG saved.','3000 × 4200 PNG saved.');
const font=assets[cards[0].id].fonts;
const html=`<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#e7e6df"><title>NIGHT — Depth Editions</title><style>@font-face{font-family:NightDisplay;src:url('${font.display}')}@font-face{font-family:NightSans;src:url('${font.sans}')} ${css}</style></head><body>
<header><div class="wordmark">NIGHT</div><div class="collection">The Artist Editions</div></header>
<main><section class="details" aria-label="Collection"><p class="index" id="number"></p><h1 id="edition-name"></h1><p id="description" hidden></p>
<div class="editions" role="group" aria-label="Select an edition">${cards.map(c=>`<button type="button" data-card="${c.id}" aria-pressed="false"><small>${c.number}</small><span class="name">${c.edition.charAt(0)+c.edition.slice(1).toLowerCase()}</span><span class="dot" aria-hidden="true"></span></button>`).join('')}</div>
<div class="download"><button type="button" id="png-export">Save 4K PNG</button><button type="button" id="svg-export">Save SVG</button></div><p id="status" aria-live="polite"></p></section>
<section class="studio" aria-label="Card viewer"><div class="tools"><button type="button" id="turn"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 8V4L2 8l4 4V8h8a6 6 0 0 1 0 12h-3"/></svg><span>Turn card</span></button><button type="button" id="finish" aria-pressed="true"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2q0 10 10 10-10 0-10 10Q12 12 2 12q10 0 10-10Z"/></svg><span>Foil on</span></button></div><div id="stage" tabindex="0" role="group"><div id="face"></div></div><div class="caption"><span id="material-name"></span><span id="artist-name">Illustration · Tyson Tan</span></div><p class="hint">Move across the card to catch the light. Arrow keys work too.</p></section></main>
<footer><p>Illustrations by <a id="artist-credit" href="https://krita.org/en/about/mascot/" target="_blank" rel="noopener noreferrer">Tyson Tan</a> · <a id="art-license" href="https://creativecommons.org/licenses/by-sa/4.0/" target="_blank" rel="noopener noreferrer">CC BY-SA 4.0</a><br><span id="card-license">Artwork cropped and framed by NIGHT. Card images share the same license.</span></p><p class="right">Artist Editions · Collection 01<br>NIGHT Collectibles</p></footer>
<script type="application/json" id="card-data">${safe(JSON.stringify({cards,assets}))}</script><script>(()=>{${renderer}\nwindow.Night={renderCard:renderDepthCard,setCardLight:(svg,light)=>matchMedia('(prefers-reduced-motion: reduce)').matches?setCardLight(svg,light):setDepthLight(svg,light),WIDTH,HEIGHT};})();\n${js}</script></body></html>`;
await fs.mkdir(path.join(ROOT,'dist'),{recursive:true});
await fs.writeFile(path.resolve(process.argv[3]??ROOT+'/dist/NIGHT-Depth-Editions.html'),html);
console.log('Built depth studio:',(Buffer.byteLength(html)/1024/1024).toFixed(2),'MiB');
