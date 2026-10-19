import test from 'node:test';
import assert from 'node:assert/strict';
import {loadCatalogue} from '../src/catalogue.mjs';
import {TIER_STYLES,designForRank,validateTierStyles} from '../src/tiers.mjs';
import {validateFormGrades} from '../src/power.mjs';

test('the proposed tier design follows form rank and leaves SSS and EX open',async()=>{
  const c=await loadCatalogue();
  assert.equal(c.tierPolicy.status,'proposed');
  for(const card of c.cards)assert.deepEqual(card.designs,[designForRank(card.power.rank)]);
  for(const r of ['SSS','EX']){assert.equal(TIER_STYLES[r],null);assert.throws(()=>designForRank(r),/reviewed design/);}
  const changed=structuredClone(c),b=changed.cards.find(c=>c.power.rank==='B');b.designs=['obsidian'];
  assert.throws(()=>validateTierStyles(changed),/does not match form tier/);
});

test('identical form names are assessed independently across universes and continuities',async()=>{
  const c=(await loadCatalogue()).cards[0],a={...c,power:{...c.power,rank:'A'}};
  assert.doesNotThrow(()=>validateFormGrades([c,{...a,power:{...a.power,universe:'naruto'}}]));
  assert.doesNotThrow(()=>validateFormGrades([c,{...a,power:{...a.power,continuity:'other-continuity'}}]));
  assert.throws(()=>validateFormGrades([c,a]),/Conflicting grade/);
});
