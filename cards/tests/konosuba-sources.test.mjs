import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {inside} from '../src/catalogue.mjs';
import {POWER_SCALE} from '../src/power.mjs';
import {designForRank} from '../src/tiers.mjs';

const source=JSON.parse(await fs.readFile(inside('catalog/konosuba-sources.json'),'utf8'));
const byId=new Map(source.sources.map(x=>[x.id,x]));
test('NIGHT KonoSuba source manifest: unique full-body official anime identity',()=>{
  assert.equal(source.series,'konosuba');
  assert.equal(source.sources.length,18);
  assert.equal(byId.size,source.sources.length);
  assert.equal(new Set(source.sources.map(x=>x.number)).size,source.sources.length);
  assert.equal(new Set(source.sources.map(x=>x.source.image)).size,source.sources.length);
  for(const c of source.sources){
    assert.match(c.source.image,/^https:\/\/konosuba\.com\/(3rd|bakuen)\/character\/img\/[a-z0-9-]+\.png$/);
    assert.equal(c.source.usage,'reference-only');
    assert.match(c.source.rights,/rights reserved/i);
    assert.ok(POWER_SCALE.ranks.includes(c.rank.letter));
    assert.ok(designForRank(c.rank.letter));
    assert.ok(c.rank.basis.length>22&&c.rank.limits.length>22);
    assert.ok(c.rank.references.length&&c.rank.references.every(r=>r.startsWith('https://')));
  }
});
test('Rank applies to each depicted form; no arbitrary divine/borrowed powers',()=>{
  assert.notEqual(byId.get('megumin-student').source.image,byId.get('megumin-explosion').source.image);
  assert.notEqual(byId.get('yunyun-student').source.image,byId.get('yunyun-archwizard').source.image);
  assert.notEqual(byId.get('megumin-student').rank.letter,byId.get('megumin-explosion').rank.letter);
  assert.equal(byId.get('chris-thief').rank.letter,'B');
  assert.equal(byId.get('darkness-crusader').rank.letter,'A');
  assert.equal(byId.get('vanir-masked').rank.letter,'SSS');
  assert.equal(byId.get('aqua-adventurer').rank.letter,'SSS');
  assert.ok(!source.sources.some(c=>c.rank.letter==='EX'), 'Unrestricted divinity not assigned to mortal/ordinary cutout');
  for(const x of source.sources.filter(c=>c.rank.letter==='SSS')){
    assert.equal(x.feat.status,'verified');
    assert.ok(x.feat.claim.length>50);
    assert.ok(x.feat.locator.length>25);
    assert.ok(x.feat.reference.startsWith('https://'));
  }
});
