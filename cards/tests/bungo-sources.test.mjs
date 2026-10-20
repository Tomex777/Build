import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {inside} from '../src/catalogue.mjs';

const roster=JSON.parse(await fs.readFile(inside('catalog/bungo-stray-dogs-roster.json'),'utf8'));
const candidate=JSON.parse(await fs.readFile(inside('catalog/bungo-stray-dogs-sources.json'),'utf8'));
const characters=new Map(roster.roster.map(c=>[c.id,c]));
const hosts=new Set(['bungo-stray-dogs.jp','bungosd.com','www.bungo-stray-dogs.jp','www.bungosd.com']);

test('Bungo art sources use independently identified named official character profiles',()=>{
  assert.equal(candidate.series,roster.series);
  assert.ok(candidate.sources.length>0,'Do not claim discovered sources without evidence');
  const ids=new Set(),images=new Set();
  for(const entry of candidate.sources){
    const person=characters.get(entry.id);
    assert.ok(person,'Artwork has no exact canonical roster identity: '+entry.id);
    assert.equal(entry.verifiedCharacter,person.name,'Artwork must match a single named character');
    assert.equal(entry.verifiedForm,person.formId,'Artwork must show the actual labelled form');
    assert.ok(entry.reviewedBy?.length>15,'Artwork requires human identity review');
    for(const field of ['page','image']){
      const u=new URL(entry[field]);
      assert.equal(u.protocol,'https:');
      assert.ok(hosts.has(u.hostname),'Not an official anime source host');
    }
    assert.match(new URL(entry.image).pathname,/\/images\/character\/[^/]+\/body-[^/]+\.(png|webp|jpg|jpeg)$/i);
    assert.ok(!ids.has(entry.id),'Repeated character edition '+entry.id);
    assert.ok(!images.has(entry.image),'Do not reuse images across character identities');
    ids.add(entry.id);
    images.add(entry.image);
  }
});

test('Unacquired transformations never borrow main-character artwork',()=>{
  for(const pending of roster.pendingForms){
    assert.ok(!candidate.sources.some(s=>s.id===pending.id),'New form requires its own explicit source record');
    assert.ok(characters.has(pending.character));
  }
});
