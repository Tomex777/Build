import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const data=JSON.parse(fs.readFileSync(new URL('../catalog/maou-gakuin-sources.json',import.meta.url),'utf8'));
test('Official anime source bound identities',()=>{
  assert.equal(data.series,'maou-gakuin');
  assert.equal(data.sources.length,25);
  const ids=new Set(),images=new Set(),numbers=new Set();
  for(const card of data.sources){
    assert.ok(!ids.has(card.id)&&!images.has(card.source.image)&&!numbers.has(card.number));
    ids.add(card.id);images.add(card.source.image);numbers.add(card.number);
    assert.ok(['maohgakuin-anime-official','maou-gakuin-2020-official'].includes(card.source.provider));
    assert.ok(card.source.image.startsWith('https://maohgakuin.com/'));
    assert.match(card.source.image,/\/img_[a-z0-9]+\.png$/);
    if(card.source.provider==='maou-gakuin-2020-official') assert.ok(card.source.image.includes('/1st/assets/img/chara/'));
    assert.ok(card.source.rights.includes('All rights reserved'));
    assert.ok(['F','C','B','A','S','SS'].includes(card.rank.letter));
    assert.match(card.rank.limits,/./);
    assert.match(card.rank.basis,/./);
  }
});
test('Anime academy poses not falsely assigned to Silverwater endgame forms',()=>{
  for(const c of data.sources) assert.ok(!/Abyss|late novel|Noah|Amur|Zinnia|Aisha|Avos Dilhevia's transformed/i.test(c.form));
});

test('Do not reassign founding-ancestor feats to noncombatant family',()=>{
  for (const c of data.sources.filter(c=>['gusta-s1','izabella-s1'].includes(c.id))) {
    assert.equal(c.rank.letter,'F');
    assert.equal(c.continuity,'maou-gakuin-anime-s1');
  }
});
test('Named official cast extensions are present and edition-specific',()=>{
  for(const id of ['nousgallia-god','ahide-oracle','anoshu-child','gusta-s1','izabella-s1'])
    assert.ok(data.sources.some(c=>c.id===id),'Missing '+id);
  for(const c of data.sources) assert.ok(!c.form.toLowerCase().includes('silverwater sea'));
});
