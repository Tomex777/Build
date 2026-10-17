import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import sharp from 'sharp';
import {loadNoctisAssets} from '../src/noctis-assets.mjs';
import {renderNoctisCard,NOCTIS_DESIGNS} from '../src/noctis.mjs';
import {createRasterizer} from '../src/raster.mjs';
const profile=process.argv[2]??'examples/characters/ichigo.json',out=path.resolve(process.argv[3]??'dist/noctis'),width=Number(process.argv[4]??3000),selection=process.argv[5]?.split(',');
const c=JSON.parse(await fs.readFile(profile,'utf8')),a=await loadNoctisAssets(c),records=[];
await fs.mkdir(out,{recursive:true});
const local='/tmp/night-browser/chrome-headless-shell-linux64/chrome-headless-shell';
const executablePath=process.env.NIGHT_BROWSER_PATH??await fs.access(local).then(()=>local,()=>undefined);
const raster=await createRasterizer({executablePath,timeout:120000});
try{for(const d of NOCTIS_DESIGNS.filter(d=>!selection||selection.includes(d.id))){
 const stem=`${d.number}-${d.id}-${c.id}`,svg=renderNoctisCard(c,a,{design:d.id});
 let png;
 if(d.id==='obsidian'&&c.id==='ichigo'&&width===3000)png=await fs.readFile('preserved/obsidian-v1/01-obsidian-ichigo.png');
 else png=await raster.render(svg,{width});
 const decoded=sharp(png);const m=await decoded.metadata();await decoded.raw().toBuffer();if(m.width!==width||m.height!==width*1.6)throw new Error('Unexpected raster dimensions');
 for(const [ext,bytes]of [['svg',svg],['png',png]]){const dest=path.join(out,stem+'.'+ext);await fs.writeFile(dest+'.pending',bytes);await fs.rename(dest+'.pending',dest);}
 records.push({...d,png:stem+'.png',svg:stem+'.svg',width:m.width,height:m.height,sha256:crypto.createHash('sha256').update(png).digest('hex'),bytes:png.length});
 await fs.writeFile(path.join(out,'renders.json'),JSON.stringify({character:c.id,rights:a.rights,source:a.sourceUrl,exports:records},null,2));
 console.log(`${d.number} ${d.name}: ${m.width}×${m.height}`);
}}finally{await raster.close()}
