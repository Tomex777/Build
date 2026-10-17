import fs from 'node:fs/promises';
import path from 'node:path';
import {existsSync} from 'node:fs';
import sharp from 'sharp';
import {ROOT} from '../src/node-assets.mjs';
import {characterCards} from '../src/characters.mjs';
import {loadCharacterAssets} from '../src/character-assets.mjs';
import {renderPortraitCard} from '../src/portrait.mjs';
import {createRasterizer} from '../src/raster.mjs';

const out=path.resolve(process.argv[2]??ROOT+'/dist/tall');
const width=Number(process.argv[3]??3000);
const compare=process.argv.includes('--compare');
const characters=process.argv.includes('--ichigo-only')?['ichigo']:['ichigo','naruto'];
const profileFlag=process.argv.indexOf('--profile');
if(profileFlag>=0&&!process.argv[profileFlag+1])throw new Error('--profile needs a JSON filename');
const profiles=profileFlag>=0?[path.resolve(process.argv[profileFlag+1])]:characters.map(c=>`${ROOT}/examples/characters/${c}.json`);
const templates=JSON.parse(await fs.readFile(ROOT+'/examples/cards.json','utf8'));
const browser=process.env.NIGHT_BROWSER_PATH??(existsSync('/tmp/night-browser/chrome-headless-shell-linux64/chrome-headless-shell')?'/tmp/night-browser/chrome-headless-shell-linux64/chrome-headless-shell':undefined);
const engine=await createRasterizer({executablePath:browser,timeout:90000});
await fs.mkdir(out,{recursive:true});
const records=[];
try{
 for(const filename of profiles){
  const profile=JSON.parse(await fs.readFile(filename,'utf8'));
  const character=profile.id;
  const assets=await loadCharacterAssets(profile);
  const cards=characterCards(templates,profile).filter(c=>c.theme!=='verdant');
  for(const card of cards)for(const height of compare?[1400,1600]:[1600]){
   const svg=renderPortraitCard(card,assets,{height});
   const png=await engine.render(svg,{width});
   const meta=await sharp(png).metadata();
   if(meta.width!==width||meta.height!==Math.round(width*height/1000))throw new Error('Unexpected rendered dimensions');
   await sharp(png,{failOn:'warning'}).resize(20).raw().toBuffer();
   const stem=`${card.id}-${height}-${width}`;
   await fs.writeFile(path.join(out,stem+'.svg'),svg);
   await fs.writeFile(path.join(out,stem+'.png'),png);
   records.push({character,edition:card.theme,height,width:meta.width,pixelsHigh:meta.height,filename:stem+'.png'});
   console.log('Rendered',stem,`${meta.width}×${meta.height}`);
  }
 }
 await fs.writeFile(path.join(out,'renders.json'),JSON.stringify(records,null,2)+'\n');
}finally{await engine.close();}
