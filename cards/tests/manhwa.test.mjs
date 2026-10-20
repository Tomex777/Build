import test from 'node:test';
import assert from 'node:assert/strict';
import {loadManhwaRoster,validateManhwaRoster,validateArtLock,collectionSummary,
  toRendererCard,sha256} from '../src/manhwa.mjs';

const roster=await loadManhwaRoster();
const lock={version:1,collection:roster.id,editions:{}};
const clone=data=>structuredClone(data);

test('23 manhwa editions cover the three verified webcomic universes',()=>{
  assert.equal(roster.editions.length,23);
  assert.deepEqual(roster.series.map(s=>s.id),['sss-revival','infinite-mage','study-group']);
  assert.equal(new Set(roster.editions.map(x=>x.id)).size,23);
  assert.equal(roster.editions.filter(x=>x.family==='romance').length,2);
  assert.equal(roster.editions.filter(x=>x.series==='study-group'&&x.family==='power').length,0);
  assert.ok(validateManhwaRoster(roster));
});
test('romance is a true two-character edition, not a ranking masquerading as a relationship',()=>{
  for(const c of roster.editions.filter(c=>c.family==='romance')){
    assert.equal(c.subjects.length,2);
    assert.ok(!('power' in c));
    assert.equal(c.bond.grade,null);
    assert.equal(c.artwork.subjectSlots.length,2);
  }
  const tampered=clone(roster);
  tampered.editions.find(c=>c.family==='romance').subjects=['Kim Gong-ja'];
  assert.throws(()=>validateManhwaRoster(tampered),/Romance|subjectSlots/);
});
test('combat grades are unset until form-specific in-verse feats are reviewed',()=>{
  for(const c of roster.editions.filter(c=>c.family==='power')){
    assert.equal(c.power.status,'pending-feat-review');
    assert.equal(c.power.tier,null);
    assert.equal(c.power.scope,'in-universe-only');
  }
  const tampered=clone(roster);
  tampered.editions.find(c=>c.family==='power').power.tier='EX';
  assert.throws(()=>validateManhwaRoster(tampered),/fabricated/);
});
test('grounded Study Group cannot get fantasy battle ranking',()=>{
  const tampered=clone(roster), g=tampered.editions.find(c=>c.series==='study-group');
  g.family='power';
  g.power={status:'pending-feat-review',tier:null,scope:'in-universe-only',continuity:'webcomic',reference:g.story.source};
  assert.throws(()=>validateManhwaRoster(tampered),/power ladder/);
});
test('artwork never silently appears before distinct cutouts and source rights are verified',()=>{
  assert.ok(validateArtLock(roster,lock));
  assert.ok(collectionSummary(roster,lock).every(s=>s.artPinned===0));
  const bogus=clone(lock); bogus.editions['gong-ja-raviel']={};
  assert.throws(()=>validateArtLock(roster,bogus),/Two-subject romance/);
  const wrong=clone(lock); wrong.editions['gong-ja-regressor']={
    subject:'Raviel Ivansia',form:'Returner / Tower',page:'https://tapas.io',
    image:'https://tapas.io/test.png',sha256:'a'.repeat(64),width:2000,height:3200,
    credit:'Source',rights:'Rights reserved',usage:'reference-only',
    status:'source-pinned-candidate',transparencyVerified:true,
    localPath:'artwork/manhwa/gong-ja.png',crop:[0,0,2000,3200],focal:[.5,.5]};
  assert.throws(()=>validateArtLock(roster,wrong),/Invalid pinned art identity/);
});
test('renderer candidates cannot be created from unpinned or substituted art',()=>{
  const solo=roster.editions.find(c=>c.id==='gong-ja-regressor');
  assert.throws(()=>toRendererCard(solo,null,1),/missing or wrong-form/);
  assert.throws(()=>toRendererCard(solo,{subject:'Raviel Ivansia',form:solo.form,sha256:'a'.repeat(64)},1),/missing or wrong-form/);
  assert.equal(sha256('night').length,64);
});
