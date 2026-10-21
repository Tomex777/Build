import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const load=p=>JSON.parse(fs.readFileSync(new URL('../catalog/'+p,import.meta.url),'utf8'));
const roster=load('dmc-netflix-roster.json'), sources=load('dmc-netflix-sources.json');
test('Netflix-only series and 36 distinct source-sensitive editions',()=>{
 assert.equal(roster.series,'dmc-netflix');
 assert.equal(roster.editions.length,36);
 assert.equal(new Set(roster.editions.map(e=>e.id)).size,36);
 assert.equal(new Set(roster.editions.map(e=>e.number)).size,36);
 for(const e of roster.editions){
   assert.match(e.continuity,/^dmc-netflix-/);
   assert.match(e.basis,/./);assert.match(e.limits,/./);
   assert.ok(['F','E','D','C','B','A','S','SS','SSS','EX'].includes(e.rank));
   assert.ok(e.references.every(u=>u.startsWith('https://')));
   assert.ok(['artwork-pending','source-candidate'].includes(e.status));
 }
});
test('Mundus > returned Argosax; original Sparda is historical and Vergil duel unresolved',()=>{
 const lookup=Object.fromEntries(roster.editions.map(c=>[c.id,c]));
 assert.equal(lookup['mundus-king'].rank,'EX');
 assert.equal(lookup['argosax-returned'].rank,'SSS');
 assert.match(lookup['sparda-dark-knight'].form,/historical/i);
 assert.match(lookup['mundus-king'].limits,/NOT a Mundus defeat/);
 assert.notEqual(lookup['dante-hunter'].rank,lookup['dante-devil-trigger'].rank);
 assert.notEqual(lookup['lady-darkcom'].rank,lookup['lady-specialist'].rank);
 assert.notEqual(lookup['arius-sorcerer'].rank,lookup['arius-argosax-vessel'].rank);
});
test('Artwork acquisitions are only exact Netflix animated subjects',()=>{
 assert.equal(sources.series,'dmc-netflix');assert.equal(sources.assets.length,2);
 const seen=new Set();
 for(const s of sources.assets){
   assert.ok(roster.editions.some(c=>c.id===s.id&&c.form===s.form&&c.rank===s.rank));
   assert.ok(!seen.has(s.url));seen.add(s.url);
   assert.match(s.url,/^https:\/\/static\.wikia\.nocookie\.net\//);
   assert.match(s.page,/^https:\/\//);
 }
 assert.match(sources.rights,/copyrights/);
});
test('No game, 2007 anime or unfilmed DMC 5 forms falsely added',()=>{
 assert.ok(!roster.editions.some(c=>/Sin Devil Trigger|Majin|Nero Red Queen|V \(familiar\)|DmC reboot|2007 anime|DMC5|Sin DT/i.test(c.form+' '+c.basis)));
});
