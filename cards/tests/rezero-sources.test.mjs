import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {inside} from '../src/catalogue.mjs';
import {designForRank} from '../src/tiers.mjs';
import {POWER_SCALE} from '../src/power.mjs';
const data=JSON.parse(await fs.readFile(inside('catalog/rezero-sources.json'),'utf8'));
const ids=new Set(),urls=new Set(),numbers=new Set();
test('Re:Zero uses independently ranked official-form candidates',()=>{
  assert.equal(data.series,'rezero');
  assert.equal(data.sources.length,38);
  for(const item of data.sources){
    assert.ok(!ids.has(item.id));ids.add(item.id);
    assert.ok(!urls.has(item.source.image));urls.add(item.source.image);
    assert.ok(!numbers.has(item.number));numbers.add(item.number);
    assert.ok(item.source.image.startsWith('https://re-zero-anime.jp/tv/assets/character/c/'));
    assert.equal(item.source.page,'https://re-zero-anime.jp/tv/character/');
    assert.equal(item.source.usage,'reference-only');
    assert.match(item.source.rights,/rights reserved/i);
    assert.ok(item.continuity==='rezero-anime');
    assert.ok(POWER_SCALE.ranks.includes(item.rank.letter));
    assert.ok(designForRank(item.rank.letter));
    assert.ok(item.rank.basis.length>20&&item.rank.limits.length>20);
  }
});
test('Distinct character forms must not inherit another form or rank',()=>{
  const get=id=>data.sources.find(c=>c.id===id);
  assert.equal(get('rem-maid').source.image.endsWith('4b.webp'),true);
  assert.equal(get('rem-recovering').source.image.endsWith('4c.webp'),true);
  assert.equal(get('rem-recovering').rank.letter,'F');
  assert.notEqual(get('rem-maid').rank.letter,'F');
  assert.notEqual(get('ram-maid').source.image,get('ram-journey').source.image);
  assert.equal(get('garfiel-human').formId,'sanctuary-human');
  assert.equal(get('subaru-traveller').rank.letter,'F');
  assert.ok(!data.sources.some(s=>['SSS','EX'].includes(s.rank.letter)), 'Upper grades require audited feat evidence');
});
