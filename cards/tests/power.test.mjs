import test from 'node:test';
import assert from 'node:assert/strict';
import {loadCatalogue,reviewFingerprint,findEntries} from '../src/catalogue.mjs';
import {POWER_SCALE,rankValue,validatePower,powerPresentation,validateFormGrades} from '../src/power.mjs';
import {acquireArtwork} from '../src/artwork.mjs';
import {cardSvg} from '../src/render.mjs';

test('one in-universe scale preserves human baseline and distinct Ichigo forms',async()=>{
  assert.deepEqual(POWER_SCALE.ranks,['F','E','D','C','B','A','S','SS','SSS','EX']);
  assert.equal(POWER_SCALE.scope,'in-universe');
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
  assert.throws(()=>validatePower({...c.power,universe:'naruto'},c),/another universe/);
  assert.throws(()=>validatePower({...c.power,continuity:''},c),/continuity/);
  for(const rank of ['SSS','EX'])assert.throws(()=>validatePower({...c.power,rank},c),/verified feat evidence/);
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
  for (const id of ['ichigo-original-shikai','yoruichi-featured','aizen-featured','ichigo-original-bankai','ichigo-substitute-soul-reaper','kenpachi-featured','aizen-muken','byakuya-senbonzakura','kenpachi-tybw','rukia-tybw','yoruichi-flash-step','ichigo-restored-shikai','ichigo-restored-shikai-standing','isane-featured','riruka-featured','ichibe-hyosube-tybw','kon-featured']) {
    const card=catalogue.cards.find(c=>c.id===id),assets=await acquireArtwork(card);
    for (const design of card.designs) {
      const svg=await cardSvg(card,assets,design);
      assert.ok(svg.includes(`data-power-rank="${card.power.rank}"`));
      assert.ok(svg.includes('data-title-scale="0.84"'));
      assert.equal((svg.match(/data-layer="power-rank"/g)||[]).length,1);
      assert.ok(!svg.includes('>ARTIST EDITIONS</text>'));
      assert.ok(svg.includes('feDisplacementMap'));
      assert.ok(svg.includes('data-power-material="glass-letters"'));
      assert.ok(svg.includes('data-rank-ink="true"'));
      const rankLayer=svg.slice(svg.indexOf('<g data-layer="power-rank"'));
      assert.ok(!rankLayer.includes('<rect'), 'The rank must have no visible plate or pill');
      if (['nocturne','tidelight'].includes(design)) {
        assert.ok(svg.includes('data-depth="subject"'));
        const foreground=svg.match(/<g data-depth="front"[^>]*>([\s\S]*?)<\/g>/)?.[1]??'';
        assert.ok(!foreground.includes('opacity=".9"'));
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

test('distinct artwork poses of the same form retain one grade and source reviews cannot transfer',async()=>{
  const cards=(await loadCatalogue()).cards.filter(c=>c.formId==='restored-shikai');
  assert.equal(cards.length,2);assert.notEqual(cards[0].source.sha256,cards[1].source.sha256);assert.equal(cards[0].power.rank,cards[1].power.rank);
  assert.throws(()=>validateFormGrades([cards[0],{...cards[1],power:{...cards[1].power,rank:'SSS'}}]),/Conflicting grade/);
  assert.throws(()=>validatePower({...cards[0].power,review:{...cards[0].power.review,artworkSHA256:'0'.repeat(64)}},cards[0]),/Stale source/);
});
