import fs from 'node:fs/promises';
import {existsSync} from 'node:fs';
import path from 'node:path';
import sharp from 'sharp';
import {ROOT} from '../src/node-assets.mjs';
import {characterCards} from '../src/characters.mjs';
import {loadCharacterAssets} from '../src/character-assets.mjs';
import {renderDepthCard} from '../src/depth.mjs';
import {createRasterizer} from '../src/raster.mjs';

const profile=JSON.parse(await fs.readFile(path.resolve(process.argv[2]??ROOT+'/examples/characters/naruto.json'),'utf8'));
const out=path.resolve(process.argv[3]??ROOT+'/dist/depth');
const width=Number(process.argv[4]??3000);
const templates=JSON.parse(await fs.readFile(ROOT+'/examples/cards.json','utf8'));
const cards=characterCards(templates,profile).filter(c=>c.theme!=='verdant').map(c=>({...c,...(profile.depthSubject?{depthSubject:profile.depthSubject}:{})}));
const art=await loadCharacterAssets(profile);
const raster=await createRasterizer({executablePath:process.env.NIGHT_BROWSER_PATH??(existsSync('/tmp/night-browser/chrome-headless-shell-linux64/chrome-headless-shell')?'/tmp/night-browser/chrome-headless-shell-linux64/chrome-headless-shell':undefined),timeout:90000});
await fs.mkdir(out,{recursive:true});
const layers=[];
try{
 for(const [i,c]of cards.entries()){
  const svg=renderDepthCard(c,art);
  await fs.writeFile(path.join(out,`${c.id}.svg`),svg);
  const png=await raster.render(svg,{width});
  await fs.writeFile(path.join(out,`${c.id}-${width}.png`),png);
  layers.push({input:await sharp(png).resize(540,756).png().toBuffer(),left:25+i*575,top:28});
  console.log('Rendered',c.id,width,Math.round(width*1.4));
 }
 await sharp({create:{width:1165,height:812,channels:3,background:'#cbd0ca'}}).composite(layers).png().toFile(path.join(out,'comparison.png'));
}finally{await raster.close();}
