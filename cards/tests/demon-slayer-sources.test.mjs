import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {inside} from '../src/catalogue.mjs';
import {designForRank} from '../src/tiers.mjs';
import {POWER_SCALE} from '../src/power.mjs';
const data=JSON.parse(await fs.readFile(inside('catalog/demon-slayer-sources.json'),'utf8'));
test('Mitsuri uses an official original and the shared NIGHT renderer',()=>{
  assert.equal(data.series,'demon-slayer');
  assert.equal(data.sources.length,1);
  const item=data.sources[0];
  assert.equal(item.id,'mitsuri-love-hashira');
  assert.equal(item.name,'Mitsuri Kanroji');
  assert.equal(item.formId,'love-hashira');
  assert.equal(item.source.image,'https://game.kimetsu.com/hinokami2/assets/img/character/main/chara32.png');
  assert.equal(item.source.usage,'reference-only');
  assert.match(item.source.rights,/reserved/i);
  assert.equal(item.rank.letter,'A');
  assert.ok(POWER_SCALE.ranks.includes(item.rank.letter));
  assert.equal(designForRank(item.rank.letter),'nocturne');
  assert.ok(item.rank.basis.includes('Zohakuten'));
  assert.ok(item.rank.references.every(x=>x.startsWith('https://')));
  assert.equal(data.sources.some(s=>s.source.provider==='ai-generated'),false);
});
test('Marked Mitsuri cannot silently inherit unmarked art',()=>{
  assert.ok(data.pendingForms.some(f=>f.id==='mitsuri-demon-slayer-mark'));
  assert.equal(data.sources.some(s=>s.formId==='love-hashira-marked'),false);
  assert.ok(!data.sources.some(s=>['SSS','EX'].includes(s.rank.letter)));
});
