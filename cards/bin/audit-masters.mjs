import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import {inside,loadCatalogue,rendererDigest,reviewFingerprint,digest,atomic} from '../src/catalogue.mjs';
import {acquireArtwork} from '../src/artwork.mjs';
import {cardSvg} from '../src/render.mjs';

// Independently regenerate SVGs and re-derive phone images from master bytes.
const folder=inside(process.argv[2]??'dist/bleach-refined-masters');
const report=JSON.parse(await fs.readFile(path.join(folder,'build.json'),'utf8'));
const catalogue=await loadCatalogue(),renderer=await rendererDigest();
const expected=catalogue.cards.flatMap(c=>c.designs.map(d=>c.id+'--'+d));
assert.equal(report.renderer,renderer);assert.equal(report.width,3000);assert.equal(report.height,4800);
assert.deepEqual(report.failures,[]);assert.equal(report.cards.length,expected.length);
assert.deepEqual([...report.cards.map(c=>c.id)].sort(),[...expected].sort());
assert.equal(new Set(report.cards.map(c=>c.artworkSHA256)).size,expected.length);
let minimumCrossing=Infinity;
for(const entry of report.cards){
  const card=catalogue.cards.find(c=>entry.id===c.id+'--'+entry.design);
  assert.ok(card,entry.id);assert.equal(entry.artworkSHA256,card.source.sha256);
  assert.equal(entry.fingerprint,reviewFingerprint(card,entry.design,renderer,3000),entry.id);
  const assets=await acquireArtwork(card),svg=await cardSvg(card,assets,entry.design);
  assert.equal(digest(svg),entry.svgSHA256,entry.id+' fresh SVG');
  assert.equal(digest(await fs.readFile(path.join(folder,entry.svg))),entry.svgSHA256,entry.id+' saved SVG');
  const png=await fs.readFile(path.join(folder,entry.png));assert.equal(digest(png),entry.pngSHA256,entry.id+' PNG');
  const m=await sharp(png).metadata();assert.equal(m.width,3000);assert.equal(m.height,4800);
  const phone=await sharp(png).resize(320,512).png().toBuffer();
  assert.equal(digest(phone),digest(await fs.readFile(path.join(folder,entry.phone))),entry.id+' phone');
  assert.equal(digest(await fs.readFile(path.join(folder,entry.preview))),entry.previewSHA256,entry.id+' gallery preview');
  assert.equal(entry.qa.powerUniverse,'bleach');assert.ok(entry.qa.characterBreakout.required);
  assert.ok(entry.qa.characterBreakout.longestCrossing>=24,entry.id+' frame crossing');
  minimumCrossing=Math.min(minimumCrossing,entry.qa.characterBreakout.longestCrossing);
}
const result={checkedAt:new Date().toISOString(),cards:report.cards.length,characters:new Set(catalogue.cards.map(c=>c.character)).size,distinctOriginals:new Set(report.cards.map(c=>c.artworkSHA256)).size,dimensions:[3000,4800],renderer,regeneratedSVGsMatch:true,pngHashesMatch:true,phoneImagesReproduced:true,previewHashesMatch:true,fingerprintsMatch:true,minimumCrossing,failures:0};
await atomic(inside('.cache/master-review/audit.json'),JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify(result,null,2));
