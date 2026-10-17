import fs from 'node:fs/promises';
import path from 'node:path';
import {ROOT} from '../src/node-assets.mjs';
import {characterCards} from '../src/characters.mjs';
import {loadCharacterAssets} from '../src/character-assets.mjs';

// Reuse the original studio and renderer without changing their source files.
await import('./build.mjs');
const profile=JSON.parse(await fs.readFile(path.resolve(process.argv[2]??path.join(ROOT,'examples/characters/naruto.json')),'utf8'));
const out=path.resolve(process.argv[3]??path.join(ROOT,'dist',profile.id));
const width=Number(process.argv[4]??3000),height=Math.round(width*1.4);
if(!Number.isInteger(width)||width<160||width>4000)throw new Error('Invalid export width');
const templates=JSON.parse(await fs.readFile(path.join(ROOT,'examples/cards.json'),'utf8'));
const cards=characterCards(templates,profile),art=await loadCharacterAssets(profile);
const assets=Object.fromEntries(cards.map(c=>[c.id,art]));
let html=await fs.readFile(path.join(ROOT,'dist/index.html'),'utf8');
const payload=JSON.stringify({cards,assets}).replace(/</g,'\\u003c');
html=html.replace(/(<script type="application\/json" id="card-data">)[\s\S]*?(<\/script>)/,()=>`<script type="application/json" id="card-data">${payload}</script>`);
for(let i=0;i<cards.length;i++)html=html.replace(`data-card="${templates[i].id}"`,`data-card="${cards[i].id}"`);
// Only the exported raster resolution differs; card design and studio CSS stay fixed.
const substitutions=[
 ['-${side}-2000.png`','-${side}-'+width+'.png`'],
 ['canvas.width=2000;canvas.height=2800;',`canvas.width=${width};canvas.height=${height};`],
 ['ctx.drawImage(img,0,0,2000,2800);',`ctx.drawImage(img,0,0,${width},${height});`],
 ["status.textContent='2000 × 2800 PNG saved.';",`status.textContent='${width} × ${height} PNG saved.';`]
];
for(const [from,to] of substitutions){if(!html.includes(from))throw new Error('Original export code changed; review the studio adapter');html=html.replace(from,to);}
await fs.mkdir(out,{recursive:true});
const filename=path.join(out,`NIGHT-${profile.id}.html`);
await fs.writeFile(filename,html);
console.log('Built character studio:',filename);
