import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import {ROOT} from '../src/node-assets.mjs';
import {characterCards} from '../src/characters.mjs';

const profile=JSON.parse(await fs.readFile(path.resolve(process.argv[2]??path.join(ROOT,'examples/characters/naruto.json')),'utf8'));
const out=path.resolve(process.argv[3]??path.join(ROOT,'dist',profile.id));
const width=Number(process.argv[4]??3000);
const templates=JSON.parse(await fs.readFile(path.join(ROOT,'examples/cards.json'),'utf8'));
const cards=characterCards(templates,profile);
const layers=[];
for(let i=0;i<cards.length;i++){
 const input=await sharp(path.join(out,`NIGHT-${cards[i].id}-${width}.png`)).resize(600,840).png().toBuffer();
 layers.push({input,left:20+i*620,top:35});
}
const filename=path.join(out,`NIGHT-${profile.id}-Three-Editions.png`);
await sharp({create:{width:cards.length*620+20,height:920,channels:3,background:'#e7e6df'}}).composite(layers).png().toFile(filename);
console.log('Wrote actual-render comparison:',filename);
