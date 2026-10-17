import fs from 'node:fs/promises';
import {existsSync} from 'node:fs';
import path from 'node:path';
import {ROOT} from '../src/node-assets.mjs';
import {loadCharacterAssets} from '../src/character-assets.mjs';
import {characterCards} from '../src/characters.mjs';
import {renderDepthCard} from '../src/depth.mjs';
import {createTiledRasterizer} from '../src/tiled-raster.mjs';

const profile=JSON.parse(await fs.readFile(path.resolve(process.argv[2]??ROOT+'/examples/characters/naruto.json'),'utf8'));
const out=path.resolve(process.argv[3]??ROOT+'/dist/depth');
const width=Number(process.argv[4]??6000),format=process.argv[5]??'png';
const cards=characterCards(JSON.parse(await fs.readFile(ROOT+'/examples/cards.json','utf8')),profile).filter(c=>c.theme!=='verdant');
const art=await loadCharacterAssets(profile);
const engine=await createTiledRasterizer({executablePath:process.env.NIGHT_BROWSER_PATH??(existsSync('/tmp/night-browser/chrome-headless-shell-linux64/chrome-headless-shell')?'/tmp/night-browser/chrome-headless-shell-linux64/chrome-headless-shell':undefined)});
await fs.mkdir(out,{recursive:true});const exports=[];
try{
 for(const c of cards){
  const svg=renderDepthCard(c,art);
  await fs.writeFile(path.join(out,`${c.id}.svg`),svg);
  const target=path.join(out,`${c.id}-${width}.${format==='jpeg'?'jpg':'png'}`);
  const result=await engine.renderToFile(svg,target,{width,format,onProgress:({completed,total})=>{if(completed===4||completed===total)console.log(`${c.id}: ${completed}/${total} tiles rendered`);}});
  exports.push(result);console.log(JSON.stringify(result));
 }
 await fs.writeFile(path.join(out,`exports-${width}.json`),JSON.stringify({renderer:'NIGHT Depth 2.0',nativeArtwork:[art.artWidth,art.artHeight],exports},null,2));
}finally{await engine.close();}
