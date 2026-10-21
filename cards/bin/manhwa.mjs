#!/usr/bin/env node
// Candidate catalogue tooling. Does NOT license art, approve visuals, or deploy.
import fs from 'node:fs/promises';
import sharp from 'sharp';
import {inside,atomic} from '../src/catalogue.mjs';
import {loadManhwaRoster,validateArtLock,collectionSummary,toRendererCard,toRomanceRendererCard,verifyLocalSource} from '../src/manhwa.mjs';

const roster = await loadManhwaRoster();
const lock = validateArtLock(roster,JSON.parse(await fs.readFile(inside('catalog/manhwa-art-lock.json'),'utf8')));
const command = process.argv[2] || 'audit';
if (command === 'audit') {
  const summary = collectionSummary(roster,lock);
  for (const s of summary) {
    console.log(`${s.name}: ${s.total} candidates (${s.power} power, ${s.character} story, ${s.romance} romance); ${s.artPinned} pinned original sources`);
  }
  console.log('Unapproved candidates:',roster.editions.length);
  console.log('Production-ready:',0,'— art provenance and actual visual approval required');
} else if (command === 'prepare') {
  let total = 0;
  for (const series of roster.series) {
    const candidates = roster.editions.filter(e => e.series===series.id);
    const prepared=[];
    for (const c of candidates) {
      const art=lock.editions[c.id];
      if (!art) continue;
      const parts = c.family === 'romance' ? art.subjects : [art];
      for (const part of parts) {
        await verifyLocalSource(part);
        const meta = await sharp(inside(part.localPath),{limitInputPixels:30_000_000}).metadata();
        if (meta.width!==part.width || meta.height!==part.height || !meta.hasAlpha
          || !['png','webp'].includes(meta.format))
          throw Error('Native transparency / dimension mismatch: '+c.id+' / '+part.subject);
        const raw=await sharp(inside(part.localPath)).ensureAlpha().stats();
        if (raw.channels[3].min>32)
          throw Error('Art has no meaningful transparent pixels: '+c.id+' / '+part.subject);
      }
      prepared.push(c.family === 'romance'
        ? toRomanceRendererCard(c,art,candidates.indexOf(c)+1)
        : toRendererCard(c,art,candidates.indexOf(c)+1));
    }
    if (!prepared.length) { console.log(series.name,'— no pinned cutouts; rendering blocked'); continue; }
    const preparedIds=new Set(prepared.map(c=>c.id));
    const data={version:1,series:series.id,name:series.name+' / NIGHT',
      dimensions:[3000,4800],status:'source-pinned-candidates-not-approved',
      artworkPolicy:{distinctPerEdition:true,breakoutRequired:true},
      rightsNote:series.rights+' Reference only; no redistribution approval.',
      cards:prepared,pendingForms:candidates.filter(c=>!preparedIds.has(c.id)).map(c=>({
        id:c.id,character:c.subjects.join(' + '),form:c.form,family:c.family,
        reason:'Unacquired or unverified form-accurate transparent cutout(s).'
      }))};
    await atomic(inside('catalog/'+series.id+'.json'),JSON.stringify(data,null,2)+'\n');
    total+=prepared.length;
    console.log('Prepared',prepared.length,series.name,'source-pinned candidates; 3000×4800 SVG/PNG not yet rendered');
  }
  if (!total) console.log('No art was forged or substituted. Source locks are empty.');
  else console.log('Run npm run build -- --series <series-id> --width 3000 --out dist/<series-id> and inspect each master + phone proof.');
} else {
  throw Error('Usage: node bin/manhwa.mjs [audit|prepare]');
}
