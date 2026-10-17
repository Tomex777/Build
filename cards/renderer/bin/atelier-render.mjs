import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import sharp from 'sharp';
import {loadCharacterAssets} from '../src/character-assets.mjs';
import {renderAtelierCard,ATELIER_DESIGNS} from '../src/atelier.mjs';
import {createRasterizer} from '../src/raster.mjs';
const profilePath=process.argv[2]??'examples/characters/ichigo.json';
const out=path.resolve(process.argv[3]??'dist/atelier');
const width=Number(process.argv[4]??3000);
const selected=process.argv[5]?.split(',');
const character=JSON.parse(await fs.readFile(profilePath,'utf8'));
const assets=await loadCharacterAssets(character);
const browser='/tmp/night-browser/chrome-headless-shell-linux64/chrome-headless-shell';
const executablePath=process.env.NIGHT_BROWSER_PATH??await fs.access(browser).then(()=>browser,()=>undefined);
const raster=await createRasterizer({executablePath,timeout:90000});
await fs.mkdir(out,{recursive:true});
const exports=[];
try{
 for(const d of ATELIER_DESIGNS.filter(d=>!selected||selected.includes(d.id))){
  const stem=`${d.number}-${d.id}-${character.id}`;
  const svg=renderAtelierCard(character,assets,{design:d.id});
  const png=await raster.render(svg,{width});
  const image=sharp(png);const info=await image.metadata();await image.raw().toBuffer();
  if(info.width!==width||info.height!==width*1.6)throw new Error('Unexpected export dimensions');
  await fs.writeFile(path.join(out,stem+'.svg.pending'),svg);
  await fs.writeFile(path.join(out,stem+'.png.pending'),png);
  await fs.rename(path.join(out,stem+'.svg.pending'),path.join(out,stem+'.svg'));
  await fs.rename(path.join(out,stem+'.png.pending'),path.join(out,stem+'.png'));
  const entry={...d,character:character.id,width:info.width,height:info.height,png:stem+'.png',svg:stem+'.svg',sha256:crypto.createHash('sha256').update(png).digest('hex'),bytes:png.length};
  exports.push(entry);console.log(`${d.number} ${d.name}: ${info.width}×${info.height}, ${png.length} bytes`);
 }
 await fs.writeFile(path.join(out,'renders.json'),JSON.stringify({generatedAt:new Date().toISOString(),character:character.id,rights:assets.rights,artworkSource:assets.sourceUrl,artworkSHA256:character.artwork.sha256,exports},null,2));
}finally{await raster.close()}
