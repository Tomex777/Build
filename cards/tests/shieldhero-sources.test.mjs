import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {inside} from '../src/catalogue.mjs';
import {designForRank} from '../src/tiers.mjs';
import {POWER_SCALE} from '../src/power.mjs';

const data=JSON.parse(await fs.readFile(inside('catalog/shieldhero-sources.json'),'utf8'));
test('Shield Hero candidate sources are distinct, official, and in-universe only',()=>{
  assert.equal(data.series,'shieldhero');
  assert.equal(data.sources.length,16);
  const ids=new Set(),numbers=new Set(),urls=new Set();
  for(const entry of data.sources){
    assert.ok(!ids.has(entry.id),'duplicate card ID');ids.add(entry.id);
    assert.ok(!numbers.has(entry.number),'duplicate card number');numbers.add(entry.number);
    assert.ok(!urls.has(entry.source.image),'duplicated character illustration');urls.add(entry.source.image);
    assert.match(entry.source.image,/^https:\/\/shieldhero-anime\.jp\/assets\/img\/(?:3rd|4th)\/char\/detail\/[a-z0-9-]+\.png$/);
    assert.ok(entry.source.page.startsWith('https://shieldhero-anime.jp/en/'));
    assert.equal(entry.continuity,'shieldhero-anime');
    assert.equal(entry.source.usage,'reference-only');
    assert.match(entry.source.rights,/rights reserved/i);
    assert.ok(POWER_SCALE.ranks.includes(entry.rank.letter));
    assert.ok(designForRank(entry.rank.letter));
    assert.ok(entry.rank.basis.length>=32);
    assert.ok(entry.rank.limits.length>=32);
    assert.ok(!['SSS','EX'].includes(entry.rank.letter),'upper ranks need independent verified feat reviews');
  }
});
test('Light-novel-only forms never borrow normal anime figure artwork',()=>{
  const records=data.sources;
  assert.ok(records.some(r=>r.id==='raphtalia-katana-hero'&&r.formId==='katana-hero'));
  assert.ok(records.some(r=>r.id==='motoyasu-empowered'));
  assert.ok(!records.some(r=>/god|divine|zero-series|vassal-gauntlet|blood-sacrifice|wrath-iii/.test(r.id)));
  const sadeena=records.find(r=>r.id==='sadeena-orca-warrior');
  assert.notEqual(sadeena.formId,'vassal-harpoon-hero');
  assert.ok(!records.some(r=>r.id.includes('fitoria')),'no unacquired portrait passed off as Fitoria');
});
