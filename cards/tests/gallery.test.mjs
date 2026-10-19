import test from 'node:test';
import assert from 'node:assert/strict';
import {collectionGallery,collectionOrder} from '../src/gallery.mjs';

test('gallery preserves exact forms, strongest-first ordering and safely embedded data',()=>{
  const card={id:'a',name:'A </script> <b>',form:'Restored Shikai / Lost Agent',formId:'restored-shikai',number:2,design:'ivory',phone:'a.png',preview:'a.jpg',power:{rank:'A',scope:'x',basis:'y',limits:'z',references:['https://example.test/']}};
  const report={cards:[{...card,id:'b',number:1,power:{...card.power,rank:'B'}},card]};
  assert.deepEqual([...report.cards].sort(collectionOrder).map(c=>c.id),['a','b']);
  const html=collectionGallery(report),data=html.match(/<script id="collection-data" type="application\/json">([\s\S]*?)<\/script>/)[1];
  const cards=JSON.parse(data);assert.equal(cards[0].form,card.form);assert.equal(cards[0].image,'a.jpg');assert.equal(cards[0].name,card.name);assert.ok(!data.includes('<'));
  assert.doesNotMatch(html,/aria-label="Finish"/);assert.match(html,/aria-label="Power rank"/);assert.match(html,/Rank reasoning/);
});
