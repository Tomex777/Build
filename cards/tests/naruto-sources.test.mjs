import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {inside} from '../src/catalogue.mjs';
import {designForRank} from '../src/tiers.mjs';
import {POWER_SCALE} from '../src/power.mjs';
const data=JSON.parse(await fs.readFile(inside('catalog/naruto-sources.json'),'utf8'));
const ids=new Set(),images=new Set(),numbers=new Set(),formIds=new Set();
test('Naruto source cards are original, distinct, transparent official game portraits',()=>{
 assert.equal(data.series,'naruto');assert.equal(data.sources.length,29);
 for(const item of data.sources){
  assert.match(item.id,/^[a-z][a-z0-9-]*$/);assert.ok(!ids.has(item.id));ids.add(item.id);
  assert.ok(!images.has(item.source.image));images.add(item.source.image);
  assert.ok(!numbers.has(item.number));numbers.add(item.number);
  const key=item.character+'-'+item.formId;assert.ok(!formIds.has(key));formIds.add(key);
  const image=new URL(item.source.image);
  assert.equal(image.origin,'https://naruto-game.bngames.net');
  assert.match(image.pathname,/^\/images\/character\/\d{2}_\d{2}\/img_character\.png$/);
  const code=image.pathname.match(/\d{2}_\d{2}/)[0];
  assert.equal(item.source.page,'https://naruto-game.bngames.net/character/'+code+'.html');
  assert.equal(item.source.usage,'reference-only');
  assert.match(item.source.rights,/rights reserved/i);
  assert.ok(POWER_SCALE.ranks.includes(item.rank.letter));
  assert.ok(designForRank(item.rank.letter));
  assert.ok(item.rank.basis.length>30&&item.rank.limits.length>30);
  assert.equal(['SSS','EX'].includes(item.rank.letter),false,'Upper ranks need audited feats');
 }
});
test('Exact form identity is not confused with a future transformation',()=>{
 const get=id=>data.sources.find(x=>x.id===id);
 assert.match(get('naruto-six-paths').source.image,/01_01/);
 assert.match(get('naruto-shippuden').source.image,/02_01/);
 assert.match(get('naruto-child').source.image,/09_02/);
 assert.match(get('sasuke-rinne-sharingan').source.image,/01_02/);
 assert.match(get('sasuke-eternal-mangekyo').source.image,/07_06/);
 assert.notEqual(get('madara-war').source.image,get('madara-edo').source.image);
 for(const expected of ['naruto-baryon-mode','naruto-kcm1','naruto-sage-mode','kakashi-double-mangekyo','madara-ten-tails']){
   assert.ok(data.pendingForms.some(x=>x.id===expected));
   assert.ok(!data.sources.some(x=>x.id===expected));
 }
});
