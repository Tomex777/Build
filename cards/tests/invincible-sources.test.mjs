import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const s=JSON.parse(fs.readFileSync(new URL('../catalog/invincible-sources.json',import.meta.url),'utf8'));
test('21 unique official Skybound portraits, bound to the right fighter',()=>{
 assert.equal(s.series,'invincible');assert.equal(s.sources.length,21);
 const ids=new Set(),urls=new Set(),numbers=new Set();
 for(const c of s.sources){
  assert.ok(!ids.has(c.id)&&!urls.has(c.source.image)&&!numbers.has(c.number),'duplicate '+c.id);
  ids.add(c.id);urls.add(c.source.image);numbers.add(c.number);
  assert.equal(c.source.provider,'skybound-invincible-vs');
  assert.ok(c.source.page.startsWith('https://invinciblevs.com/'));
  assert.match(c.source.image,/^https:\/\/invinciblevs\.com\/wp-content\/uploads\/20\d\d\/\d\d\/[A-Za-z0-9_.-]+\.(webp|png)$/);
  assert.equal(c.source.usage,'reference-only; not cleared for sale or public card redistribution');
  assert.ok(['F','C','B','A','S','SS','SSS'].includes(c.rank.letter));
  assert.ok(c.rank.basis&&c.rank.limits&&c.rank.scope);
  assert.ok(!Object.hasOwn(c,'pose'),'Placement must be silhouette-derived to prevent clipped heads');
  assert.ok((c.form.split(' / ')[0]).length<=28,'Displayed edition must fit actual NIGHT typography: '+c.id);
 }
});
test('Iconic upper-tier characters get their own cards; do not fake inaccessible forms',()=>{
 for(const id of ['mark-original','omni-man-classic','battle-beast-armed','conquest-viltrumite','allen-coalition','atom-eve-normal','robot-armor'])
  assert.ok(s.sources.some(c=>c.id===id),'missing '+id);
 assert.ok(s.pending.some(c=>c.id==='thragg-grand-regent'&&c.rank==='SSS'));
 assert.ok(s.pending.some(c=>c.id==='mark-emperor'&&c.rank==='SSS'));
 assert.ok(s.pending.some(c=>c.id==='atom-eve-limit-break'&&c.rank==='SSS'));
 for(const c of s.sources){
  assert.ok(!/emperor|end.?game|sun battle|limit.?break/i.test(c.form),'Incorrect form art '+c.id);
  assert.ok(c.id!=='thragg-grand-regent');
 }
});
test('Continuity and individual combat are not conflated',()=>{
 const byId=id=>s.sources.find(c=>c.id===id);
 assert.equal(byId('ella-elementalist').continuity,'invincible-vs-game');
 assert.equal(byId('cecil-director').rank.letter,'F');
 assert.match(byId('robot-armor').rank.limits,/single suit|later army/i);
 assert.match(byId('battle-beast-armed').rank.limits,/Thragg/i);
 assert.equal(byId('mark-original').rank.letter,'S');
 assert.ok(s.pending.length>=15);
});
