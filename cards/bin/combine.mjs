import fs from 'node:fs/promises';
import path from 'node:path';
import {inside,atomic,loadCatalogue,rendererDigest,reviewFingerprint} from '../src/catalogue.mjs';
import {readJson,presentation} from '../src/pipeline.mjs';
const folder=inside(process.argv[2]??'dist/candidates'),count=Number(process.argv[3]??4);
const names=(await fs.readdir(folder)).filter(n=>/^build-part-\d+\.json$/.test(n)).sort();
if(names.length!==count)throw Error('Missing build parts');
const catalogue=await loadCatalogue(),renderer=await rendererDigest(),cards=[];
for(const name of names){
 const r=await readJson(path.join(folder,name));
 if(r.width!==3000||r.renderer!==renderer||r.failures.length)throw Error('A build part failed or used a different renderer');
 cards.push(...r.cards);
}
const expected=catalogue.cards.flatMap(c=>c.designs.map(d=>c.id+'--'+d));
if(cards.length!==expected.length||new Set(cards.map(c=>c.id)).size!==cards.length||expected.some(id=>!cards.some(c=>c.id===id)))throw Error('Incomplete or duplicate combined catalogue');
for(const entry of cards){const c=catalogue.cards.find(c=>entry.id===c.id+'--'+entry.design);if(!c||reviewFingerprint(c,entry.design,renderer,3000)!==entry.fingerprint)throw Error('Stale build part');}
cards.sort((a,b)=>a.number-b.number||catalogue.cards.find(c=>c.number===a.number).designs.indexOf(a.design)-catalogue.cards.find(c=>c.number===b.number).designs.indexOf(b.design));
const report={version:1,series:'bleach',width:3000,height:4800,renderer,builtAt:new Date().toISOString(),cards,failures:[]};
await presentation(report,folder);await atomic(path.join(folder,'build.json'),JSON.stringify(report,null,2)+'\n');
console.log('Combined',cards.length,'full-resolution editions');
