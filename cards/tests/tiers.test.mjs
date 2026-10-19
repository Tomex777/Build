import test from 'node:test';
import assert from 'node:assert/strict';
import {loadCatalogue} from '../src/catalogue.mjs';
import {TIER_STYLES,designForRank,validateTierStyles} from '../src/tiers.mjs';
import {validateFormGrades} from '../src/power.mjs';

test('the proposed tier design follows form rank and includes distinct SSS and EX designs',async()=>{
  const c=await loadCatalogue();
  assert.equal(c.tierPolicy.status,'proposed');
  for(const card of c.cards)assert.deepEqual(card.designs,[designForRank(card.power.rank)]);
  assert.equal(TIER_STYLES.SSS,'eclipse');assert.equal(TIER_STYLES.EX,'ascendant');assert.equal(new Set(Object.values(TIER_STYLES)).size,10);
  const changed=structuredClone(c),b=changed.cards.find(c=>c.power.rank==='B');b.designs=['obsidian'];
  assert.throws(()=>validateTierStyles(changed),/does not match form tier/);
});

test('identical form names are assessed independently across universes and continuities',async()=>{
  const c=(await loadCatalogue()).cards[0],a={...c,power:{...c.power,rank:'A'}};
  assert.doesNotThrow(()=>validateFormGrades([c,{...a,power:{...a.power,universe:'naruto'}}]));
  assert.doesNotThrow(()=>validateFormGrades([c,{...a,power:{...a.power,continuity:'other-continuity'}}]));
  assert.throws(()=>validateFormGrades([c,a]),/Conflicting grade/);
});
