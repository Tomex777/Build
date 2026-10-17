import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {loadCatalogue,inside,digest,reviewFingerprint,findEntries} from '../src/catalogue.mjs';
import {openCardLibrary} from '../src/bot.mjs';
import {publish,approve} from '../src/pipeline.mjs';
import {discover} from '../src/discovery.mjs';

test('catalogue keeps exact Ichigo forms and does not substitute unacquired forms',async()=>{
  const c=await loadCatalogue();const ichigo=c.cards.filter(c=>c.character==='ichigo');
  assert.equal(ichigo.length,3);assert.equal(findEntries(ichigo,'Ichigo',{form:'original-bankai'}).length,1);
  assert.equal(findEntries(ichigo,'Ichigo',{form:'blood-chains'}).length,0);
  assert.ok(JSON.stringify(c.pendingForms).includes('blood'));
  assert.ok(c.cards.length>=70);assert.equal(new Set(c.cards.map(c=>c.number)).size,c.cards.length);
});
test('review fingerprints bind identity, artwork, crop, finish and renderer',async()=>{
  const c=(await loadCatalogue()).cards[0], base=reviewFingerprint(c,'nocturne','v1');
  for(const changed of [{...c,name:'Different Name'},{...c,number:999},{...c,form:'Different Form'},{...c,recipe:{...c.recipe,crop:[0,0,100,100]}},{...c,source:{...c.source,sha256:'0'.repeat(64)}}])assert.notEqual(reviewFingerprint(changed,'nocturne','v1'),base);
  assert.notEqual(reviewFingerprint(c,'ivory','v1'),base);assert.notEqual(reviewFingerprint(c,'nocturne','v2'),base);assert.throws(()=>inside('../outside'));
});
test('bot reads only approved files, disambiguates finishes and detects tampering',async()=>{
  const folder=await fs.mkdtemp(path.join(os.tmpdir(),'night-cards-'));
  try{
    const bytes=Buffer.from('fixture image'),card={id:'ichigo-shikai--ivory',character:'ichigo',name:'Ichigo Kurosaki',form:'Original Shikai',formId:'original-shikai',design:'ivory',aliases:['Ichigo','Kurosaki Ichigo'],approved:true,review:{status:'approved'},png:'one.png',pngSHA256:digest(bytes)};
    await fs.writeFile(path.join(folder,'one.png'),bytes);
    await fs.writeFile(path.join(folder,'manifest.json'),JSON.stringify({version:1,cards:[card,{...card,id:'ichigo-shikai--nocturne',design:'nocturne'},{...card,id:'unapproved',approved:false}]}));
    const l=await openCardLibrary(folder);assert.equal(l.find('Ichigo').length,2);
    await assert.rejects(()=>l.image('Ichigo'),e=>e.code==='CARD_AMBIGUOUS'&&e.choices.length===2);
    assert.deepEqual((await l.image('Ichigo',{design:'ivory'})).buffer,bytes);
    await assert.rejects(()=>l.image('Ichigo',{form:'blood-chains'}),e=>e.code==='CARD_NOT_FOUND');
    await fs.writeFile(path.join(folder,'one.png'),'changed');await assert.rejects(()=>l.image(card.id),/integrity/);
  }finally{await fs.rm(folder,{recursive:true,force:true});}
});
test('publication rejects failed builds and approvals reject reduced-size studies',async()=>{
  const out='.cache/test-quarantine';await fs.mkdir(inside(out),{recursive:true});
  try{
    await fs.writeFile(inside(out+'/build.json'),JSON.stringify({width:3000,cards:[],failures:[{error:'bad crop'}]}));
    await assert.rejects(()=>publish({out}),/Quarantined/);
    await fs.writeFile(inside(out+'/build.json'),JSON.stringify({width:1000,cards:[],failures:[]}));
    await assert.rejects(()=>approve({out,ids:['one']}),/3000/);
  }finally{await fs.rm(inside(out),{recursive:true,force:true});}
});
test('discovery limits official candidates without inventing a license',async()=>{
  const html='<a href="/en/character/aizen.html"><img alt="Bleach Sosuke Aizen" src="x"></a><a href="https://foreign.test/character/fake.html"><img alt="Fake"></a>';
  const r=await discover({fetchImpl:async()=>new Response(html)});
  assert.equal(r.candidates.length,1);assert.equal(r.candidates[0].slug,'aizen');assert.equal(r.candidates[0].usage,'reference-only');
  await assert.rejects(()=>discover({fetchImpl:async()=>new Response('<html>changed</html>')}),/structure changed/);
});
