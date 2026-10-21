import test from 'node:test';
import assert from 'node:assert/strict';
import {loadManhwaRoster,validateManhwaRoster,validateArtLock,collectionSummary,
  toRendererCard,sha256} from '../src/manhwa.mjs';
import {researchReviewHtml} from '../src/manhwa-review.mjs';

const roster=await loadManhwaRoster();
const lock={version:1,collection:roster.id,editions:{}};
const clone=data=>structuredClone(data);

test('82 edition candidates cover three distinct manhwa universes without fake publication status',()=>{
  assert.equal(roster.editions.length,82);
  assert.deepEqual(roster.series.map(s=>s.id),['sss-revival','infinite-mage','study-group']);
  assert.equal(new Set(roster.editions.map(x=>x.id)).size,82);
  assert.deepEqual(roster.series.map(s=>roster.editions.filter(e=>e.series===s.id).length),[24,34,24]);
  assert.equal(roster.editions.filter(e=>e.family==='power').length,21);
  assert.equal(roster.editions.filter(e=>e.family==='character').length,59);
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

test('all 82 candidates are linked to publisher title and character research, not invented webcomic verification',()=>{
  for(const e of roster.editions){
    assert.equal(e.research.verification,'candidate-not-confirmed');
    assert.equal(e.research.webcomicAppearance,'requires-chapter-check');
    assert.equal(e.research.evidence.length,2);
    assert.ok(e.research.evidence[0].startsWith('https://'));
    assert.ok(e.research.evidence[1].startsWith('https://'));
    assert.equal(e.artwork.provenance,null);
  }
  const tampered=clone(roster);
  tampered.editions[0].research.verification='chapter-verified';
  assert.throws(()=>validateManhwaRoster(tampered),/episode proof/);
  const fake=clone(roster);
  fake.editions[0].research.episodeEvidence={number:65,url:'https://example.org/episode',reviewer:'none',reviewDate:'2026-10-21'};
  assert.throws(()=>validateManhwaRoster(fake),/cannot claim adaptation evidence/);
});

test('romanized aliases retain their identity in the renderer without creating another card',()=>{
  const gamin=roster.editions.find(e=>e.id==='gamin-yoon');
  const alias=gamin.aliases.find(name=>name==='Yoon Ga-min');
  assert.ok(alias);
  const art={subject:gamin.subjects[0],form:gamin.form,sha256:'f'.repeat(64),
    page:'https://www.webtoons.com',image:'https://www.webtoons.com/image',
    width:1000,height:1500,rights:'All rights reserved',usage:'reference-only',
    credit:'Official art',localPath:'artwork/manhwa/study-group/gamin.png',crop:[0,0,1000,1500],focal:[.5,.5]};
  const sample=toRendererCard(gamin,art,1);
  assert.ok(sample.aliases.includes('Yoon Ga-min'));
  assert.equal(sample.name,'Gamin Yoon');
  assert.equal(sample.source.sha256,art.sha256);
  assert.equal(sample.designs.length,1);
});

test('SSS Tower roles and Infinite Mage magic titles never grant invented upper tier letters',()=>{
  assert.ok(roster.editions.some(e=>e.id==='marcus-sword-saint'));
  assert.ok(roster.editions.some(e=>e.id==='victor-arcane'));
  assert.ok(roster.editions.some(e=>e.id==='fall-rain-demon-king'));
  assert.ok(roster.editions.some(e=>e.id==='sylvia-chamberlain'));
  const study=roster.editions.filter(e=>e.series==='study-group');
  assert.equal(study.length,24);
  assert.ok(study.every(e=>e.family==='character'));
  assert.ok(study.every(e=>!('power' in e)));
});

test('offline review browser is a spoiler-aware research index, never counterfeit art cards',()=>{
  const html=researchReviewHtml(roster,lock);
  assert.match(html,/<title>NIGHT — Manhwa Candidate Roster<\/title>/);
  assert.ok(html.includes('82</b>EDITION CANDIDATES'));
  assert.ok(html.includes('34')===false || html.includes('The Infinite Mage'));
  assert.ok(html.includes('data-spoiler="true"'));
  assert.ok(html.includes('id="spoilers"'));
  assert.ok(html.includes('id="series"'));
  assert.ok(html.includes('id="family"'));
  assert.ok(html.includes('CHAPTER CHECK'));
  assert.ok(!html.includes('<img'));
  assert.ok(!html.includes('SOURCE APPROVED'));
  assert.ok(!html.includes('EX RANK'));
});
