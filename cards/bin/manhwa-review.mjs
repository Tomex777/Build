#!/usr/bin/env node
import fs from 'node:fs/promises';
import {inside,atomic} from '../src/catalogue.mjs';
import {loadManhwaRoster,validateArtLock,collectionSummary} from '../src/manhwa.mjs';
import {researchReviewHtml} from '../src/manhwa-review.mjs';

const roster=await loadManhwaRoster();
const lock=validateArtLock(roster,JSON.parse(await fs.readFile(inside('catalog/manhwa-art-lock.json'),'utf8')));
const out=inside('dist/manhwa-research');
await atomic(out+'/index.html',researchReviewHtml(roster,lock));
await atomic(out+'/summary.json',JSON.stringify({
  generated:new Date().toISOString(),series:collectionSummary(roster,lock),
  total:roster.editions.length,chapterVerified:roster.editions.filter(x=>x.research.verification==='chapter-verified').length,
  artworkPinned:Object.keys(lock.editions).length,
  note:'Research roster only: not illustrated card masters, not approved for distribution.'
},null,2)+'\n');
console.log('Research index exported: dist/manhwa-research/index.html');
