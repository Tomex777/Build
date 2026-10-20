import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const data=JSON.parse(fs.readFileSync(new URL('../catalog/maou-gakuin-sources.json',import.meta.url),'utf8'));
test('Official anime source bound identities',()=>{
  assert.equal(data.series,'maou-gakuin');
  assert.equal(data.sources.length,13);
  const ids=new Set(),images=new Set(),numbers=new Set();
  for(const card of data.sources){
    assert.ok(!ids.has(card.id)&&!images.has(card.source.image)&&!numbers.has(card.number));
    ids.add(card.id);images.add(card.source.image);numbers.add(card.number);
    assert.equal(card.source.provider,'maohgakuin-anime-official');
    assert.match(card.source.image,/^https:\/\/maohgakuin\.com\/assets\/img\/character\/img_[a-z]+\.png$/);
    assert.ok(card.source.rights.includes('All rights reserved'));
    assert.ok(['C','B','A','S','SS'].includes(card.rank.letter));
    assert.match(card.rank.limits,/./);
    assert.match(card.rank.basis,/./);
  }
});
test('Anime academy poses not falsely assigned to Silverwater endgame forms',()=>{
  for(const c of data.sources) assert.ok(!/Abyss|late novel|Noah|Amur|Zinnia|Aisha|Avos Dilhevia's transformed/i.test(c.form));
});
