import test from 'node:test';
import assert from 'node:assert/strict';
import {loadCatalogue,reviewFingerprint,findEntries} from '../src/catalogue.mjs';
import {POWER_SCALE,rankValue,validatePower,powerPresentation} from '../src/power.mjs';
import {acquireArtwork} from '../src/artwork.mjs';
import {cardSvg} from '../src/render.mjs';

test('one collection-independent scale preserves human baseline and distinct Ichigo forms',async()=>{
  assert.deepEqual(POWER_SCALE.ranks,['F','E','D','C','B','A','S','SS','SSS']);
  const catalogue=await loadCatalogue();
  assert.equal(catalogue.pendingForms.find(c=>c.id==='ichigo/human').power.rank,'F');
  const rank=id=>catalogue.cards.find(c=>c.id===id).power.rank;
  assert.ok(rankValue(rank('ichigo-substitute-soul-reaper'))<rankValue(rank('ichigo-original-shikai')));
  assert.ok(rankValue(rank('ichigo-original-shikai'))<rankValue(rank('ichigo-original-bankai')));
  assert.ok(rankValue(rank('nel-featured'))<rankValue(rank('nelliel-featured')));
  assert.ok(catalogue.pendingForms.filter(c=>c.form!=='human').every(c=>c.power.rank===null));
  assert.ok(catalogue.cards.every(c=>validatePower(c.power,c)));
});

test('an assessment cannot silently transfer to different artwork, a new form, or an invalid grade',async()=>{
  const c=(await loadCatalogue()).cards.find(c=>c.id==='ichigo-original-shikai');
  assert.throws(()=>validatePower({...c.power,rank:'SSR'},c),/grade/);
  assert.throws(()=>validatePower({...c.power,status:'pending'},c),/grade/);
  assert.throws(()=>validatePower({...c.power,rank:null},c),/grade/);
  assert.throws(()=>validatePower(c.power,{...c,formId:'human'}),/form\/artwork/);
  assert.throws(()=>validatePower(c.power,{...c,source:{...c.source,sha256:'0'.repeat(64)}}),/form\/artwork/);
  assert.throws(()=>validatePower({...c.power,references:['javascript:alert(1)']},c),/reference/);
});

test('power changes invalidate approval and rank filters do not confuse a finish with strength',async()=>{
  const c=(await loadCatalogue()).cards.find(c=>c.id==='ichigo-original-shikai');
  const fp=reviewFingerprint(c,'nocturne','renderer');
  assert.notEqual(reviewFingerprint({...c,power:{...c.power,rank:'S'}},'nocturne','renderer'),fp);
  assert.notEqual(reviewFingerprint({...c,power:{...c.power,scope:'Different depicted form'}},'nocturne','renderer'),fp);
  const entries=['nocturne','ivory','tidelight'].map(design=>({...c,design}));
  assert.equal(findEntries(entries,'Ichigo',{rank:'b'}).length,3);
  assert.equal(findEntries(entries,'Ichigo',{rank:'SSS'}).length,0);
});

test('every finish renders a smaller title with the form grade, while depth remains outside the inner frame',async()=>{
  const catalogue=await loadCatalogue();
  for (const id of ['ichigo-original-shikai','yoruichi-featured','aizen-featured','ichigo-original-bankai','ichigo-substitute-soul-reaper','kenpachi-featured']) {
    const card=catalogue.cards.find(c=>c.id===id),assets=await acquireArtwork(card);
    for (const design of card.designs) {
      const svg=await cardSvg(card,assets,design);
      assert.ok(svg.includes(`data-power-rank="${card.power.rank}"`));
      assert.ok(svg.includes('data-title-scale="0.84"'));
      assert.equal((svg.match(/data-layer="power-rank"/g)||[]).length,1);
      assert.ok(!svg.includes('>ARTIST EDITIONS</text>'));
      assert.ok(svg.includes('feGaussianBlur'));
      if (['nocturne','tidelight'].includes(design)) {
        assert.ok(svg.includes('data-depth="subject"'));
        assert.ok(!/<g data-depth="front"[^>]*>[\s\S]*?opacity="\.9"\/>[\s\S]*?<\/g>/.test(svg));
        // The subject is layered separately from the architecture rather than
        // clipped into an illustration window by the new presentation.
        assert.ok(svg.includes('data-depth="rear"'));
      }
    }
  }
});

test('presentation fails closed instead of silently styling an unknown renderer layout',async()=>{
  const c=(await loadCatalogue()).cards[0];
  assert.throws(()=>powerPresentation('<svg></svg>',c,'obsidian'),/title/);
});
