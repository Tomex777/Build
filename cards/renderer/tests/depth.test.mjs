import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {ROOT} from '../src/node-assets.mjs';
import {characterCards} from '../src/characters.mjs';
import {loadCharacterAssets} from '../src/character-assets.mjs';
import {renderDepthCard,depthPose} from '../src/depth.mjs';
const profile=JSON.parse(await fs.readFile(ROOT+'/examples/characters/naruto.json','utf8'));
const cards=characterCards(JSON.parse(await fs.readFile(ROOT+'/examples/cards.json','utf8')),profile);
const assets=await loadCharacterAssets(profile);

test('depth renderer is deterministic, self-contained, and has three independently movable planes',()=>{
 for(const c of cards.slice(0,2)){
  const svg=renderDepthCard(c,assets);
  assert.equal(svg,renderDepthCard(c,assets));
  for(const layer of ['rear','subject','front'])assert.ok(svg.includes(`data-depth="${layer}"`));
  assert.ok(svg.includes('feDropShadow'));assert.ok(svg.includes('mask-type="alpha"'));
  assert.ok(svg.includes('Artwork rights reserved'));
  assert.ok(!/<image[^>]*href="https:/.test(svg));
  assert.notEqual(svg,renderDepthCard(c,assets,{light:[.9,.8]}));
  assert.notEqual(svg,renderDepthCard(c,assets,{foil:0}));
 }
 assert.deepEqual(depthPose([.28,.2]),{rear:[-0,-0],subject:[0,0],front:[0,0]});
});
test('depth export rejects unsafe assets and escapes character identity',()=>{
 assert.throws(()=>renderDepthCard(cards[0],{...assets,fonts:{display:"bad'font",sans:assets.fonts.sans}}),/Fonts/);
 assert.throws(()=>renderDepthCard({...cards[0],depthSubject:{x:0,y:0,width:-1,height:100}},assets),/placement/);
 assert.throws(()=>renderDepthCard(cards[0],assets,{light:[NaN,0]}),/light/);
 const svg=renderDepthCard({...cards[0],name:'<script>evil</script>'},assets);
 assert.ok(!svg.includes('<script>'));
});
