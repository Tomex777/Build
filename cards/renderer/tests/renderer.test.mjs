import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {renderCard,validateCard} from '../src/index.mjs';
import {cover} from '../src/geometry.mjs';
import {loadAssets,ROOT} from '../src/node-assets.mjs';
const cards=JSON.parse(await fs.readFile(ROOT+'/examples/cards.json','utf8'));
const assets=await loadAssets(cards[0]);
test('Artwork focal points cover the requested box without blank slivers, including both edges',()=>{
 const b={x:20,y:30,w:940,h:1340};for(const p of [[0,0],[.5,.5],[1,1]]){const r=cover(6000,3000,b,p);assert(r.x<=b.x&&r.y<=b.y);assert(r.x+r.w>=b.x+b.w-.01);assert(r.y+r.h>=b.y+b.h-.01)}
});
test('Render is deterministic and contains full provenance without external image/font dependencies',()=>{
 const a=renderCard(cards[0],assets);assert.equal(a,renderCard(cards[0],assets));assert.match(a,/CC BY-SA 4.0/);assert.match(a,/Tyson Tan/);assert(!/href="https?:[^\"]+\.(png|jpg|webp)"/.test(a));assert.match(a,/data:font\/ttf;base64/);
});
test('Display text is escaped, and script URLs, unsafe image types and prefixes are rejected',()=>{
 const card={...cards[0],name:'<script>&"'};const svg=renderCard(card,assets);assert(svg.includes('&lt;script&gt;&amp;&quot;'));assert(!svg.includes('<script>'));
 assert.throws(()=>renderCard(cards[0],{...assets,sourceUrl:'javascript:alert(1)'}),/HTTPS/);
 assert.throws(()=>renderCard(cards[0],{...assets,art:'data:image/svg+xml;base64,AAAA'}),/PNG, JPEG/);
 assert.throws(()=>renderCard(cards[0],assets,{prefix:'bad)prefix'}),/id prefix/);
});
test('Invalid layouts, excessive display text and out-of-bounds focal points fail explicitly',()=>{
 for(const patch of [{theme:'missing'},{layout:'missing'},{name:'x'.repeat(80)},{focal:[-1,1]},{focal:[0,NaN]}])assert.throws(()=>validateCard({...cards[0],...patch}));
});
test('Light and foil states change the exported design; reverse contains source and license links',()=>{
 assert.notEqual(renderCard(cards[0],assets,{light:[0,0]}),renderCard(cards[0],assets,{light:[1,1]}));
 assert.notEqual(renderCard(cards[0],assets,{foil:0}),renderCard(cards[0],assets,{foil:1}));
 const back=renderCard(cards[0],assets,{side:'back'});assert(back.includes(assets.sourceUrl));assert(back.includes(assets.licenseUrl));assert(!back.includes('THE CYBER SQUIRREL'));
});

// Material and composition remain separate when a caller reuses another palette.
test('Composition selection is explicit and rejects incompatible artwork layouts',()=>{
 assert.throws(()=>validateCard({...cards[0],composition:'pearl'}),/require full art/);
 assert.throws(()=>validateCard({...cards[1],composition:'unknown'}),/Unknown composition/);
 assert.equal(validateCard({...cards[1],theme:'nocturne',composition:'pearl'}).composition,'pearl');
});
