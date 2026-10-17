import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import {renderCard} from '../src/index.mjs';
import {createRasterizer} from '../src/raster.mjs';
import {loadAssets,ROOT} from '../src/node-assets.mjs';
const out=path.resolve(process.argv[2]??path.join(ROOT,'dist')),ids=process.argv.slice(3);
await fs.mkdir(out,{recursive:true});
const cards=JSON.parse(await fs.readFile(path.join(ROOT,'examples/cards.json'),'utf8')).filter(c=>!ids.length||ids.includes(c.id));
if(!cards.length)throw new Error('No matching cards');
const raster=await createRasterizer();
try{
 for(const c of cards){
  const assets=await loadAssets(c);
  for(const side of ['front','back']){
   const svg=renderCard(c,assets,{side});
   const png=await raster.render(svg);
   await fs.writeFile(path.join(out,`${c.id}-${side}.svg`),svg);
   await fs.writeFile(path.join(out,`${c.id}-${side}-2000.png`),png);
   if(side==='front'){
    for(const width of [320,360])await fs.writeFile(path.join(out,`${c.id}-whatsapp-${width}.png`),await raster.render(svg,{width}));
    await sharp(png).resize(1143,1600).flatten({background:'#061c24'}).jpeg({quality:94,mozjpeg:true}).toFile(path.join(out,`${c.id}-whatsapp-1600.jpg`));
    const caption=`NIGHT · ${c.name} / ${c.edition}\n${assets.title} — ${assets.artist}\n${assets.sourceUrl}\n${assets.license}: ${assets.licenseUrl}\nCropped and framed by NIGHT. Card image: ${assets.outputLicense} (${assets.outputLicenseUrl}).`;
    await fs.writeFile(path.join(out,`${c.id}-caption.txt`),caption);
   }
  }
  console.log('Rendered',c.id);
 }
}finally{await raster.close()}
