import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {build,approve,publish,readJson} from '../src/pipeline.mjs';
import {inside,loadCatalogue,rendererDigest,reviewFingerprint} from '../src/catalogue.mjs';
import {exportGallery} from './export-gallery.mjs';
import {openCardLibrary} from '../src/bot.mjs';
import {discover} from '../src/discovery.mjs';
const [command,...args]=process.argv.slice(2), options={};
for(let i=0;i<args.length;i+=2){if(!args[i].startsWith('--')||args[i+1]===undefined)throw Error('Use --option value');options[args[i].slice(2)]=args[i+1];}
const csv=s=>s?s.split(',').filter(Boolean):undefined;
const common={series:options.series??'bleach',out:options.out??'dist/candidates'};
if(command==='build'){const r=await build({...common,width:Number(options.width??3000),only:csv(options.only),designs:csv(options.designs)});console.log(`${r.cards.length} rendered; ${r.failures.length} quarantined`);if(r.failures.length)process.exitCode=1;}
else if(command==='approve'){await approve({...common,ids:csv(options.ids),reviewer:options.reviewer??'Codex'});console.log('Recorded visual reviews');}
else if(command==='publish'){const r=await publish({...common,destination:options.destination??'dist/published'});console.log(`${r.length} approved cards published`);}
else if(command==='verify'){const library=await openCardLibrary(inside(options.folder??'dist/published'));for(const c of library.manifest.cards)await library.image(c.id);console.log(`Verified ${library.manifest.cards.length} bot-readable cards`);}
else if(command==='pack'){const r=spawnSync('python3',[inside('bin/pack.py'),inside(options.folder??'dist/published'),inside(options.file??'dist/NIGHT-Bleach-Collection.zip')],{stdio:'inherit'});process.exitCode=r.status??1;}
else if(command==='gallery'){
  const folder=inside(common.out),report=await readJson(path.join(folder,'build.json')),renderer=await rendererDigest(),catalogue=await loadCatalogue(common.series);
  const expected=catalogue.cards.flatMap(c=>c.designs.map(d=>c.id+'--'+d));
  if(report.failures.length||report.renderer!==renderer||report.cards.length!==expected.length||new Set(report.cards.map(c=>c.id)).size!==expected.length||expected.some(id=>!report.cards.some(c=>c.id===id)))throw Error('Use a complete current collection build');
  for(const entry of report.cards){const card=catalogue.cards.find(c=>entry.id===c.id+'--'+entry.design);if(!card||reviewFingerprint(card,entry.design,renderer,report.width)!==entry.fingerprint)throw Error('Stale gallery card: '+entry.id);}
  const destination=inside(options.file??'dist/Bleach-Collection.html');await exportGallery(report,folder,destination);console.log('Saved',destination);
}
else if(command==='discover')console.log(JSON.stringify(await discover({provider:options.provider??'brave-souls',query:options.query??'',limit:Number(options.limit??150)}),null,2));
else throw Error('Commands: discover, build, approve, publish, verify, pack, gallery');
