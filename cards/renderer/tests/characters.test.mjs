import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {characterCards} from '../src/characters.mjs';
import {ROOT} from '../src/node-assets.mjs';

test('character substitution preserves every original design parameter and the source templates',async()=>{
 const templates=JSON.parse(await fs.readFile(ROOT+'/examples/cards.json','utf8'));
 const original=structuredClone(templates);
 const profile=JSON.parse(await fs.readFile(ROOT+'/examples/characters/naruto.json','utf8'));
 const cards=characterCards(templates,profile);
 const characterFields=new Set(['id','name','artwork','subtitle','description']);
 assert.equal(cards.length,3);
 for(let i=0;i<templates.length;i++){
  assert.equal(cards[i].name,'Naruto');
  assert.equal(cards[i].artwork,'naruto-official');
  for(const key of Object.keys(templates[i])){
   if(!characterFields.has(key))assert.deepEqual(cards[i][key],templates[i][key],`${templates[i].id}: ${key} must stay fixed`);
  }
 }
 cards[0].subject.x=999;
 assert.deepEqual(templates,original,'Generated definitions must not mutate the original designs');
});
