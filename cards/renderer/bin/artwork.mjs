import fs from 'node:fs/promises';
import {discover,acquire} from '../src/discovery.mjs';
import {registerArtwork} from '../src/catalog.mjs';
import {ROOT} from '../src/node-assets.mjs';
const [command,...argv]=process.argv.slice(2),opts={};
for(let i=0;i<argv.length;i+=2){if(!argv[i]?.startsWith('--')||!argv[i+1])throw new Error('Use --option value pairs');opts[argv[i].slice(2)]=argv[i+1];}
if(command==='search'){
 const results=await discover({provider:opts.provider??'krita',query:opts.query??'',minWidth:Number(opts['min-width']??2000),minHeight:Number(opts['min-height']??2000),limit:Number(opts.limit??12)});
 const json=JSON.stringify(results,null,2);if(opts.out)await fs.writeFile(opts.out,json);else console.log(json);
 console.error(`${results.length} candidates. Pixel dimensions are verified on download; visual composition still requires inspection.`);
}else if(command==='acquire'){
 if(!opts.from||!opts.id||!opts.to)throw new Error('acquire --from candidates.json --id candidate-id --to destination');
 const records=JSON.parse(await fs.readFile(opts.from,'utf8')),r=records.find(x=>x.id===opts.id);if(!r)throw new Error('Candidate not found');
 console.log(JSON.stringify(await acquire(r,{destination:opts.to,minWidth:Number(opts['min-width']??2000),minHeight:Number(opts['min-height']??2000)}),null,2));
}else if(command==='register'){
 if(!opts.from||!opts.as)throw new Error('register --from acquired-manifest.json --as unique-art-id');
 console.log(JSON.stringify(await registerArtwork(opts.from,{root:ROOT,id:opts.as}),null,2));
}else{throw new Error('Use search, acquire, or register. See README.md.')}
