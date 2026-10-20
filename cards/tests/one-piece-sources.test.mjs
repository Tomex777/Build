import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {inside} from '../src/catalogue.mjs';
import {POWER_SCALE} from '../src/power.mjs';
import {designForRank} from '../src/tiers.mjs';
import {seriesDisplayName,collectionGallery} from '../src/gallery.mjs';

const roster=JSON.parse(await fs.readFile(inside('catalog/one-piece-sources.json'),'utf8'));
test('One Piece sources are verified official-character page candidates',()=>{
  assert.equal(roster.series,'one-piece');
  assert.equal(roster.sources.length,11);
  const ids=new Set(),numbers=new Set(),urls=new Set();
  for(const s of roster.sources){
    assert.ok(!ids.has(s.id)); ids.add(s.id);
    assert.ok(!numbers.has(s.number)); numbers.add(s.number);
    assert.ok(!urls.has(s.original.image)); urls.add(s.original.image);
    assert.ok(s.original.page.startsWith('https://www.onepiece-film.jp/'));
    assert.match(s.original.image,/\/character\/chara\d{2}\.png$/);
    assert.equal(s.continuity,'one-piece-film-red');
    assert.equal(s.original.usage,'reference-only');
    assert.match(s.original.rights,/rights reserved/i);
    assert.ok(POWER_SCALE.ranks.includes(s.power.rank));
    assert.equal(typeof designForRank(s.power.rank),'string');
    assert.ok(s.power.basis.length>15&&s.power.limits.length>15);
    const r=s.extraction.normalizedRect;
    assert.equal(r.length,4);
    assert.ok(r.every(v=>v>=0&&v<=1));
    assert.ok(r[0]+r[2]<=1.001&&r[1]+r[3]<=1.001);
  }
});
test('a costume is never mislabelled as a powerful transformation',()=>{
  const list=JSON.stringify(roster.sources.map(({id,form,formId})=>({id,form,formId})));
  assert.ok(!/gear-5|king-of-hell|ifrit-jambe|demonio-fleur/i.test(list));
  assert.ok(!roster.sources.some(s=>['SSS','EX'].includes(s.power.rank)));
  assert.ok(roster.sources.some(s=>s.id==='luffy-film-red'));
  assert.ok(roster.sources.some(s=>s.id==='shanks-film-red'));
});
test('the gallery is universal NIGHT architecture, not BLEACH-only text',()=>{
  assert.equal(seriesDisplayName('bleach'),'BLEACH');
  assert.equal(seriesDisplayName('one-piece'),'ONE PIECE');
  const fake={series:'one-piece',cards:[]};
  const html=collectionGallery(fake);
  assert.match(html,/NIGHT \/ ONE PIECE/);
  assert.doesNotMatch(html,/\bBLEACH\b/);
});
