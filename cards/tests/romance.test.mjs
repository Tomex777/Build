import test from 'node:test';
import assert from 'node:assert/strict';
import {renderRomanceCard} from '../src/romance.mjs';
const art='data:image/png;base64,iVBORw0KGgo=',font='data:font/ttf;base64,AA==';
const card={id:'tomo-jun',name:'Tomo & Jun',series:'Tomo-chan Is a Girl!',bond:'CHILDHOOD FRIENDS',number:'R-001',design:'tidelight',subjects:[{id:'jun',x:510,y:130,width:390},{id:'tomo',x:110,y:147,width:400}]};
const assets={fonts:{display:font,sans:font},subjects:['jun','tomo'].map(id=>({id,art,width:480,height:880,alphaBBox:[70,2,402,877],page:'https://tomo-chan.jp/',sha256:'a'.repeat(64)}))};
test('romance keeps two independent original images, shared depth and no power grade',()=>{
 const svg=renderRomanceCard(card,assets);
 assert.equal((svg.match(/data-romance-subject=/g)??[]).length,2);
 for(const id of ['jun','tomo'])assert.ok(svg.includes(`data-romance-subject="${id}"`));
 assert.ok(svg.includes('data-family="romance"')&&svg.includes('CHILDHOOD FRIENDS'));
 assert.ok(!svg.includes('ARTIST EDITIONS')&&!svg.includes('data-layer="power-rank"'));
 assert.ok(svg.includes('data-character-breakout="required"'));
 assert.ok(svg.indexOf('data-frame-boundary')>svg.lastIndexOf('data-depth="subject"'));
 assert.ok(svg.includes('transform="matrix(1.142857 0 0 1 -71.428571 0)" data-romance-duo'));
});
test('romance refuses duplicate or mismatched character identities',()=>{
 assert.throws(()=>renderRomanceCard({...card,subjects:[card.subjects[0],card.subjects[0]]},assets),/distinct/);
 const wrong=structuredClone(assets);wrong.subjects[1].id='chinatsu';assert.throws(()=>renderRomanceCard(card,wrong),/identity/);
});
test('romance refuses alpha crops outside original image dimensions',()=>{
 const wrong=structuredClone(assets);wrong.subjects[0].alphaBBox=[70,2,500,877];assert.throws(()=>renderRomanceCard(card,wrong),/alpha bounds/);
});
