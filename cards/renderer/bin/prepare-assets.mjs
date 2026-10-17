/** Restore/refresh the showcase through the same verified discovery pipeline. */
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import {ROOT} from '../src/node-assets.mjs';
import {discover,acquire,hash} from '../src/discovery.mjs';
import {registerArtwork} from '../src/catalog.mjs';
const catalog=JSON.parse(await fs.readFile(path.join(ROOT,'assets/catalog.json'),'utf8'));
const refresh=process.argv.includes('--refresh');
let intact=true;for(const a of catalog){try{intact&&=hash(await fs.readFile(path.join(ROOT,a.localPath)))===a.sha256}catch{intact=false}}
if(intact&&!refresh){console.log('Bundled artwork verified. Use --refresh to re-acquire from the current licensed source.');process.exit(0)}
const temporary=await fs.mkdtemp(path.join(os.tmpdir(),'night-showcase-'));
try{
 const candidates=await discover({provider:'krita',limit:30});
 for(const a of catalog){
  const record=candidates.find(r=>r.originalUrl===a.originalUrl);if(!record)throw new Error(`Licensed artwork is no longer available: ${a.id}`);
  const original=await acquire(record,{destination:path.join(temporary,'originals')});
  await registerArtwork(path.join(temporary,'originals',`${original.id}.json`),{root:temporary,id:a.id});
 }
 const next=JSON.parse(await fs.readFile(path.join(temporary,'assets/catalog.json'),'utf8'));
 for(const a of next)await fs.copyFile(path.join(temporary,a.localPath),path.join(ROOT,a.localPath));
 await fs.writeFile(path.join(ROOT,'assets/catalog.json'),JSON.stringify(next,null,2));
 console.log('Artwork restored. Render and inspect the refreshed compositions before approving them.');
}finally{await fs.rm(temporary,{recursive:true,force:true})}
