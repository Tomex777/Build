#!/usr/bin/env node
import fs from 'node:fs/promises';
import {inside,atomic} from '../src/catalogue.mjs';
import {loadManhwaRoster,validateArtLock} from '../src/manhwa.mjs';
import {researchReviewHtml} from '../src/manhwa-review.mjs';
import {loadVisualSources,manhwaGalleryHtml} from '../src/manhwa-gallery.mjs';
const roster=await loadManhwaRoster();
const lock=validateArtLock(roster,JSON.parse(await fs.readFile(inside('catalog/manhwa-art-lock.json'),'utf8')));
const visuals=await loadVisualSources(roster);
const out=inside('dist/manhwa-showcase');
await atomic(out+'/index.html',manhwaGalleryHtml({roster,visuals}));
await atomic(out+'/roster.html',researchReviewHtml(roster,lock));
await atomic(out+'/art-sources.json',JSON.stringify(visuals,null,2)+'\n');
await atomic(out+'/summary.json',JSON.stringify({
 totalCandidates:roster.editions.length,visualStudies:visuals.entries.length,
 artPinned:Object.keys(lock.editions).length,
 publishedApprovedIllustrations:0,
 note:'Public review showcase linking third-party manhwa image references. Does not license artwork or approve collectible merchandise.'
},null,2)+'\n');
console.log('NIGHT manhwa illustrated visual studies',visuals.entries.length,'of',roster.editions.length);
