import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {inside} from '../src/catalogue.mjs';
import {POWER_SCALE} from '../src/power.mjs';
import {designForRank} from '../src/tiers.mjs';

const roster=JSON.parse(await fs.readFile(inside('catalog/bungo-stray-dogs-roster.json'),'utf8'));
const byId=new Map(roster.roster.map(character=>[character.id,character]));

test('Bungo Stray Dogs is another NIGHT universe, not a distinct renderer',()=>{
  assert.equal(roster.series,'bungo-stray-dogs');
  assert.equal(roster.tierScale,'night-universe-power-v1');
  assert.equal(roster.artRules.characterSilhouetteBreaksInnerFrame,true);
  assert.equal(roster.artRules.uniqueSourcePerForm,true);
  assert.equal(roster.status,'source-research');
  assert.equal(roster.roster.length,34);
  assert.equal(byId.size,roster.roster.length);
});

test('Series and character identities are never silently swapped',()=>{
  const ids=new Set(),numbers=new Set();
  for(const character of roster.roster){
    assert.match(character.id,/^[a-z][a-z0-9-]+$/);
    assert.ok(!ids.has(character.id));ids.add(character.id);
    assert.ok(!numbers.has(character.number));numbers.add(character.number);
    assert.ok(character.name&&character.japaneseName&&character.ability);
    assert.ok(character.basis.length>=22&&character.limits.length>=22);
    assert.ok(POWER_SCALE.ranks.includes(character.proposedRank));
    assert.ok(designForRank(character.proposedRank));
    assert.equal(character.artwork,null,'Unsourced cards may not masquerade as finished artwork');
    assert.equal(character.sourceStatus,'unverified');
  }
  assert.equal(byId.get('ranpo-edogawa').proposedRank,'F','Non-supernatural deduction is not destructive power');
  assert.equal(byId.get('osamu-dazai').ability,'No Longer Human');
  assert.equal(byId.get('chuya-nakahara').proposedRank,'A','Corruption is separate');
  assert.equal(byId.get('ochi-fukuchi').proposedRank,'S');
  assert.ok(!roster.roster.some(c=>['SSS','EX'].includes(c.proposedRank)),
    'SSS and EX require an actual feat audit and form-specific review');
});

test('Major later transformations are separate pending image identities',()=>{
  const pendingIds=new Set(roster.pendingForms.map(c=>c.id));
  assert.equal(pendingIds.size,roster.pendingForms.length);
  for(const required of ['atsushi-tiger','chuya-corruption','dazai-dark-era','akutagawa-vampire']){
    assert.ok(pendingIds.has(required));
  }
  for(const candidate of roster.pendingForms){
    assert.ok(candidate.formId&&candidate.status.startsWith('pending'));
    assert.ok(roster.roster.some(c=>c.id===candidate.character));
  }
});
