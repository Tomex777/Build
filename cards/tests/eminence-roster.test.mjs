import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const roster=JSON.parse(await fs.readFile(fileURLToPath(new URL('../catalog/eminence-roster.json',import.meta.url)),'utf8'));
const tiers=['F','E','D','C','B','A','S','SS','SSS','EX'];
test('NIGHT Eminence roster is traceable, unique and unreleased',()=>{
 assert.equal(roster.series,'eminence');
 assert.equal(roster.scale,'night-universe-power-v1');
 assert.equal(roster.cards.length,50);
 const ids=new Set(),nums=new Set(),forms=new Set();
 for(const card of roster.cards){
   assert.match(card.id,/^[a-z][a-z0-9-]+$/);
   assert.ok(!ids.has(card.id));ids.add(card.id);
   assert.ok(Number.isInteger(card.number)&&!nums.has(card.number));nums.add(card.number);
   const form=[card.continuity,card.character,card.formId].join('/');
   assert.ok(!forms.has(form),'duplicate form '+form);forms.add(form);
   assert.ok(card.reference.startsWith('https://'));
   assert.ok(card.basis.length>20);
   assert.ok(card.limits.length>20);
   assert.equal(card.artwork.status,'not-pinned');
   assert.equal(card.publication,'blocked-pending-art-rights-and-visual-review');
   assert.ok(card.candidateRank===null||tiers.includes(card.candidateRank));
   if(['EX','SSS'].includes(card.candidateRank))
     assert.equal(card.editorialStatus,'unverified-upper-tier');
   if(card.continuity==='master-of-garden-apocrypha')
     assert.equal(card.candidateRank,null);
 }
});
test('Multiple Shadow identities are not upgraded transformations',()=>{
 const byId=Object.fromEntries(roster.cards.map(c=>[c.id,c]));
 for(const id of ['shadow-base','shadow-atomic','cid-student','john-smith','mundane-mann','jack-the-ripper']){
   assert.equal(byId[id].candidateRank,'EX');
   assert.match(byId[id].limits,/not distinct power-ups/);
 }
 assert.notEqual(byId['elisabeth-blood-moon'].formId,byId['aurora-claire'].formId);
 assert.equal(roster.cards.filter(c=>c.continuity==='master-of-garden-apocrypha').length,10);
});
